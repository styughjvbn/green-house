# 목록·하위 이력 조회 경계 조사

기준: 2026-10-06, `c8590c4e`, BE-032/034 후속. Controller → application → Repository → 실제 프론트 소비자 → 기존 회귀 시험을 대조했다. [현재 진행 상태](10-remediation-progress.md), [초기 성능 감사](archive/06-performance.md)의 과거 미완료 항목을 현재 코드에서 다시 확인했다.

이번 변경은 조사 문서다. API·조회 SQL·응답·업무 정책은 변경하지 않았다. 아래 비용은 코드와 기존 fixture에서 확인한 증가 구조이며 새 운영 지연·heap·응답 크기 측정 결과가 아니다. 장애 발생이나 모든 경로의 즉시 수정 필요성을 확정하지 않는다. `RB-*`는 이 문서 안의 추적 번호이며 BE-001~044를 추가하거나 다시 미완료로 집계하는 번호가 아니다.

## 1. 결론과 판단 기준

현재 주요 문제는 **N+1 재발보다 조회 목적에 비해 큰 완전 목록·하위 이력을 반환하는 계약**이다. 여러 경로는 SQL 수가 일정하거나 500건 단위로만 늘지만 반환 행·Entity·Java 목록·JSON은 전체 데이터에 비례한다. 먼저 실제 사용하는 판매 선택지와 사용자 그룹 목록을 개선하고, 경매 목록/상세의 결합을 분리하는 순서가 적절하다.

| 경계 | 실제로 제한하는 것 | 제한하지 않는 것 |
| --- | --- | --- |
| root pagination, 최대 100건 | root 수와 root ID 조회 입력 | root별 targets·members·attempts·entries의 총량 |
| 500 ID 분할 | 각 SQL의 bind 수 | 모든 batch를 합친 Entity·DTO·응답량 |
| stream/fetch size 500 | JDBC 읽기 단위, Entity 적재 회피 | 전체 후보 스캔, grouping map, 마지막 `toList()` |
| 선택지 응답 최대 200건 | 최종 출하 root 수 | 사용된 후보를 건너뛰는 반복 조회 횟수와 각 출하의 lot 수 |
| 그래프 depth 제한 | 탐색 깊이 | 직접 계보 endpoint의 관계 폭·전체 과거 변환 수 |

완전한 작업 대상 snapshot·수량 수지·집계가 필요한 읽기와 화면에 한 페이지를 표시하는 읽기를 구분해야 한다. 화면용 페이지를 기존 완전 조회에 그대로 적용하면 전체 선택 누락, 잘못된 합계·capability, 과거 보정 해석 변경으로 이어질 수 있다.

## 2. endpoint·소비자별 현황

| 경로 | 현재 경계 | 소비 형태 | 후속 판단 |
| --- | --- | --- | --- |
| 일반 난 묶음 목록 | 필터는 있으나 전체 List | 구조 작업에서 몇 개 결과 ID를 찾으려고 전체 조회 | RB-01: 목적별 ID 조회·paged search |
| 판매 가능한 묶음 검색 | 재고/상태는 DB 필터, 결과 상한 없음 | 판매 선택 창에서 추가 필터·동별 집계·정렬 | RB-01: 우선 개선 |
| 품종 연결 묶음 | 품종 내 전체 List | 품종 상세의 전체 연결 목록 | RB-01: 선택된 품종 기준 페이지 |
| 사용자 그룹 목록·묶음의 소속 그룹 | 모든 root와 각 root의 전체 members | Work 등록·농장 선택 패널이 목록부터 전부 받음 | RB-02: summary와 member 조회 분리 |
| 자동 그룹 summary·members | scalar stream, 전체 summary/회원 List | 선택지 및 서버 작업 대상 해석이 공유 | RB-03: 표시 계약과 완전 해석 분리 |
| 경매 lot 목록·상세·timeline | 목록 root 최대 100, 하위 이력 전체 | 목록 응답에서 선택 lot 상세를 바로 표시 | RB-04: 목록/상세/이력 분리 |
| Work 단건·details·corrections | 대상·효과·보정 각각 전체 | 대상 탭, 실행 탭, 보정 응답 | RB-05: 표시 페이지와 업무 수지 분리 |
| 직접 계보 | 직접 관계와 변환 이력 전체 | 선택 묶음의 계보 조회 | RB-06: 그래프 상한과 별도 계약 |
| 범위별 Work 이력·거래처 텍스트 검색 | root는 페이지, 사전 범위 ID는 전체 | 운영 이력·Sales/Auction 검색 | RB-07: 페이지 이전 비용 보강 |
| Sales 경매 출하 선택지 | 결과 최대 200, 후보 반복 상한 없음 | 프론트 export는 있으나 현재 UI 호출 없음 | RB-08: 사용 확인 후 scan 계약 개선 |
| Inbound 목록 | root 최대 100, 현재 생성 묶음 전체 | 목록과 상세가 같은 응답 사용 | RB-09: 입력 상한을 고려해 후순위 측정 |
| Mutation 목록 | root 최대 100, entries/relations 전체 | `dev` 조건에서만 공개 | RB-09: 진단용 후순위 |

### RB-01 — 전체 묶음 검색과 작은 ID 조회의 과다 적재

**근거:** [OrchidGroupQueryController](../backend/src/main/java/com/greenhouse/backend/farm/controller/orchid/OrchidGroupQueryController.java)의 `GET /api/orchid-groups` → [FarmQueryService](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/FarmQueryService.java)의 `getOrchidGroups` → [OrchidGroupRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java)의 `search`. `searchSellable`도 fetch된 묶음 List 전체를 [SalesOrchidGroupQueryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupQueryService.java)에 전달한다. 필요한 위치/품종을 fetch하므로 이 경로를 mapper N+1로 분류하지 않는다. 전체 Entity·응답 적재가 문제다.

[판매 선택 창](../frontend/src/features/sales/ui/slips/SalesOrchidGroupSearchSelect.tsx)은 검색 결과 전체를 저장하고 품종명 일치·속·상태를 로컬에서 추가 필터링한다. 동별 건수도 전체 필터 결과에서 구한 뒤 동 선택과 정렬을 적용한다. 현재 서버 pagination이 없으므로 이것을 이미 발생한 “pagination 이후 filtering” 결함이라고 부르지는 않는다. 하지만 페이지만 먼저 붙이면 빈 페이지·틀린 동별 건수·다른 페이지의 선택 항목 누락을 만들 수 있다.

[구조 작업 결과 복원](../frontend/src/features/work-record/model/work-types/structure-change/useStructureChangeExecution.ts)은 `missingPriorResultIdKey`의 몇 개 ID를 찾기 위해 조건 없는 `getOrchidGroups()`를 호출한 뒤 로컬에서 ID를 걸러낸다. 이 목적에는 전체 농장 검색이 필요하지 않다. 현 목록의 `quantity > 0` 의미까지 확인하여 단건 조회 또는 제한된 ID 일괄 조회로 대체할 수 있다. 큰 ID 집합을 단건 HTTP 반복으로 바꾸는 것도 피해야 한다.

품종 상세는 [VarietyResponseAssembler](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyResponseAssembler.java)의 `connectedOrchidGroups`에서 품종 내 묶음 전체와 최신 작업일을 조립하고 [VarietyView](../frontend/src/features/inventory/ui/variety/VarietyView.tsx)가 전체 목록을 표시한다. 최신일 500 ID 분할은 전체 응답의 상한이 아니다.

**권고:** 판매 선택지에는 위치·품종·속·상태 조건을 pagination 이전에 적용하는 서버 계약을 추가한다. 동별 집계와 이미 선택된 ID의 표시를 별도로 보장한다. 판매 가능 상태/가용 수량 정책은 Farm 소유 정책과 최종 쓰기 검증을 유지한다. 일반 전체 목록과 품종 연결 조회의 기존 소비자는 단계적으로 전환하며 기존 응답을 조용히 잘라내지 않는다.

### RB-02 — 사용자 그룹 목록에서 모든 소속 묶음을 펼침

**근거:** [OrchidGroupCollectionService](../backend/src/main/java/com/greenhouse/backend/farm/application/collection/OrchidGroupCollectionService.java)의 `getCollections`는 root 전체를 읽는다. `toResponses`는 모든 collection ID로 membership을 읽고, 모든 distinct 묶음의 상세를 500개씩 읽어 collection별 전체 members를 응답에 넣는다. [member Repository](../backend/src/main/java/com/greenhouse/backend/farm/repository/collection/OrchidGroupCollectionMemberRepository.java)의 collection ID `IN`은 분할되지 않는다. 같은 묶음이 여러 collection에 속하면 DTO도 각 collection에 반복된다.

`getCollectionsForOrchidGroup`도 요청한 묶음의 소속 이름만 읽는 것이 아니라 해당 collection들의 다른 모든 members를 포함한다. 단건 collection 상세는 root 1개이지만 member 수에는 읽기 상한이 없다. 따라서 반환 비용은 collection root 수보다 전체 membership 수에 좌우된다.

[WorkTargetSelectionDialog](../frontend/src/features/work-record/ui/registration/WorkTargetSelectionDialog.tsx)과 [OrchidSelectionPanel](../frontend/src/features/orchid-management/ui/components/OrchidSelectionPanel.tsx)은 선택 창에서 전체 collection 응답을 받는다. 화면 미리보기의 일부 표시만으로 서버 조회량이 줄지는 않는다. 그룹 count·quantity도 현재 member 응답에서 계산하므로 배열만 잘라 summary를 만들면 잘못된 값이 된다.

**권고:** 목록/소속 표시는 member 없는 summary와 완전한 집계로 제공하고, 선택한 collection의 members는 별도로 페이지 조회한다. collection 전체 작업 선택은 서버의 `USER_COLLECTION` 범위 해석을 유지한다. 화면에서 받은 첫 페이지 ID로 전체 작업 대상을 대체하지 않는다. 보관 상태·제거된 membership·현재 묶음 존재 여부·기존 정렬 의미를 보존해야 한다.

### RB-03 — 자동 그룹 stream과 작업 대상 resolver의 별도 경계

**근거:** [DerivedOrchidGroupService](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/DerivedOrchidGroupService.java)는 [scalar Repository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupSummaryRepository.java)의 stream을 사용하여 Entity 적재를 제거했다. 그러나 `getGroups`는 전체 후보에서 Java로 현재 연령을 계산·필터링하고 grouping map과 구역 Set을 유지한다. `getMembers`는 같은 연령 필터 뒤 전체 DTO를 `toList()` 한다. fetch size는 전체 읽기 상한이 아니다.

[FarmWorkTargetResolver](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolver.java)의 `DERIVED_GROUP`는 전체 member DTO → ID Set → active Entity 재조회이고, `USER_COLLECTION`도 전체 membership ID → active Entity 재조회다. **`resolveActiveIds`는 한 번의 `findActiveWorkTargetsByIds(ids, ...)`를 호출한다.** 이는 `lockAndValidateActive`의 500 ID 분할과 다르며 대상이 커지면 단일 JPQL `IN`도 커진다. scalar로 먼저 읽더라도 뒤의 Entity 재조회 비용은 남는다. 건별 N+1이 아닌 projection 재조회와 큰 ID 집합 문제다.

**권고:** 화면 member 페이지와 서버의 완전 대상 해석을 분리한다. resolver용 ID/scalar read와 제한된 ID batch를 Farm 소유 경계 안에서 검토한다. 업무일 기준 연령 필터는 페이지 이전에 정확히 적용하거나 동등성이 검증된 cursor 계약을 사용한다. 원천 행을 먼저 자른 summary는 전체 count·quantity·zone 수를 보존하지 못한다. 새 작업 생성의 전체 대상 snapshot과 잠금 순서는 유지한다.

### RB-04 — 경매 lot root 페이지에 모든 시도·결과·상태 이력 포함

**근거:** [AuctionTrackingService](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)의 `getLots`는 root page를 먼저 읽지만 `assembleLots`가 페이지 안 모든 lot의 [attempt/result lines](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionAttemptRepository.java)와 [status histories](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionLotStatusHistoryRepository.java)를 전부 읽는다. root pagination에 collection fetch join을 건 경우는 아니다. 하위 batch 조회에 총량 상한이 없는 것이다. 단건과 timeline도 전체 lot 응답을 사용한다.

[useAuctionTracking](../frontend/src/features/sales/model/useAuctionTracking.ts)은 목록 `content`에서 선택 lot을 찾아 상세에 전달한다. 선택하지 않은 lot의 이력까지 내려받는 이유가 이 소비 계약이다. lot 수가 같아도 반복 경매·결과 분할·상태 이벤트가 누적되면 Entity와 JSON이 증가한다.

**권고:** 목록은 상태·수량·금액·capability와 필요한 요약만, 선택 lot은 별도 상세로 조회한다. 시도/상태 이력은 안정적인 날짜+ID 순서와 continuation/page 계약을 제공한다. 전체 결과에 근거한 업무 가능 여부와 금액은 서버에서 완전한 사실로 판단하며 화면의 일부 이력으로 재계산하지 않는다. 과거 snapshot과 저장된 write receipt 응답을 목록 DTO 변경 때문에 다시 작성하지 않는다.

### RB-05 — Work 대상·실행·보정 전체 조회와 중복 로딩

**근거:** [WorkOperationResponseAssembler](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationResponseAssembler.java)는 단건 응답에도 전체 유효 targets를 넣는다. [WorkOperationDetailService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailService.java)는 모든 실행 효과·효과 관계·보정과 필요한 참조를 조립한다. FARM/HOUSE 등 큰 범위로 만든 작업은 root 1개라도 대상이 많을 수 있다.

[WorkOperationCorrectionService](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java)의 `response`는 일반 Work 상세, 전체 corrections, `quantities.context(originalId)`를 호출한다. [WorkCorrectionQuantityService](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkCorrectionQuantityService.java)의 `context`도 원본·전체 effects·**같은 전체 corrections를 다시 조회**하여 저장된 수량 수지를 복원한다. gate가 꺼져 있어도 이 조회 응답의 context 계산은 수행된다. 중복 load는 확인됐지만 이번에 실행 시간이나 추가 lazy SQL 수를 측정하지 않았다.

[WorkOperationDetails](../frontend/src/features/work-record/ui/detail/WorkOperationDetails.tsx)의 실행 상세 query는 실행 탭에서만 활성화된다. 실행 결과 카드의 개별 묶음 query도 `editing && groupId != null` 조건이다. 모든 카드 표시마다 HTTP N+1이 발생한다고 판정하지 않는다.

**권고:** summary·progress·availableActions는 전체 사실에 대한 집계를 유지하고 targets·effects·보정 표시 목록은 페이지 계약으로 분리한다. 같은 요청에서 이미 읽은 보정 사실을 수지 계산에 재사용하는 개선은 별도 작은 변경으로 검토할 수 있다. **수지 계산에 최신 몇 건만 넣는 방식은 금지한다.** 저장 효과/보정 전체를 해석하는 reader·snapshot 의미와 구형 JSON 호환을 유지해야 한다. BE-009 gate 활성화·일반 수정의 RECONCILIATION 전환은 이번 범위가 아니다.

### RB-06 — 직접 계보의 폭과 구조 효과 ID 조회

**근거:** [OrchidGroupLineageService](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupLineageService.java)는 모든 직접 source/result 관계와 [StructureChangeLineageQueryService](../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeLineageQueryService.java)의 변환 이력을 합친다. Work는 묶음에 연결된 효과를 읽은 뒤 구조 변경 여부를 Java에서 걸러 모든 관련 효과 ID의 관계를 단일 `IN`으로 읽는다. Farm의 관련 묶음 상세는 500 ID 분할이 적용됐지만 전체 결과를 유지한다.

소비자는 선택 묶음의 `/lineage` 응답을 사용한다. 별도로 상한이 적용된 Work/Mutation 그래프와 동일한 API가 아니다. 한 단계 직접 관계라는 이유만으로 결과 폭과 과거 변환 수가 제한되지는 않는다.

**권고:** 직접 관계와 변환 이력의 조회/페이지 의미를 각각 정한다. 단일 효과가 많은 source/result를 가지는 경우도 함께 고려한다. 기존 계보의 과거 수량·시간·현재 속성 참조 구분, 직접 관계와 구조 변환 중복 제거, 순서를 유지한다. 우선 효과 ID batch를 보강할 수 있으나 그것만으로 응답 상한까지 해결됐다고 판정하지 않는다.

### RB-07 — root 페이지 이전의 전체 scope/검색 ID 수집

**근거:** [WorkOperationQueryService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java)의 `historyPage`는 operation page 이전에 Farm resolver로 범위 전체 묶음의 값/현재 위치를 읽어 ID Set·location Map을 만든다. root 이력은 전역 pagination이 적용돼 있고 PostgreSQL 배열 membership도 이미 적용됐다. 그러나 반환할 대표 묶음 몇 개를 정하기 전에 모든 현재 위치를 적재하고, page 안 operation의 일치 targets/효과 관계도 모두 읽은 뒤 `putIfAbsent`로 하나씩 선택한다.

[BusinessPartnerReader](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerReader.java)의 텍스트 검색은 500개 keyset batch로 읽되 matching ID 목록은 모두 누적한다. Sales/Auction의 root page 조회 전에 실행하므로 넓은 검색어·많은 거래처에서는 페이지 크기와 별도로 조회 횟수와 ID 메모리가 증가한다. 배열 사용은 bind 폭발을 방어하며 전체 ID 수집 비용까지 제거하지 않는다.

**권고:** Work 이력에 필요한 범위 ID는 Farm application scalar 계약으로, 위치는 page에서 선택된 참조 기준으로 읽는 방안을 검토한다. 현재 active scope·제외된 대상·효과 전파·한 Work당 중복 제거·전역 날짜/ID 정렬을 보존한다. 거래처 검색은 기존 대량 benchmark와 실제 분포를 먼저 확인한다. matching ID를 임의 상한으로 잘라 total·검색 결과를 틀리게 만들거나 다른 모듈 테이블을 native join하지 않는다.

### RB-08 — 출하 선택지 200건을 채우기 위한 반복 scan

**근거:** [SalesQueryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesQueryService.java)의 `getAuctionShipmentOptions`는 최신 출하 ID를 200개씩 offset page로 받아 Sales에서 사용된 ID를 제외한다. 사용 가능한 결과가 200개 모이거나 후보가 끝날 때까지 page를 계속 읽는다. 결과 상한은 있으나 scan page 상한은 없고 마지막 200개 출하에 연결된 lot도 모두 응답에 넣는다.

이 경로는 실제 후보 pagination 뒤 사용 여부를 필터링한다. 반복해서 채우기 때문에 현재 코드가 “첫 페이지가 모두 사용되면 오래된 사용 가능 출하를 놓친다”는 결함은 아니다. 사용된 최신 출하가 많으면 반복 SQL·깊은 offset 비용이 커지는 구조다. [프론트 API](../frontend/src/features/sales/api/salesApi.ts)에 함수와 공개 export는 있으나 현재 UI 호출은 찾지 못했다. 외부 소비자 부재까지 확정하지 않는다.

**권고:** 사용 여부를 확인하고 keyset/continuation 및 scan 예산·부분 결과 의미를 명시하는 계약을 검토한다. 첫 후보 page만 읽고 중단하는 수정은 기존 “최신 사용 가능 200개” 의미를 깨므로 채택하지 않는다. active UI 경로보다 후순위다.

### RB-09 — Inbound 하위 결과와 dev Mutation 진단 응답

**Inbound:** [InboundRecordResponseAssembler](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordResponseAssembler.java)는 page 안 입고에 연결된 `quantity > 0` 결과 묶음을 모두 포함한다. 날짜 집계·undo capability는 일괄 조회라 건별 N+1로 분류하지 않는다. 다만 최대 100개 입고 root만으로 하위 결과 총량이 작다고 보장되지는 않는다.

일반 HTTP 포트 입력에는 results 최대 100개 validation이 있고, 즉시 배치 생성은 단일 placement다. 신규 포트 결과가 같은 입고에 아무 제한 없이 무한 생성되는 흐름이라고 주장하지 않는다. 이후 구조 변경·과거 데이터에서의 연결 결과 수는 root read 계약과 따로 확인해야 한다. 우선 실제 분포를 측정하고, 필요하면 목록의 count/capability와 선택 입고의 결과 조회를 분리한다. 생성 묶음이 비었는지 판단하는 기존 UI/업무 의미는 완전한 집계로 보존한다.

**Mutation:** [query Controller](../backend/src/main/java/com/greenhouse/backend/farm/controller/orchid/OrchidGroupMutationQueryController.java)는 `app.environment=dev`에서만 등록된다. [query service](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationQueryService.java)는 root page의 모든 entries(before/after JSON 포함)와 연결 관계를 읽는다. 특정 묶음 필터는 해당 Mutation root를 찾는 조건이지 entries를 해당 묶음으로 제한하는 조건이 아니다. baseline/import처럼 entries가 많은 진단 응답은 커질 수 있다. 운영 사용자 목록의 즉시 장애와 같은 우선순위로 취급하지 않는다. 필요하면 header summary와 선택 Mutation의 entries/relations 페이지를 분리한다. 별도 bounded graph는 그대로 유지한다.

## 3. 이미 방어된 영역과 의도된 완전 조회

- Work 운영 목록은 paged summary다. target fan-out이 늘어도 반환 JDBC 행을 제한하는 scalar/count 경로와 회귀가 있다. RB-05의 단건 대상/상세와 구분한다.
- Work 캘린더는 날짜 필수·최대 366일, SQL에서 1,001건까지만 읽고 1,000건 초과 시 명시적인 조회 제한 오류다. 단순히 읽은 뒤 조용히 일부만 반환하는 계약이 아니다.
- 구형 묶음 작업 이력은 최신 500건, 범위 Work 이력은 전역 page다. Work/Mutation 그래프도 node·depth·내부 참조 상한과 `truncated` 의미가 있다. 초기 감사의 무제한 문제를 그대로 재등록하지 않는다.
- Sales·Settlement·Payment 운영 root 목록의 기존 pagination/호환 상한은 유지한다. 이번에 이들 단건 전표 전체를 모두 새 페이지로 바꾸는 설계를 제안한 것은 아니다.
- [FarmStatusService](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmStatusService.java)의 전체 농장 지도는 완전한 배치를 보여주는 projection 계약이다. 임의 목록 cut을 넣으면 실제 배치가 사라진다. `/api/houses` 전체 구조+묶음 tree를 Work 등록/실행에서 쓰는 경우는 가벼운 구조/대상 조회로 분리할 여지가 있지만 지도 계약 자체를 부분 목록으로 바꾸지는 않는다.
- 전체 작업 대상 snapshot과 수량 수지 복원은 완전성이 필요한 읽기다. 화면용 요약/페이지와 분리하되 transaction·잠금·snapshot·receipt·보정 gate를 변경하는 근거로 삼지 않는다.

## 4. 기존 시험이 방어하는 것과 남는 측정

아래는 시험 코드를 조사한 결과다. 이번 차수에서 테스트나 benchmark를 새로 실행한 결과가 아니다.

| 기존 시험 | 현재 방어 | 이번 경계에서 부족한 축 |
| --- | --- | --- |
| [CoreQueryRegressionTest](../backend/src/test/java/com/greenhouse/backend/CoreQueryRegressionTest.java) 판매 선택 | 1/10/50건에서 판매 불가 제외·SQL 1회 | SQL 1회 안의 전체 결과/Entity/응답량, 실제 UI의 정확한 필터·동별 count·페이지 밖 선택 |
| 같은 시험의 경매 목록 | 1/10/50 lot, lot당 시도·결과 행 1개에서 SQL 최대 5회 | root 수를 고정하고 시도·결과·상태 이력만 늘리는 경우 |
| 같은 시험의 출하 선택지 | 420개 중 최신 210개 사용 후에도 오래된 사용 가능 200개·순서·SQL 8회 | 사용된 후보의 긴 연속 구간, deep offset, 출하별 많은 lot |
| [OrchidGroupCollectionQueryTest](../backend/src/test/java/com/greenhouse/backend/OrchidGroupCollectionQueryTest.java) | 1/10/50 collection에 동일한 2개 member를 공유, batch SQL·집계·순서 | distinct members와 총 membership 증가, collection ID bind, summary 응답 크기 |
| [OrchidSummaryQueryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidSummaryQueryPostgresE2ETest.java) | 1/500/5,001건에서 scalar·Entity 0, 자동 그룹/연령·합계 | stream 뒤 최종 member 목록 크기, Java 집계 비용, resolver 재조회·큰 `IN` |
| [LegacyLineageQueryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/LegacyLineageQueryPostgresE2ETest.java) | 1/10/50/501 관계의 완전 응답·수량/날짜·묶음 bind 최대 500 | 효과 ID 증가, 단일 변환 폭, 최종 JSON 총량의 표시 경계 |
| [WorkDetailQueryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkDetailQueryPostgresE2ETest.java) | 보정 0/1/10/50 및 실행 1/10/50, 상세 SQL 수·저장 JSON 호환 | 큰 단건 targets, `/corrections`의 중복 보정 조회, 표시 페이지·수지 완전성 동시 보호 |
| [WorkQueryMeasurementPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkQueryMeasurementPostgresE2ETest.java) | 4 roots × targets 1/40/100에서 SQL 최대 7·JDBC 행 최대 48·target Entity 0 | 단건 상세나 범위 이력의 page 이전 전체 location 적재는 별도 경로 |
| [BoundedReferenceQueryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/BoundedReferenceQueryPostgresE2ETest.java)·[SearchScalabilityBenchmarkTest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SearchScalabilityBenchmarkTest.java) | 500/501/5,001 참조, 501/5,001/70,001 거래처 검색의 전역 page·배열·batch·할당/시간 계측 | 실제 운영 분포/계획 비용; 기존 대량 검색 시험이 없는 영역이라고 표현하지 않는다 |

개선 목적별로 **고정 root 수 + 증가하는 하위 행** fixture를 추가한다. SQL 수만 아니라 JDBC 소비 행·Entity 적재·bind 최대·직렬화 결과의 항목 수/bytes를 함께 확인한다. HTTP 요청 수도 실제 소비자 흐름에서 확인하고, 모든 시험에 환경 의존적인 절대 ms 기준을 일괄 넣지는 않는다. 측정용 allocation/heap은 필요한 시나리오에서 따로 수집한다.

필수 correctness는 페이지 순서·전체 count·필터의 페이지 이전 적용·페이지 밖 선택 유지·전체 범위 선택·일치하지 않는 연령 후보·보관/제거된 member·같은 날짜 ID tie-break다. 과거 이력 조회 변경에는 전체 수량 수지·구형 JSON·snapshot·write receipt/replay가 표시 페이지와 독립적으로 유지되는지를 검증한다.

## 5. 개선 순서와 완료 기준

| 순서 | 변경 목적 | 완료 기준 |
| --- | --- | --- |
| 1 | 판매 묶음 선택 검색의 페이지/필터 계약 | Farm 소유 판매 가능 정책 유지, 모든 선택 조건은 페이지 이전, 동별 전체 집계·선택 ID 복원, backend·실제 UI·OpenAPI/생성 타입을 함께 전환, PG 반환 행/정렬 회귀 |
| 2 | collection summary와 member 조회 분리 | 목록/소속 응답에서 전체 member 적재 제거, 집계 정확성·보관 상태 유지, 전체 collection 작업 snapshot 보존, root 고정/member 증가 회귀 |
| 3 | 경매 목록 summary와 선택 상세/이력 분리 | 선택하지 않은 lot 이력 적재 제거, 전체 이력 접근 경로 제공, capability/금액/receipt 보존, 고정 lot/다수 이력 회귀 |
| 4 | 일반/품종/자동 그룹의 목적별 조회·resolver batch | 소수 ID의 전체 농장 조회 제거, 정확한 연령/집계·완전 대상 유지, ID bind 상한·재조회 비용 회귀 |
| 5 | Work 표시 페이지·수지 계산의 중복 load 정리 | targets/effects/corrections의 표시 경계 제공, 같은 보정의 불필요한 재조회 제거, 전체 사실의 수지/순서/호환 보존 |
| 조건부 | 직접 계보·사전 scope 조회·출하 선택지·Inbound·dev Mutation | 해당 소비/운영 분포와 비용을 확인한 범위부터 적용; 기존 완전 계약을 임의 절단하지 않음 |

한 번에 모든 목록을 공통 generic pagination으로 바꾸지 않는다. 표시의 페이지와 전체 scope 해석은 책임이 다르다. 계약 변경 때는 Controller/DTO/validation/회귀를 먼저 고친 뒤 OpenAPI와 프론트 생성 타입을 함께 갱신한다. DB 경계 변경의 PG 검증과 API 소비자 검증은 각 구현 단위에서 수행한다.

정산 시작 경로는 사용자 결정대로 별도 일괄 정산 구현 후 제거할 예정이며 현재 TODO/성능 수정 보류를 유지한다. BE-009 일반 수정/RECONCILIATION 결정도 보류다. 이번 조사에서 두 정책의 변경을 전제하지 않는다.

## 6. 이번 변경의 검증

코드·Controller·관련 API slice·프론트 소비자·기존 시험을 정적으로 대조하고 문서 링크/`git diff --check`를 확인한다. 제품 코드 변경이 없어 backend 전체 시험·frontend check·PostgreSQL E2E·standard/large benchmark는 재실행하지 않는다. 마지막 일반 전체 검증은 65차의 backend 144개 클래스·748건/frontend check, 전체 PG 체크포인트는 59차의 76개 클래스·780건이다. 이것을 신규 read 경계 개선의 완료 증적으로 사용하지 않는다.
