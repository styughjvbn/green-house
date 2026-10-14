# Backend 통합 findings

> 보관 문서: 당시 코드·감사·검증 이력이며 현재 구현의 기준이 아니다. 미해결/운영 검증/보류 상태는 [현재 작업 목록](../10-remediation-progress.md)을 따른다.

- 평가일: 2026-10-03
- 재검토 기준: `80106917232a671b5a489ad8e59d37b63a06dffe` (`develop`). 코드 수정 없이 문서만 작성했다.
- 출처: [02 아키텍처](02-architecture.md), [03 도메인·정합성](03-domain-consistency.md), [04 코드 품질](04-code-quality.md), [05 변경 용이성](05-changeability.md), [06 성능](06-performance.md), [07 테스트](07-testing.md).
- 02·04·05의 기준 commit은 `08b50dc7…`이다. 해당 문서의 주요 계약·메서드·호출자를 현재 코드와 다시 대조했다. 기존 행 번호는 그대로 복사하지 않고 파일과 메서드로 근거를 표시했다.
- **통합 44건: Critical 0, High 5, Medium 27, Low 12.** 동일 원인과 개선 작업을 공유하는 항목을 합쳤다. 재현된 업무 결함에 대한 테스트 공백은 그 finding에 포함했으며 별도 결함으로 중복 집계하지 않았다.

## 판단 기준과 근거의 강도

| Severity | 기준 |
| --- | --- |
| Critical | 광범위하고 즉시적인 데이터 손실·금전 손상·핵심 업무 중단이 확인되고 영향 통제가 어려움 |
| High | 실제 재고·금액·이력의 잘못된 확정이나 중요한 업무 규칙 위반이 재현됨 |
| Medium | 제한된 업무 실패, 누적 데이터에 따른 성능 위험, 중요한 변경에서 여러 계약의 불일치를 만들 가능성과 변경 비용이 큼 |
| Low | 국소적 계약·추적·테스트 유지비 문제. 현재 재고·금액 손상이나 운영 장애는 확인하지 못함 |

`Evidence`의 **재현**은 03·06의 추가 진단 결과, **코드 확인**은 현재 구현의 정적 근거, **조건부 위험**은 아직 발생 조건을 실행으로 확인하지 못한 위험이다. 진단 probe가 성공했다는 말은 결함 조건을 관찰했다는 뜻이다. 운영 데이터 장애·빈도·최대 부하는 측정하지 않았다. Critical을 부여할 근거는 없었다.

`Estimated scope`는 수정 범위이며 일정 견적이 아니다. `Regression risk`는 권고 변경이 기존 정상 동작을 깨뜨릴 위험이다. 긴 클래스, 많은 파일, suffix, 정렬 방식만으로 finding이나 Severity를 정하지 않았다. CQ-002·005·008은 영향 범위를 재평가해 Low로 낮췄다. PERF-07의 기존 High도 공유 잠금 안의 증가 비용은 인정하되 장애·대기 실측이 없어 Medium으로 조정했다.

## 업무 정합성과 기록

### BE-001 — 판매 수정의 예약 identity 재사용

ID: BE-001  
Severity: High  
Category: Domain consistency / Idempotency  
Affected modules: sales, farm, settlement  
Evidence: **PostgreSQL 재현**, 03 DC-01·07 §9. [SalesSlipUpdateService.update](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java)가 기존 예약 해제 후 root를 flush하고 재예약한다. [SalesSlipInventoryService](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java)는 `RESERVE:<version>`을 사용한다. spec만 수정하면 allocation=10인데 reserved=0, reserve movement=2가 저장됐다.\
Problem: 자식 품목만 변경되면 root JPA version이 증가하지 않아 신규 예약이 기존 Mutation replay로 처리된다. 같은 총액의 다른 그룹 배분은 fingerprint 충돌로 정상 수정이 거부된다.  
Why it matters: 전표와 예약이 불일치한 채 성공 응답한다. 이후 출고·취소 실패와 다른 전표 예약 소비 가능성이 있다. 후자는 아직 재현하지 않았다. 단일 transaction과 원장 fence도 잘못된 업무 identity를 교정하지 않는다.  
Example change scenario: 품목 규격·메모만 수정하거나 총액을 유지한 채 배분 그룹을 바꾼 후 출고한다.  
Recommended action: JPA version과 별도의 판매 변경 operation identity를 같은 transaction에서 발급한다. release/reserve/movement를 같은 변경에 연결하고 replay의 movement 중복도 막는다. spec 변경·배분 변경·연속 수정→출고/취소를 영구 PG 회귀로 추가한다. 기존 불일치 자료는 대사 후 복구 정책을 정한다.  
Estimated scope: sales 변경 identity·재고 조율, farm replay 소비 계약, PG 회귀; 영속 identity 설계에 따라 migration 및 기존 자료 대사.  
Regression risk: High — 기존 source key·replay·snapshot 순서·예약 해제 및 출고의 대칭성.  
Related findings: BE-003, BE-004, BE-006, BE-008, BE-013.

### BE-002 — 경매 결과가 있는 출하의 물리 삭제

ID: BE-002  
Severity: High  
Category: History preservation / Cancellation  
Affected modules: auction, sales, farm, settlement  
Evidence: **PostgreSQL 재현**, 03 DC-04·07 §9. [AuctionShipmentLifecycleService.deleteDraftShipment/findShipmentIdsWithResults](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentLifecycleService.java)는 실제 결과 참조 대신 `currentStatus != WAITING`을 검사한다. SOLD 결과→수동 WAITING→정산 전 전표 취소 후 result/attempt/history가 모두 0건이 됐다.\
Problem: 변경 가능한 현재 상태를 과거 결과 존재 여부로 사용하고 shipment cascade 삭제를 허용한다. 취소 검사와 삭제에 lot 잠금 후 사실 재확인 계약도 없다.  
Why it matters: 이미 발생한 경매 이력이 사라지고 재고가 복구된다. 정산 연결된 경우는 별도 검사/FK로 보호되므로 결과가 있고 정산은 없는 구간이 실제 위험 범위다. 동시 결과 입력/취소는 미재현이다.  
Example change scenario: 운영자가 상태를 대기로 보정한 다음 출하 전표를 취소한다.  
Recommended action: 실제 attempt/result/history 참조로 취소 가능 여부를 계산하고 lot 잠금 후 재검증한다. 기록이 생긴 출하는 상태 변경·취소 기록으로 보존한다. capability와 실행을 같은 판단에 연결하고 순차 재현 및 동시 결과/취소 양쪽 순서를 시험한다.  
Estimated scope: auction lifecycle·상태 전이, sales 취소/capability, 다중 모듈 PG 회귀; 보존 방식에 따라 migration.  
Regression risk: High — 무결과 출하 취소, stock 복구, 정산 FK·기존 history 보존.  
Related findings: BE-005, BE-007, BE-010, BE-006.

### BE-003 — 직접 판매 금액 overflow

ID: BE-003  
Severity: High  
Category: Financial integrity / DB constraints  
Affected modules: sales, settlement, analytics, print  
Evidence: **PostgreSQL·Java 재현**, 03 DC-02·07 §9. [SalesSlipItem](../../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipItem.java)의 `quantity * unitPrice`, [SalesSlip.recalculateAmounts](../../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlip.java)의 int 합계. 수량 2×단가 1,500,000,000 입력이 total=-1,294,967,296으로 저장되고 재고도 예약됐다. 두 품목 합계 overflow도 확인했다.\
Problem: 입력값 각각의 validation은 통과하지만 곱·합계의 범위를 검증하지 않는다. 음수 금액을 막는 DB 제약도 없다. 경매 결과에는 별도의 정확 연산 검사가 있다.  
Why it matters: 매출·미수금·입금 가능액·출력에 잘못된 금액이 확정된다. 잔액의 0 clamp는 계산 오류를 숨길 수 있다.  
Example change scenario: 단가 한도 확대 또는 여러 고액 품목을 한 전표로 합친다.  
Recommended action: domain에서 정확 연산 및 저장 범위를 검증한다. long 전환 여부는 DTO·DB·입금·분석까지 함께 결정한다. 곱과 다중 품목 합계 경계, 거절 시 전체 예약/전표 rollback을 시험하고 비음수 CHECK/backfill을 검토한다.  
Estimated scope: 단순 거절은 sales domain 중심; 저장 범위 확대는 DB·API·settlement·출력 계약까지 확대.  
Regression risk: High — 기존 금액 범위, paid/remaining 계산, 직렬화 및 DB 타입.  
Related findings: BE-001, BE-011, BE-037.

### BE-004 — 판매 가능 상태 정책의 조회·예약 불일치

ID: BE-004  
Severity: High  
Category: Domain policy / Duplicated rules  
Affected modules: farm, sales, work, analytics, dashboard  
Evidence: **PostgreSQL 재현**, 03 DC-03, 05 B/C, 07 §9. [OrchidGroup.reserve](../../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java)는 가용 수량만 검사하고 [OrchidGroupRepository.searchSellable](../../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java)도 판매불가 정책을 강제하지 않는다. 집계는 [OrchidGroupStatusPolicy](../../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupStatusPolicy.java)를 사용한다. 병해충 그룹 10개 예약이 확정됐다. Work 활성 대상 JPQL에는 비활성 상태 문자열이 별도로 있다.\
Problem: 같은 상태의 의미가 정책·조회 조건·수량 기반 visibility·쓰기 경로로 나뉜다. 실제 판매 예약은 선언된 판매불가 정책을 적용하지 않는다.  
Why it matters: 판매불가 재고가 판매 업무에 진입한다. 새 상태 추가 때 조회·집계·작업 대상·예약 의미가 더 쉽게 갈라진다. 비활성 문자열의 현재 불일치는 재현하지 않았다.  
Example change scenario: 새 격리 상태를 판매불가로 등록해도 선택 조회와 예약은 계속 허용된다.  
Recommended action: Farm domain의 신규 예약 허용 정책을 단일 기준으로 삼고 검색·capability·집계 조건도 맞춘다. 이미 예약된 재고의 해제/출고 규칙은 별도로 결정한다. 상태 분류별 조회와 writer, 실패 side effect 0을 회귀로 보호한다.  
Estimated scope: farm 정책·Repository 조건, sales 선택 계약, Work 활성 조건 및 상태별 회귀.  
Regression risk: High — 기존 예약 처리, 비활성 정의, Work 가용 수량을 판매 제한과 혼동하지 않도록 검증.  
Related findings: BE-001, BE-009, BE-023, BE-043.

### BE-005 — 경매 부분 결과·반환 재전송의 중복 반영

ID: BE-005  
Severity: High  
Category: Idempotency / Duplicate requests  
Affected modules: auction, settlement  
Evidence: 03 DC-05·07 §9. **PG 재현**: [AuctionShipmentLot.addResult](../../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipmentLot.java)의 자동 차수 요청 10개를 두 번 보내 sold=20/attempt=2. **Java 재현**: 동일 부분 반환 10개 두 번으로 returned=20. [AuctionTrackingService](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)와 반환 요청에 안정적인 replay identity가 없다.\
Problem: `attemptNo=null`은 매 요청 새 차수를 만들고 부분 반환은 누적한다. row lock과 명시 차수 UNIQUE는 네트워크 재전송과 새 업무를 구별하지 못한다.  
Why it matters: 경매 결과·반환 수량이 중복 확정되고 결과 금액은 정산에 전달될 수 있다. 전량 처리의 두 번째 거절을 부분 처리의 멱등성으로 해석할 수 없다.  
Example change scenario: 성공 응답을 받지 못해 같은 부분 낙찰·반환 요청을 재시도한다.  
Recommended action: 결과와 반환의 안정적인 요청 key·fingerprint·저장 결과를 정의한다. 동일 요청은 기존 사실을 반환하고 다른 payload는 명시적으로 거절한다. 순차/동시 재전송과 실제 두 번째 업무를 구별하는 PG 회귀를 추가한다.  
Estimated scope: auction command/receipt·DB UNIQUE·Controller 계약·OpenAPI/생성 타입·PG 시험.  
Regression risk: High — 과거 자동 차수, 실제 추가 경매·추가 반환, 결과/정산 연결.  
Related findings: BE-002, BE-007, BE-008, BE-026.

### BE-006 — 유스케이스 전체의 잠금 순서 미보장

ID: BE-006  
Severity: Medium  
Category: Concurrency / Lock ordering  
Affected modules: sales, farm, work  
Evidence: **PG deadlock 재현**, 03 DC-06·§9.1, 07 §4/9. [SalesSlipUpdateService.update](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java)는 old group 해제 후 new group 잠금을 취득한다. 다른 거래처 G1↔G2 교차 수정에서 `40P01`을 확인했다. Farm 배치의 입력별 누적 잠금 및 단일/배치의 group↔zone 순서 차이는 **조건부 위험**이다.\
Problem: 각 Repository 호출의 ID 정렬은 transaction에서 이미 취득한 잠금과 이후 집합을 함께 정렬하지 못한다.  
Why it matters: 정상 동시 편집이 실패한다. DB rollback은 작동했으므로 이 근거만으로 데이터 손상·전체 운영 중단으로 올리지 않는다. 같은 partner 직렬화도 다른 partner의 공유 재고를 보호하지 못한다.  
Example change scenario: 다른 거래처의 전표가 서로의 기존 배분 그룹으로 동시에 변경된다.  
Recommended action: 변경 전 old/new ID 합집합을 정렬해 취득하고 group/zone/operation/partner의 경로별 순서를 명시한다. release 직후 barrier의 교차 수정 및 취소/구조 변경 양쪽 순서 시험을 추가한다. 단순 재시도를 주된 해결책으로 삼지 않는다.  
Estimated scope: sales 수정 우선; farm 배치/zone 경로 잠금 순서 조사와 관련 PG 경쟁 회귀.  
Regression risk: High — 잠금 범위·보유 시간 증가, 기존 race 방어 및 atomicity.  
Related findings: BE-001, BE-002, BE-033, BE-038.

### BE-007 — 상태가 유지되는 경매 수량 변경의 기록 누락

ID: BE-007  
Severity: Medium  
Category: Auditability / Result consistency  
Affected modules: auction, settlement  
Evidence: **Java 재현**, 03 DC-07·07 §9. [AuctionShipmentLot.confirmReturn/adjustQuantities/changeStatus](../../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipmentLot.java)는 같은 next status에서 history 생성을 건너뛴다. 두 번째 부분 반환·같은 상태의 수량 보정에서 수량만 바뀌었다. 결과·정산 참조 검사 없이 lot 수량을 조정할 수 있다.\
Problem: 수량 변경 사실을 상태 전이 history에 의존한다. lot 현재 sold 수량과 과거 결과/정산의 차이를 설명하는 보정 계약도 없다.  
Why it matters: 반환·보정의 사유와 전후 수량을 추적하지 못한다. 정산 snapshot은 보존되지만 현재 lot와 달라진 이유 및 입금 이후 허용 범위를 판단하기 어렵다. 잘못된 정산 금액 갱신은 재현하지 않았다.  
Example change scenario: REAUCTION_WAITING 상태를 유지한 채 반환 수량을 보정하거나 입금 후 lot sold 수량을 수정한다.  
Recommended action: 상태 전이와 별개로 수량 변경의 before/after·actor·사유·시점을 저장한다. 결과/정산 연결 이후 수정 허용 및 보정 기록 정책을 정하고 같은 상태 변경·입금 후 보정을 시험한다. 과거 snapshot을 현재 값으로 덮어쓰지 않는다.  
Estimated scope: auction 변경 기록 및 정산 참조 계약, 필요 시 history schema와 migration.  
Regression risk: High — 반환 분류·현재 수량·과거 결과의 의미 및 기존 정산/입금 보존.  
Related findings: BE-002, BE-005, BE-010.

### BE-008 — 일반 생성 요청의 재시도 identity 부재

ID: BE-008  
Severity: Medium  
Category: API reliability / Idempotency  
Affected modules: farm/inbound, work, sales  
Evidence: **코드 확인**, 03 §9.2·05 F. [InboundRecordService.create](../../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java), [WorkOperationPlanService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java), [SalesSlipCreationService.create](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java)는 안정적인 클라이언트 요청 identity로 일반 생성을 dedup하지 않는다. HTTP timeout 재전송 실험은 미실행이다.\
Problem: 내부 Mutation/효과의 멱등성이 일반 생성 요청까지 전달되지 않는다. 같은 입력 두 번이 신규 업무인지 재전송인지 계약으로 구별할 수 없다.  
Why it matters: 응답 유실 후 재시도에서 입고·계획·전표 및 예약을 중복 생성할 수 있다. 자동 재시도하는 새 채널 도입 시 위험이 커진다. 모든 POST가 멱등이어야 한다는 일반 규칙으로 판단한 것은 아니다.  
Example change scenario: 저장 성공 뒤 timeout이 난 판매 생성 요청을 앱이나 Agent가 재전송한다.  
Recommended action: 재시도 가능한 생성 경로와 duplicate 의미를 먼저 명시하고 필요한 경로에 request key·fingerprint·결과 보존을 적용한다. 응답 유실/동시 재전송·다른 payload를 시험한다. 기존 Work Receipt를 무분별하게 공유하지 않는다.  
Estimated scope: 선택한 생성 유스케이스별 application/DB/API 계약; 경로를 한 번에 모두 변경할 필요 없음.  
Regression risk: High — 실제 동일 내용의 신규 생성과 replay 구별, 과거 클라이언트 호환.  
Related findings: BE-001, BE-005, BE-021, BE-019.

### BE-009 — 일반 묶음 수정과 보정·실사의 정책 차이

ID: BE-009  
Severity: Medium  
Category: Domain policy / Change authorization  
Affected modules: farm, work  
Evidence: **코드·정책 문서 차이**, 03 §11·05 B/D. [OrchidGroupCommandService.update](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java)→Engine `updateDetails`는 수량·일반 상태·배치 범위를 바꿀 수 있다. [FarmWorkCorrectionAdapter](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java)의 후속 사용/실사 gate를 거치지 않는다. ADR-001의 일반 상세 수정 제한 방향과 다르다.\
Problem: 동일한 현재 상태 변경의 허용 범위가 명령에 따라 다르고, 어느 차이가 의도된 운영 정책인지 명확하지 않다. Engine/원장 자체를 우회하는 경로는 아니다.  
Why it matters: 보정·실사의 제한을 일반 수정으로 대체할 여지가 있다. 정책 합의 없이 한 경로만 강화하면 다른 경로와 감사 의미가 갈라진다. 현재 일반 수정이 반드시 금지돼야 한다고 단정하지 않는다.  
Example change scenario: 실사 이후 과거 보정이 거절된 그룹의 수량을 일반 상세 수정으로 바꾼다.  
Recommended action: metadata·수량·상태·위치 변경의 승인된 유스케이스를 합의하고 실제 command guard와 정책 문서를 맞춘다. 허용/거절·reservation·후속 사용·실사 이후 상태를 명시적 회귀로 보호한다.  
Estimated scope: farm 변경 정책·API capability 및 필요 시 work 사용 검사; 정책 문서/계약 갱신.  
Regression risk: High — 기존 현장 수정 기능을 차단하거나 수정 이력을 잘못 분류할 위험.  
Related findings: BE-004, BE-010, BE-043.

### BE-010 — 감사 기록의 범위와 전후 값 완전성 부족

ID: BE-010  
Severity: Medium  
Category: Audit completeness  
Affected modules: sales, auction, farm, audit  
Evidence: **코드 확인**, 03 §10·05 D. [SalesSlipCreationService](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java)는 전표 생성 AuditEvent를 기록하지 않는다. [OrchidGroupAuditSupport](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupport.java)의 snapshot/changedFields는 memo·placementType·trayCount 등 모든 필드를 포함하지 않는다. Auction은 일반 AuditEvent 대신 자체 이력을 사용한다.\
Problem: 변경과 저장되는 기록의 atomicity는 있지만 누가 무엇을 바꿨는지 모든 주요 경로·필드에서 같은 수준으로 확인할 수 없다. WorkEffect·Mutation·자체 history도 업무 기록이므로 일반 AuditEvent 부재만으로 무기록이라 판정하지 않는다.  
Why it matters: 전표 최초 생성 주체나 metadata 변경 경위를 복원하기 어렵다. 새 속성을 snapshot에만 추가하고 changedFields/values를 빼먹으면 감사 이벤트가 누락될 수 있다.  
Example change scenario: trayCount나 memo를 변경한 뒤 운영자가 변경 이유와 실행자를 조회한다.  
Recommended action: 업무 사실 원장과 actor 감사의 책임을 구분하고 필수 경로/필드를 명시한다. 필요한 생성 및 metadata 감사만 추가하며 변경과 동일 transaction에서 저장한다. 변경 필드·actor·실패 rollback을 검사한다.  
Estimated scope: sales/farm 감사 조립·audit 계약 확인 및 대상별 회귀.  
Regression risk: Medium — 이벤트 중복, 기존 source·필드명·changedFields 의미.  
Related findings: BE-007, BE-009, BE-020, BE-022.

### BE-011 — 기존 데이터의 미검증 DB 제약

ID: BE-011  
Severity: Medium  
Category: Persistence / Legacy data validation  
Affected modules: farm, sales  
Evidence: **DDL 확인·운영 상태 미확인**, 03 §9.3. [V14](../../backend/src/main/resources/db/migration/V14__enforce_inventory_and_sales_consistency.sql)의 quantity/reserved/Sales 상태 CHECK 및 [V21](../../backend/src/main/resources/db/migration/V21__add_orchid_group_mutation_engine.sql)의 revision CHECK는 `NOT VALID`다. migration에서 대응 `VALIDATE CONSTRAINT`를 찾지 못했다. 운영 DB의 convalidated·기존 불량 행은 조회하지 않았다.\
Problem: 새/변경 행의 제약 적용과 과거 모든 행의 검증 완료가 구별되지 않는다. 보존을 위한 최초 NOT VALID 선택 자체는 타당하다.  
Why it matters: migration 성공만으로 기존 데이터의 불변식을 신뢰하면 오래된 오류가 조회·수정·대사에 뒤늦게 나타날 수 있다. 실제 불량 행이 존재한다고 주장하지 않는다.  
Example change scenario: 과거 재고를 새 예약 정책이나 원장 전환의 입력으로 사용한다.  
Recommended action: 운영 제약 상태와 legacy 위반을 read-only 대사하고 정정/원장 연결 계획 후 VALIDATE 단계를 명시한다. 교차 불변식은 필요한 application 대사로 보완한다. 모든 모듈 간 동치를 거대한 DB trigger로 강제하는 방향은 피한다.  
Estimated scope: DB 상태 점검·운영 대사·조건부 backfill/검증 migration·PG upgrade 회귀.  
Regression risk: High — 과거 자료 변경과 constraint validation의 잠금·배포 시간.  
Related findings: BE-001, BE-003, BE-009, BE-036.

## 계약과 변경 비용

### BE-012 — 실행 타입 소실과 분산된 효과 JSON 해석

ID: BE-012  
Severity: Medium  
Category: Application contracts / Persistence compatibility  
Affected modules: work, farm  
Evidence: **현재 코드 확인**, ARC-005·CQ-003·05 D. [WorkEffectCommand](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectCommand.java)/[WorkExecutionResult](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkExecutionResult.java)는 Object/Map 경계다. 포트 실행은 typed→Map→요청 DTO로 변환한다. [WorkEffectDetailCodec](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkEffectDetailCodec.java), [WorkOperationTargetView](../../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkOperationTargetView.java), WorkEffectResults는 key/fallback/숫자/결과 ID 해석을 각각 알고 있다.\
Problem: handler/payload 조합과 필드 전달을 컴파일러가 끝까지 검증하지 못한다. legacy HTTP 표현 및 저장 JSON 지식이 업무 실행 경계에 퍼진다.  
Why it matters: 속성 변경 시 실행·상세·계보·보정·replay를 함께 추적해야 하며 한 reader만 누락될 수 있다. reader의 우선순위/합집합 차이가 현재 잘못된 결과라는 재현은 없다.  
Example change scenario: 구조 변경 결과에 속성을 추가하고 숫자 문자열을 가진 과거 효과도 계속 읽는다.  
Recommended action: 기존 typed command/result를 실행 경계까지 유지하고 JSON 변환을 저장/호환 경계에 모은다. 형식별 해석 정책과 결과 순서는 명시적으로 보존하며 legacy endpoint를 입구에서 변환한다.  
Estimated scope: work effect processor/store/decoder, farm handler·호환 mapper, 과거 JSON·상세·계보 계약 회귀.  
Regression risk: High — 저장 JSON·null/없는 필드·fallback·순서 및 요청 지문 호환.  
Related findings: BE-013, BE-014, BE-016, BE-020.

### BE-013 — 필드 확장의 fingerprint·snapshot 호환 정책 부족

ID: BE-013  
Severity: Medium  
Category: Schema evolution / Idempotency compatibility  
Affected modules: farm, work, sales, audit  
Evidence: **코드 확인·조건부 위험**, 05 D·07 §3.3. [OrchidGroupMutationFingerprint](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationFingerprint.java), [OrchidGroupMutationCommandFingerprint](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationCommandFingerprint.java), [WorkRequestFingerprint](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkRequestFingerprint.java)는 객체 직렬화를 정규화한다. [OrchidGroupStateSnapshot](../../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupStateSnapshot.java)의 canonical도 head/대사·복구에 사용된다. 과거 hash를 보존하는 새 필드 version 전환이 자동 제공되지는 않는다.\
Problem: nullable 필드 추가도 직렬화에 포함되면 과거 요청 hash나 snapshot equality를 바꿀 수 있다. typed화만으로 해결되지 않는 영속 계약이다.  
Why it matters: 재배포 후 정상 replay 거절, legacy/current head 불일치, rolling writer의 값 유실 가능성이 있다. 실제 신규 속성을 배포해 장애를 재현한 것은 아니다.  
Example change scenario: 결과 묶음 속성을 nullable로 추가하고 기존 key 요청을 재시도한다.  
Recommended action: 저장 형식·지문 version 및 absent/null/default 의미를 변경 전에 정한다. 기존 golden hash와 구형 JSON을 보존하고 upgrade/replay/복구·구버전 writer 시험을 추가한다. backfill은 ACTIVE write fence/원장과 맞추며 현재 값으로 과거 snapshot을 만들지 않는다.  
Estimated scope: command canonicalization·snapshot/codec·migration/복구 도구·호환 fixture; 요구에 따라 sales snapshot.  
Regression risk: High — 과거 receipt/Mutation 의미, effective head, baseline manifest 및 취소 복원.  
Related findings: BE-001, BE-012, BE-016, BE-036, BE-039.

### BE-014 — Work 유형·handler·계보 분류의 독립된 대응

ID: BE-014  
Severity: Medium  
Category: Domain extensibility / Classification consistency  
Affected modules: work, farm  
Evidence: **현재 코드 확인**, 05 A. [StructureChangeLineageQueryService.isStructureChangeExecution](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeLineageQueryService.java)는 저장 handler code를 `WorkTypeDefinition.forCode`에 넣는다. [OrchidGroupLineageService.relationType](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupLineageService.java)는 MOVEMENT/REPOT/DIVIDE/MERGE switch다. 정의·handler/strategy supports·DB seed는 별도다.\
Problem: 유형 code와 handler code가 같은 의미라는 전제가 조회에 숨어 있다. registry의 구현 누락 검사가 계보 분류까지 검증하지 못한다.  
Why it matters: 신규 실행은 성공하지만 계보에서 빠지거나 조회 default 예외가 날 수 있다. 기존 사용자 기록형 template 추가에는 해당 확장이 필요 없고 현재 등록된 유형 장애는 확인하지 않았다.  
Example change scenario: 유형과 다른 handler 이름을 사용하는 새 STRUCTURE_CHANGE 유형을 등록한다.  
Recommended action: 저장 효과의 종류/정의와 계보 관계 대응을 명시적인 계약으로 연결한다. 등록→실행→상세/계보→취소→replay를 시험하고 seed/capability 대응도 확인한다. 새로운 유형마다 모든 enum을 늘리는 방향은 피한다.  
Estimated scope: work 분류·farm 계보 mapping·startup 검증 및 새 유형 회귀.  
Regression risk: Medium — MOVE/MOVEMENT 등 기존 호환 분류 및 과거 저장 handler 의미.  
Related findings: BE-012, BE-024, BE-043.

### BE-015 — 쓰기 단계의 상세 응답 재사용과 반복 조회

ID: BE-015  
Severity: Medium  
Category: Orchestration / Query amplification  
Affected modules: work, farm/inbound, sales, settlement  
Evidence: **현재 코드 확인**, CQ-001·PERF-08·05 F. [StructureChangeRecordService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java)는 계획→시작→실행→최종 조회마다 WorkOperationView를 받는다. [InboundPottingOperationService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java)는 request별 상세 응답으로 target/progress를 읽고 getAll을 반복한다. Sales의 lockStates/Engine/최종 state 조회와 정산 snapshot/표시 참조 재조회도 있다.\
Problem: 내부 상태 전환 결과와 외부 상세 응답 계약이 결합되어 필요한 것보다 넓은 조회·조립이 반복된다. 모든 재조회가 불필요한 것은 아니다.  
Why it matters: 조회 필드 추가가 쓰기 성능·실패 경로에도 영향을 준다. 배치 record 수에 따라 비용이 증폭되며 기존 일반 즉시 완료 120 target 시험은 구조 변경/포트 경로를 보호하지 않는다.  
Example change scenario: Work 상세에 새 관계 정보를 추가하면 즉시 포트·배치 구조 기록의 중간 조립도 늘어난다.  
Recommended action: 내부에는 ID/target/진행 결과를 반환하고 최종 응답을 한 번 조립한다. 검증 시점·잠금 후 재확인·snapshot 생성은 유지한다. 실제 경로별 SQL breakdown 및 rollback·replay 응답 계약을 함께 시험한다.  
Estimated scope: 관련 application 내부 결과·assembler 호출 경계 및 경로별 회귀; 공개 응답 유지 가능.  
Regression risk: High — 완료 판정, snapshot 시점, lock 재확인, receipt 반환 순서.  
Related findings: BE-012, BE-030, BE-033, BE-042.

### BE-016 — 구조 변경 계획의 생성 요청 DTO 경유

ID: BE-016  
Severity: Low  
Category: Change cost / Mapping duplication  
Affected modules: farm, work  
Evidence: **현재 코드 확인**, CQ-002·05 D. [BatchStructureTransformationExecutor.planResults/mutationCommand](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java)는 ResultPlan에 OrchidGroupCreateRequest를 만들고 MutationDetails로 다시 복사한다. 이 중간 DTO 단계에 별도 API 호출/요청 validation은 없다.\
Problem: 원본 확보·상속·원장 실행·결과 기록 사이에 의미를 추가하지 않는 속성 전달이 있다.  
Why it matters: 결과 속성 변경 때 상속과 보존을 여러 mapper에서 확인해야 한다. 범위가 단일 실행기 중심이고 현재 값 유실은 재현되지 않아 CQ-002의 Medium을 Low로 조정했다.  
Example change scenario: nullable 속성을 추가하면서 일반 생성 DTO와 구조 결과 계획을 동시에 수정한다.  
Recommended action: ResultPlan이 placement/MutationDetails/purpose를 직접 보유하게 하고 실행 전 source 값 확보와 적용 후 결과를 구분한다. 단계별 새 Service를 늘리지 않는다.  
Estimated scope: 단일 실행기 내부 계획·mapping, 상속/동일 ID 이동/취소 회귀.  
Regression risk: Medium — source 차감 전 상속, 이동의 ID 보존, purpose별 상태.  
Related findings: BE-012, BE-013, BE-033.

### BE-017 — Work 취소 판단의 종류·잠금·응답 책임 혼합

ID: BE-017  
Severity: Medium  
Category: Change cost / Cancellation policy  
Affected modules: work, farm  
Evidence: **현재 코드 확인**, CQ-004. [WorkOperationVoidService.inspectCancellation/cancelOperation/cancelBatch](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java)는 상태별 blocker, 연관 폐기, effect/mutation 수집, 기록형/포트/구조 분기, lock 모드와 영향 응답을 함께 조립한다.\
Problem: 새 취소 조건이 eligibility·실행·batch·포트에 같은 우선순위로 적용되는지 지역 누적 상태와 early return을 함께 추적해야 한다.  
Why it matters: 원장 보상 및 연관 작업을 다루는 변경의 검토 범위가 크다. 의존 수·메서드 길이가 아니라 같은 정책의 판단 모드와 표현이 얽힌 비용이다. 현재 취소 원자성 실패를 확인한 것은 아니다.  
Example change scenario: 실사 이후 취소 blocker를 추가하고 조회와 실제 batch 취소의 결과를 맞춘다.  
Recommended action: 상태/종류 판정→관련 효과 수집→종류별 검사→응답 변환을 작은 내부 단계와 결과로 나눈다. 조회 검사와 잠금 후 재검사는 모두 유지하며 새 blocker를 양쪽 모드와 batch에서 시험한다.  
Estimated scope: Work 취소 서비스 내부 단계, Farm 검사 port 계약 및 PG 취소 회귀.  
Regression risk: High — blocker 우선순위, effective head·보상 순서·연관 폐기 atomicity.  
Related findings: BE-006, BE-020, BE-037.

### BE-018 — 그래프 조립의 가변 상태와 위치 기반 생성

ID: BE-018  
Severity: Low  
Category: Change cost / Graph assembly  
Affected modules: work, farm/mutation  
Evidence: **현재 코드 확인**, CQ-005. [WorkOperationGraphQueryService.addMutationFlow](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationGraphQueryService.java)는 입력 Map와 출력 nodes/edges를 함께 받고 truncated를 별도로 반환한다. 종류별 노드는 21개 component의 응답 생성자에 위치 기반 값/null을 넣는다.\
Problem: 추가 노드 필드와 상한 변경이 서로 다른 가변 상태·동일 타입 인자의 대응을 요구한다.  
Why it matters: 컴파일은 성공해도 필드 위치나 truncated 의미를 틀릴 여지가 있다. 국소 조회 조립이며 실제 오류가 확인되지 않아 CQ-005를 Low로 조정했다. 적재 상한의 성능 위험은 BE-034에서 별도로 다룬다.  
Example change scenario: 그래프 노드에 새 표시 속성을 추가하고 상한 도달 시 edge/truncated를 변경한다.  
Recommended action: 내부 조립 context/result와 DTO 가까운 종류별 factory로 값 이름을 드러낸다. 순서·노드/edge 정합성·상한/truncated 계약을 보호한다.  
Estimated scope: 그래프 서비스/DTO factory와 계약 시험; schema 유지 가능.  
Regression risk: Medium — 그래프 순서, visible operation 관계, truncation 의미.  
Related findings: BE-034, BE-012.

### BE-019 — 공개 application 계약과 내부 API 혼재

ID: BE-019  
Severity: Medium  
Category: Module boundary / Public contracts  
Affected modules: farm, work, sales; 신규 입력 adapter  
Evidence: **현재 코드 확인**, ARC-001·05 F·07 §5. [OrchidGroupReader](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java)는 외부 값 API와 Optional<Entity> API를 함께 public으로 제공한다. Farm은 WorkOperationSupport helper도 사용한다. 일부 application은 HTTP DTO를 사용하고 validation/인가/감사 identity는 주로 HTTP 진입부가 제공한다.\
Problem: 소비자가 사용할 승인된 업무 계약과 내부 조립/Entity 경로를 타입 접근 수준에서 구분하기 어렵다. 비 HTTP 소비자는 자동 validation/HTTP 권한/context를 기대할 수 없다.  
Why it matters: 내부 helper 변경이 다른 모듈에 전파되고 새 채널에서 입력/actor/transaction proxy 전제를 빠뜨릴 수 있다. 현재 타 모듈 Entity 사용 위반이나 비 HTTP 권한 우회 배포는 확인하지 않았다.  
Example change scenario: Reader 내부 Entity 메서드를 정리하거나 Agent adapter가 기존 서비스에 직접 명령을 전달한다.  
Recommended action: 실제 소비자의 값·잠금·업무 계약을 식별하고 내부 Entity/helper 접근을 좁힌다. 새 채널은 검증·신뢰된 주체·인가·감사 context·DI proxy·재시도 계약을 제공한다. 모든 서비스에 interface/복제 DTO를 추가하지 않는다.  
Estimated scope: 소비자 중심 공개 타입 분리·architecture gate; 새 채널 도입 부분은 조건부.  
Regression risk: Medium — 내부 JPA 변경 감지, 호출자 주입 타입, HTTP 및 감사 identity 보존.  
Related findings: BE-008, BE-021, BE-022, BE-041.

### BE-020 — Farm adapter에 분산된 Work 보정 조율

ID: BE-020  
Severity: Medium  
Category: Ownership / Transaction orchestration  
Affected modules: work, farm, audit  
Evidence: **현재 코드 확인**, ARC-002. [WorkOperationCorrectionService.create](../../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java)는 저장 callback을 [WorkCorrectionPort](../../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkCorrectionPort.java)에 넘긴다. [FarmWorkCorrectionAdapter.correct](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java)는 callback 실행 시점·Work 날짜 비교/변경·Farm Mutation·결과 조립을 결정한다.\
Problem: Work가 루트 transaction/저장소를 소유하지만 실제 순서 일부는 Farm adapter가 조율한다.  
Why it matters: 날짜만 변경하는 보정도 Farm을 통과하고 adapter 수정에 Work 감사 ID·날짜·replay까지 알아야 한다. runtime 재진입은 존재하지만 소스 DAG/Bean 순환 문제나 현재 rollback 실패는 아니다.  
Example change scenario: 날짜-only 보정이나 새로운 농장 correction adapter를 추가한다.  
Recommended action: Work가 날짜·감사 ID·최종 조립을 조율하고 Farm은 농장 규칙 검증/변경 결과를 반환한다. 감사 ID를 Mutation 전에 확보하고 모두 같은 transaction에 둔다. callback 제거를 transaction 분리로 구현하지 않는다.  
Estimated scope: Work service/port·Farm adapter·날짜 service 호출 및 보정 회귀.  
Regression risk: High — correction receipt/event/Mutation atomicity, no-op·날짜-only·실사/후속 사용 제한.  
Related findings: BE-010, BE-012, BE-017, BE-037.

### BE-021 — Farm에 노출된 Work Receipt 메커니즘

ID: BE-021  
Severity: Medium  
Category: Application API / Idempotency ownership  
Affected modules: farm/inbound, work  
Evidence: **현재 코드 확인**, ARC-003. [InboundRecordService.voidPotting](../../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java)는 `INBOUND_POTTING_VOID:` scope·PottingVoidIdentity·callback·Work ID 목록을 [WorkCommandReceipts.executeExisting](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java)에 전달한다. execute/executeExisting 선택이 creation membership 의미를 결정한다.\
Problem: Farm이 업무 명령뿐 아니라 Work 접수 namespace·지문 표현·membership 저장 방식을 결정한다.  
Why it matters: 새 소비자가 잘못된 scope/creation 방식을 고르면 replay나 관계 의미가 달라진다. 현재 입고 취소 멱등성 결함이 재현된 것은 아니다.  
Example change scenario: 포트 취소의 request identity나 Work receipt membership 모델을 바꾼다.  
Recommended action: 포트 취소의 typed 업무 계약으로 외부 API를 좁히고 Work 내부에서 scope·fingerprint·membership을 선택한다. 기존 identity와 지문을 그대로 읽을 수 있게 하고 입고/보상/감사의 atomicity를 유지한다.  
Estimated scope: Work 전용 업무 API·Farm 호출자·receipt/replay/취소 회귀.  
Regression risk: High — 저장된 key·생성 membership·취소 후 replay 및 입고 상태.  
Related findings: BE-008, BE-013, BE-019, BE-020.

### BE-022 — Sales 감사의 Settlement 내부 helper 결합

ID: BE-022  
Severity: Low  
Category: Ownership / Audit contracts  
Affected modules: sales, settlement, audit  
Evidence: **현재 코드 확인**, ARC-004. [SalesPaymentService.confirmPayment](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java)는 [SettlementAuditSupport](../../backend/src/main/java/com/greenhouse/backend/settlement/application/SettlementAuditSupport.java)의 전표 snapshot/record helper를 사용한다. 같은 helper에는 Settlement 내부 Entity 감사도 있다.\
Problem: Sales 전표의 감사 표현이 Settlement 내부 기능과 같은 공개 helper에 묶여 있다. 입금 원장 API 의존과 구별되는 불필요한 변경 결합이다.  
Why it matters: Settlement 감사 내부 변경을 Sales 계약 변경인지 함께 검토해야 한다. 타 모듈 Entity 직접 전달이나 현재 금융 오류는 없다.  
Example change scenario: Settlement 감사 snapshot 필드 또는 helper 가시성을 변경한다.  
Recommended action: 전표 snapshot은 Sales가 소유하고 공통 Audit 값 계약을 사용한다. 공유 입금 의미가 필요하면 전용 값 API로 좁힌다. 전표 감사와 입금 event 감사는 다른 사실이므로 하나를 제거하지 않는다.  
Estimated scope: Sales 감사 조립·Settlement helper 공개 범위·입금 감사/rollback 시험.  
Regression risk: Medium — source/필드명·중복 감사 및 입금 전체 rollback.  
Related findings: BE-010, BE-019, BE-020.

### BE-023 — 직접 판매 공통 정책의 생성·수정 중복

ID: BE-023  
Severity: Low  
Category: Domain rule duplication  
Affected modules: sales  
Evidence: **현재 코드 확인**, CQ-006. [SalesSlipCreationService.create](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java)와 [SalesSlipUpdateService.update](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java)에 거래처 필수·품목 필수·경매장 금지 조건/메시지가 반복된다. paymentStatus 기본값은 SalesType와 문자열 `미입금`으로 갈린다.\
Problem: 동일 직접 판매 입력 정책을 두 유스케이스에서 따로 유지한다. 생성의 경매 분기·수정 가능성 검사는 별도 규칙이다.  
Why it matters: 거래처 정책·기본값·오류 변경 때 한쪽만 바꿀 가능성이 있다. 현재 다른 업무 결과를 만든다는 재현은 없어 Low다.  
Example change scenario: 새 거래처 분류의 직접 판매 허용이나 기본 입금 상태를 변경한다.  
Recommended action: 실제 공통 정책과 기본값만 작은 domain policy/함수로 모은다. 생성/수정 고유 제약과 HTTP/application 단계별 방어는 유지한다.  
Estimated scope: sales 두 service·정책/default와 오류 계약 회귀.  
Regression risk: Medium — 경매 수정 차단, 품목 개수 제한 및 API 오류.  
Related findings: BE-004, BE-026.

### BE-024 — 호출되지 않는 전략 옵션과 주입 의존

ID: BE-024  
Severity: Low  
Category: Change cost / Inactive contracts  
Affected modules: work, farm/transformation  
Evidence: **현재 참조 검색**, CQ-007. [ImmediateWorkExecutionService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/ImmediateWorkExecutionService.java)의 appliedEffectRepository는 선언만 있다. `requiresEverySourceResult`는 [StructureChangeStrategy](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategy.java)와 [MovementStrategy](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/MovementStrategy.java)에만 선언되고 호출되지 않는다.\
Problem: 필수 협력자처럼 보이는 unused injection과 실제 실행에 영향을 주지 않는 정책 옵션이 있다.  
Why it matters: 전략 override 변경이 동작을 바꾼다고 잘못 판단할 수 있다. 작은 추적 비용이며 현재 장애는 없다. 실제 registry/handler·CLI·migration 클래스 전체를 dead code로 분류하지 않는다.  
Example change scenario: 전략 옵션을 바꿔 모든 source의 result 생성을 강제했다고 판단한다.  
Recommended action: unused injection은 정리하고 옵션은 제거하거나 명시적 정책으로 연결한다. 후자는 기능 변경으로 취급하고 source/result 검증을 시험한다.  
Estimated scope: 필드/생성자 및 전략 계약 두 파일 중심.  
Regression risk: Low — 단순 제거; 동작 연결 시 Medium 이상이며 별도 도메인 회귀 필요.  
Related findings: BE-014, BE-016, BE-025.

### BE-025 — 즉시 실행 명령의 반복 분해·정규화

ID: BE-025  
Severity: Low  
Category: Change cost / Command plumbing  
Affected modules: work  
Evidence: **현재 코드 확인**, CQ-010. [ImmediateWorkExecutionService.execute/executeForTarget](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/ImmediateWorkExecutionService.java)는 fingerprint용 ImmediateCommand를 만들고 callback/private 메서드에 8/9개 인자를 다시 전달한다. actor도 private 경로에서 다시 정규화한다.\
Problem: 동일 입력이 record·메서드 서명·callback에 반복된다. 같은 String 인자 위치 오류를 타입 검사가 막지 못한다.  
Why it matters: 필드·기본값 추가의 국소 변경 비용이 늘어난다. 현재 값 전달 오류나 replay 불일치는 확인하지 않았다.  
Example change scenario: 즉시 작업의 actor/default 또는 명령 속성을 추가한다.  
Recommended action: 이미 만든 ImmediateCommand를 내부 실행에도 사용하고 정규화 시점을 일치시킨다. 공개 서명이나 범용 framework를 먼저 바꾸지 않는다.  
Estimated scope: 단일 서비스 내부 전달과 fingerprint/actor 회귀.  
Regression risk: Medium — 지문 대상 값과 실제 실행 값, demo actor 의미.  
Related findings: BE-013, BE-024.

### BE-026 — 멱등 key 충돌의 오류 계약 차이

ID: BE-026  
Severity: Low  
Category: Error contracts / Idempotency  
Affected modules: work, settlement, common  
Evidence: **현재 코드 확인**, CQ-009. [WorkCommandReceipt.validate](../../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkCommandReceipt.java)/WorkCorrectionReceipt는 409 `IDEMPOTENCY_KEY_REUSED`, [PartnerPaymentEvent.validateReplay](../../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerPaymentEvent.java)는 IllegalArgumentException→400 `VALIDATION_ERROR`다. PaymentTests는 현재 400을 기대한다.\
Problem: key 재사용 충돌을 일반 입력 오류와 구별하는 관례가 업무마다 다르다.  
Why it matters: 클라이언트 분기·새 유스케이스의 오류 선택 기준이 불명확하다. 현재 payment 응답을 금융 처리 버그로 판정하지 않는다.  
Example change scenario: 같은 key의 다른 금액 요청을 새 API에서 처리한다.  
Recommended action: 입력 오류/현재 상태 충돌/key 충돌의 안정적인 code 선택 기준을 정한다. 기존 payment 변경은 호환성을 검토해 적용하며 모든 IllegalArgumentException을 409로 바꾸지 않는다.  
Estimated scope: 예외 관례·선택한 domain exception·Controller/클라이언트 계약 및 테스트.  
Regression risk: Medium — 기존 HTTP status/code 소비자 호환.  
Related findings: BE-005, BE-008, BE-023.

## Persistence와 성능

### BE-027 — 경매 변경 응답 mapper의 N+1

ID: BE-027  
Severity: Medium  
Category: Performance / Lazy loading  
Affected modules: auction  
Evidence: **PG 측정**, PERF-01·07 §6/9. [AuctionTrackingService](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java) 쓰기→findForUpdate(shipment만 fetch)→[AuctionLotResponse.from](../../backend/src/main/java/com/greenhouse/backend/auction/dto/AuctionLotResponse.java)에서 attempt별 resultLines를 읽는다. 시도 1/10/50개에 SQL 5/14/54, Entity load 6/24/104였다. commit DML은 측정에 포함하지 않았다.\
Problem: GET의 일괄 assembler와 다른 쓰기 응답 경로가 lazy collection을 순회한다.  
Why it matters: 이력이 누적될수록 변경 응답 및 쓰기 transaction 보유 시간이 늘어난다. GET query 회귀는 이를 잡지 못한다. 운영 SLA 실패는 측정하지 않았다.  
Example change scenario: 재경매 시도가 오래 누적된 lot의 상태를 보정한다.  
Recommended action: 필요한 attempts/resultLines/history를 명시적으로 일괄 로딩/조립하고 1/10/50개의 독립 시도에서 SQL 증가를 검사한다. root lock과 commit 비용도 함께 확인한다.  
Estimated scope: auction write 응답 조립·Repository bulk 조회·PG query 회귀.  
Regression risk: Medium — 응답 이력 순서/완전성, 쓰기 검증 및 잠금.  
Related findings: BE-005, BE-007, BE-034, BE-042.

### BE-028 — 과거 직접 계보 mapper의 N+1

ID: BE-028  
Severity: Medium  
Category: Performance / Association loading  
Affected modules: farm/transformation  
Evidence: **PG 측정**, PERF-02·07 §6/9. [OrchidGroupLineageService.getLineage](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupLineageService.java) 직접 sources/results 경로의 graph에 위치 tree/variety/inbound가 없다. mapper가 현재 위치/년생을 읽어 독립 연결 1/10/50개에서 SQL 11/38/158, Entity load 9/54/254였다.\
Problem: Work effect 기반 일괄 조회와 달리 지원 중인 legacy/direct lineage가 불완전 graph로 상세 mapper를 호출한다.  
Why it matters: 위치 공유 fixture와 1차 cache가 N+1을 숨긴다. 과거 자료의 계보 조회가 누적 연결 수에 비례해 느려진다. 모든 신규 Work 계보가 같은 문제를 겪는 것은 아니다.  
Example change scenario: 서로 다른 위치·입고·품종을 가진 과거 결과 연결을 조회한다.  
Recommended action: 그룹 참조를 모아 완전한 상세 조회/projection을 적용하고 cache 공유 없는 fixture로 크기별 query 회귀를 추가한다.  
Estimated scope: farm 직접 계보 조회·mapper loading 계약·PG 회귀.  
Regression risk: Medium — Work-covered 연결 제외, 결과 순서와 현재 년생 의미.  
Related findings: BE-012, BE-014, BE-034, BE-042.

### BE-029 — 구역 profile 조회의 불필요한 난 묶음 적재

ID: BE-029  
Severity: Medium  
Category: Performance / Entity overfetch  
Affected modules: farm/structure  
Evidence: **PG 측정**, PERF-03·07 §6/9. [BedPlacementProfileService.findZone](../../backend/src/main/java/com/greenhouse/backend/farm/application/structure/BedPlacementProfileService.java)→[BedZoneRepository.findWithDetailsById](../../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/BedZoneRepository.java)는 orchidGroups+capacities를 fetch한다. 그룹 1/10/50개에서 SQL은 1회지만 그룹 Entity는 1/10/50개 적재됐다. 두 collection의 G×C join row 증폭은 정적 위험이며 JDBC rows는 미측정이다.\
Problem: profile/감사에 필요 없는 난 묶음이 공용 상세 graph 때문에 전부 읽힌다.  
Why it matters: SQL count가 고정이어도 heap·DB 전송 비용은 증가한다. 정상 profile 기능에 재고 전체 규모가 불필요하게 영향을 준다.  
Example change scenario: 수천 그룹을 가진 구역의 배치 용량 설정을 조회한다.  
Recommended action: profile 전용 graph/projection에서 orchidGroups를 제외하고 capacities·house만 필요한 범위로 조회한다. 그룹 load 0 및 G/C 증가 실험을 추가한다.  
Estimated scope: profile 조회 전용 Repository 계약·국소 PG 회귀.  
Regression risk: Low — 설정 응답/감사 값의 완전성 확인.  
Related findings: BE-030, BE-042.

### BE-030 — Work 요약을 위한 전체 target Entity 적재

ID: BE-030  
Severity: Medium  
Category: Performance / Summary projection  
Affected modules: work  
Evidence: **PG 측정**, PERF-04·07 §6/9. [WorkOperationRelationSummaryAssembler.assemble](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationRelationSummaryAssembler.java)는 inbound 식별자를 얻기 위해 root의 전체 target를 읽고 child Entity로 개수를 센다. 기존 benchmark의 root 100×target 20에서 SQL 7회에 target Entity 2,000개가 적재됐다.\
Problem: summary에 필요한 ID/count를 얻기 위해 snapshot JSON 포함 전체 하위 Entity를 로딩한다.  
Why it matters: root pagination과 query-count 성공으로 target fan-out의 heap 비용을 제한할 수 없다.  
Example change scenario: root 페이지는 100개지만 각 작업 target가 200개로 증가한다.  
Recommended action: distinct inbound IDs와 관계 count를 projection/DB 집계로 조회한다. root 수 고정·target 수 증가의 Entity load/rows/할당량 회귀를 추가한다.  
Estimated scope: Work relation summary·Repository projection 및 PG benchmark/gate.  
Regression risk: Medium — parent/child 관계·inbound 집합·개수 의미.  
Related findings: BE-015, BE-029, BE-034, BE-042.

### BE-031 — 품종·자동 그룹의 전체 Entity 기반 Java 집계

ID: BE-031  
Severity: Medium  
Category: Performance / Aggregation and filtering  
Affected modules: farm, work  
Evidence: **코드 확인**, PERF-05. [VarietyResponseAssembler](../../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyResponseAssembler.java)는 page 품종의 모든 활성 그룹/위치로 합계·최신 작업일을 계산하고 전체 그룹 IDs를 Work IN 조회에 전달한다. [DerivedOrchidGroupService](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/DerivedOrchidGroupService.java)는 전체 candidates의 상세 DTO를 만든 뒤 현재 년생 필터·집계·정렬을 한다.\
Problem: 응답 summary보다 넓은 Entity/DTO를 전부 적재하고 Java에서 집계·후처리한다. 품종 pagination이 하위 그룹 수를 제한하지 않는다.  
Why it matters: 그룹 수 증가가 heap·CPU·큰 IN에 직접 반영된다. 잘못된 root pagination 이후 filtering이 발견된 것은 아니며 현재 년생은 업무일/입고일을 반영해야 한다.  
Example change scenario: 한 품종에 난 묶음이 10,000개이고 자동 그룹 필터의 선택도가 낮다.  
Recommended action: 필요한 합계/ID/날짜를 projection·집계로 읽고 상세 member는 별도 제한 조회한다. 년생 조건을 DB로 옮길 때 동일 businessDate 의미를 시험한다.  
Estimated scope: farm summary/derived 쿼리·Work 날짜 집계 계약·조회/필터 회귀.  
Regression risk: Medium — 판매 가능 분류·그룹 key·현재 년생·최신 작업일.  
Related findings: BE-004, BE-032, BE-034, BE-042.

### BE-032 — 모듈 검색의 반복 조회와 전체 ID IN 전달

ID: BE-032  
Severity: Medium  
Category: Performance / Cross-module query amplification  
Affected modules: partner, sales, auction, farm, work  
Evidence: **benchmark 관측·코드 확인**, PERF-06. [BusinessPartnerReader.findMatchingIds](../../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerReader.java)는 500개 keyset batch 결과를 전체 누적한다. [AuctionTrackingService.getLots](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)는 공백별 prefix·전체 contains·market exact 검색을 실행한다. 일치 수 501→5,001에서 Sales SQL 5→14, Auction 8→17 및 allocation 증가를 관측했다. 전체 IDs는 content/count의 IN/OR에 들어간다.\
Problem: page 크기와 별개로 일치하는 전체 ID 집합을 모으고 같은 의미의 검색을 여러 번 수행한다. getAllInfo 및 일부 lineage/history IN도 일괄 상한 보장이 없다.  
Why it matters: 흔한 이름·공백 많은 검색어에서 SQL 길이·plan·메모리 비용이 증가한다. 실제 JDBC 인자 한계 초과/운영 지연 임계값은 미측정이다.  
Example change scenario: 50,000개 거래처와 여러 공백을 가진 검색어로 마지막 1행 page를 요청한다.  
Recommended action: 검색 의미를 유지하는 dedupe/bulk module API를 검토하고 공백·일치 수·빈 결과별 IN/OR 계획을 측정한다. 일부 ID를 잘라 total/page 의미를 바꾸거나 다른 모듈 테이블을 직접 join하지 않는다.  
Estimated scope: partner 검색 계약·sales/auction 조건 조립·큰 ID 조회 batching·PG plan/회귀.  
Regression risk: High — 검색어 경계 해석, 전체 total·pagination 및 모듈 소유권.  
Related findings: BE-019, BE-031, BE-035, BE-042.

### BE-033 — Mutation 배치 placement 검사의 반복 Repository 호출

ID: BE-033  
Severity: Medium  
Category: Performance / Write transaction duration  
Affected modules: farm/mutation, farm/structure, work, inbound  
Evidence: **코드 확인·부하 미측정**, PERF-07·07 §9. [OrchidGroupMutationEngine.transform/createFromInbound/validateBatchMovePlacements](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java)는 결과/item별 [OrchidPlacementPolicy](../../backend/src/main/java/com/greenhouse/backend/farm/application/structure/OrchidPlacementPolicy.java) 조회를 호출한다. 같은 구역의 그룹을 반복 읽고 자동 배치에서 정렬한다. 복구는 bulk query여도 nested 비교가 남는다.\
Problem: R개 결과/G개 기존 그룹의 검증이 대략 R회 구역 조회·O(RG), 새 결과까지 비교하며 O(R²) 비용을 만들 수 있다. 쿼리 사이 저장은 AUTO flush로 insert batching을 끊을 가능성도 있다.  
Why it matters: group/zone 잠금을 유지한 채 비용이 늘어 공유 구역의 다른 쓰기를 기다리게 한다. 실제 lock 대기/flush/throughput 장애는 측정하지 않아 규모 위험으로 평가했다.  
Example change scenario: 하나의 구역에 다수 입고 결과를 자동 배치하거나 구조 변경 결과를 대량 생성한다.  
Recommended action: zone 잠금 후 interval/sort 값을 구역별 한 번 읽고 새 결과를 동일 메모리 검증 상태에 반영한다. R/G 독립 증가·query/flush/lock duration과 충돌/rollback을 함께 시험한다. 검증이나 잠금을 제거하지 않는다.  
Estimated scope: Engine placement 계획·policy bulk 조회·배치/경쟁 PG 회귀.  
Regression risk: High — 중간 결과 겹침·자동 위치·복원·동시 생성 불변식.  
Related findings: BE-006, BE-015, BE-016, BE-035, BE-042.

### BE-034 — root 상한 밖의 무제한 목록·하위 이력

ID: BE-034  
Severity: Medium  
Category: Performance / Bounded retrieval  
Affected modules: farm, work, auction, sales, settlement  
Evidence: **코드 확인**, PERF-09. 전체 그룹/sellable/derived/collection 및 구형 work-history/lineage는 누적 결과 상한이 없다. Work calendar 기간 폭도 무제한이다. Work/Inbound/lot/Mutation root page는 targets/results/attempts/entries/relations를 제한하지 않는다. [SalesQueryService.getAuctionShipmentOptions](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesQueryService.java)는 used 제외 후 200개를 채울 때까지 후보 pages를 반복한다. 그래프는 내부 참조를 읽은 뒤 출력 nodes를 자르는 부분이 있다.\
Problem: 응답 root/output 상한을 전체 DB scan·하위 Entity·전송량의 상한으로 사용할 수 없다.  
Why it matters: 오래된 이력과 높은 child fan-out에서 heap·JSON·DB 비용이 계속 증가한다. 후보 후처리는 옳은 결과를 내도 거의 모두 사용된 경우 많은 OFFSET pages를 읽는다. 주요 root pagination 뒤 잘못된 Java 행 필터링은 확인하지 않았다.  
Example change scenario: 한 lot에 이력이 누적되거나 최근 경매 출하 후보 대부분이 이미 전표에 연결된다.  
Recommended action: 운영 선택지/누적 이력에 pagination·범위 상한·별도 child 조회를 정한다. graph의 내부 탐색/관계 fan-out도 계측한다. 전체 farm map은 의도된 계약과 viewport 대안을 고려하고 무조건 페이지화하지 않는다.  
Estimated scope: endpoint별 조회 계약·child 분리·Controller/OpenAPI/소비자 변경 및 크기 회귀.  
Regression risk: High — 완전한 과거 이력/선택지·페이지 total·graph truncation 계약.  
Related findings: BE-018, BE-027, BE-028, BE-030, BE-031, BE-042.

### BE-035 — 실제 조회·FK·검색에 대한 index 검증 부족

ID: BE-035  
Severity: Medium  
Category: Persistence / Index and plan risk  
Affected modules: farm, sales, auction, settlement, partner, work  
Evidence: **migration/쿼리 확인·plan 미측정**, PERF-10. [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) 및 이후 INDEX 검색에서 group bed_zone/inbound, item slip, allocation item/group, movement slip, settlement line parent/lot의 대응 선두 index를 찾지 못했다. 날짜/id 정렬·상태 집계·lower/contains/OR/concat 계획도 확인하지 않았다. Auction FK·Work/Mutation 다수 index는 이미 있다.\
Problem: FK나 기존 UNIQUE/index의 존재를 실제 다른 선두 조건·정렬·문자열 검색 지원으로 간주할 수 없다.  
Why it matters: 데이터 누적 후 scan/sort/참조 검사 비용과 transaction 시간이 커질 수 있다. 작은 fixture의 sequential scan은 정상일 수 있고 운영 병목으로 확정하지 않았다.  
Example change scenario: 같은 구역의 재고가 늘거나 전체 기간 정산·공통 이름 검색을 자주 요청한다.  
Recommended action: 선택도/건수를 바꾼 실제 PG `EXPLAIN (ANALYZE, BUFFERS)`로 scan/rows/sort/estimate를 확인하고 필요한 FK·정렬·partial/검색 index만 추가한다. leading-wildcard 검색에 일반 B-tree 추가만으로 해결하려 하지 않는다.  
Estimated scope: DB 실행계획 실험→확인된 Repository/index migration·PG 회귀.  
Regression risk: Medium — index 생성/쓰기 비용·배포 잠금·선택도별 plan 변화.  
Related findings: BE-032, BE-033, BE-034, BE-036, BE-042.

### BE-036 — 정산 초기화·원장 대사의 전체 메모리와 긴 transaction

ID: BE-036  
Severity: Medium  
Category: Operational performance / Batch processing  
Affected modules: settlement, auction, partner, farm/mutation  
Evidence: **코드 확인**, PERF-11. [AuctionSettlementService.rebuildExistingResults](../../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java)는 500개 조회 후 미연결 결과 전체를 누적·정렬하고 전체 partner 잠금/정산 merge를 한 transaction에 수행한다. 기본 활성 [AuctionSettlementInitializer](../../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementInitializer.java)의 startup 경로다. [OrchidGroupLedgerReconciliationService](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerReconciliationService.java)는 전체 그룹/Entry를 누적한다.\
Problem: 조회 batching을 처리 메모리·commit/clear 단위로 사용하지 않는다. min/max 날짜 사이 기존 정산 전체를 읽는 과다 범위도 있다.  
Why it matters: 최초 대량 정산은 startup 시간·partner lock·heap을, 대사 CLI는 장기 이력·persistence context를 크게 만들 수 있다. 이미 연결된 결과 fast path 및 일반 page와 다른 위험이며 운영 실패는 미측정이다.  
Example change scenario: 장기 미연결 결과를 처음 정산하거나 ACTIVE 전환 전에 큰 원장을 대사한다.  
Recommended action: 최초/fast path를 구분해 peak heap/GC/lock duration을 측정한다. 정산별 원자성을 보장하는 처리 단위와 재시작 의미를 정하고 batch/clear·기간 조회 범위를 개선한다. 다중 인스턴스 연결 재확인과 입금 snapshot 보존을 유지한다.  
Estimated scope: 정산 batch/initializer·대사 CLI 처리 단위·운영 절차 및 PG 대량/재실행 회귀.  
Regression risk: High — transaction 분할의 부분 처리·중복 line·입금/기존 snapshot 보존 및 대사 완전성.  
Related findings: BE-011, BE-013, BE-035, BE-042, BE-044.

## Regression 방어력과 문서 계약

### BE-037 — 외부 transaction·느슨한 실패 assertion의 거짓 확신

ID: BE-037  
Severity: Medium  
Category: Testing / Transaction rollback  
Affected modules: test infrastructure, work, sales, settlement, farm  
Evidence: **테스트 코드 확인**, 07 §2/3.2/7. H2 MockMvc의 test-level `@Transactional` 및 Sales/입금의 외부 TransactionTemplate는 caller 참여를 검증하지만 root transaction 누락을 가릴 수 있다. [WorkBatchCancellationPostgresE2ETest.failureAfterMutationFlushRollsBackGroupsWorkAndAudit](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkBatchCancellationPostgresE2ETest.java)는 HTTP ≥400/불변 상태만으로 의도한 후속 CHECK 도달을 확정하지 못한다.\
Problem: 시험의 바깥 경계가 production root 경계를 대신하거나 early rejection이 late rollback 시험을 통과시킬 여지가 있다.  
Why it matters: 중요한 수량·입금·취소 변경에서 잘못된 transaction 분리나 실패 주입 위치를 놓칠 수 있다. 실제 HTTP PG·독립 감사 rollback 등 강한 보완 시험도 있으므로 전체 suite가 무효라는 판정은 아니다.  
Example change scenario: 최상위 @Transactional을 제거하거나 요청이 mutation 적용 전에 거절되게 바뀐다.  
Recommended action: 핵심 쓰기는 외부 test transaction 없는 application/HTTP 호출 뒤 새 transaction에서 상태를 읽는다. 늦은 실패 지점·예상 cause/code 도달을 먼저 확인하고 receipt/audit/원장/수량/금액 전체 복구를 검사한다.  
Estimated scope: 선택한 rollback 시험·실패 주입 helper·PG root boundary 회귀.  
Regression risk: Low — 테스트 보강 자체; 임시 constraint와 격리 정리는 주의.  
Related findings: BE-003, BE-017, BE-020, BE-039.

### BE-038 — 동시성 시험의 충돌 지점 관측 공백

ID: BE-038  
Severity: Medium  
Category: Testing / Concurrency  
Affected modules: farm, sales, work, settlement test suites  
Evidence: **테스트 코드 확인**, 07 §4/7. [OrchidGroupMutationRoutingPostgresE2ETest.creationCancellationWaitsForTheGroupLock](../../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupMutationRoutingPostgresE2ETest.java)는 시작 신호 후 200ms 미완료를 관찰한다. 일부 경쟁 시험은 latch/executor 제출 뒤 최종 상태만 본다. pg_stat_activity helper는 전체 DB waiter 수를 센다. Sales 생성의 정렬 시험은 old/new 교차 수정과 다른 경로다.\
Problem: worker scheduling 지연을 row-lock 대기로 오인하거나 중요한 pair의 실제 overlap을 보장하지 못할 수 있다. 전역 waiter는 향후 병렬 시험에서 다른 요청과 혼동될 수 있다.  
Why it matters: lock 제거/순서 변경을 놓칠 수 있다. 실제 lock owner·lock_timeout·wait 관찰·spy barrier를 쓰는 강한 시험도 존재하고 이번 전체 실행에서 flaky failure는 관찰하지 않았다.  
Example change scenario: 실행기를 변경해 worker 도달이 늦어지거나 suite를 병렬화한다.  
Recommended action: worker PID/application_name 또는 충돌 직전 barrier로 정확한 대기를 관찰하고 양쪽 순서·최종 invariant/HTTP 오류/rollback을 시험한다. 재현된 다단계 lock cycle은 BE-006의 회귀로 포함한다.  
Estimated scope: concurrency helper·선택한 pair 시험·DB 격리.  
Regression risk: Medium — 계측/spy가 원래 잠금 타이밍을 바꾸지 않도록 확인.  
Related findings: BE-002, BE-005, BE-006, BE-039, BE-040.

### BE-039 — 테스트 표현·fixture의 과도한 변경 민감도

ID: BE-039  
Severity: Low  
Category: Testing / Brittleness and fixture maintenance  
Affected modules: integration/PG test infrastructure, farm, work  
Evidence: **현재 테스트 확인**, CQ-008·07 §7/8. [InboundPottingPlanIntegrationTests](../../backend/src/test/java/com/greenhouse/backend/InboundPottingPlanIntegrationTests.java)/[WorkOperationIntegrationTests](../../backend/src/test/java/com/greenhouse/backend/WorkOperationIntegrationTests.java)는 JSON 인접/순서 정규식으로 ID를 읽는다. migration 시험은 최신 34·7개/21~34 목록을 고정한다. 년생 시험에 system LocalDate가 있고 fixture는 SQL/payload/lock helper·seed MIN/OFFSET를 반복하며 일부 단건에도 큰 공통 layout을 만든다.\
Problem: 업무 의미와 무관한 JSON·seed·migration head 변경이 실패를 만들 수 있다. 긴 복합 시나리오의 앞 실패는 뒤 보존 검사 실행을 막는다.  
Why it matters: 변경 때 원인 파악과 fixture 수정 비용이 증가한다. 현재 flaky 실패는 없고 테스트 유지비가 중심이므로 CQ-008의 Medium을 Low로 조정했다.  
Example change scenario: JSON 필드 순서 변경·V35 추가·기본 farm seed 변경 후 다수 시험이 실패한다.  
Recommended action: JSON tree/path·fixed Clock·caller 소유 작은 fixture를 사용한다. 과거 upgrade 구간과 최신 schema gate를 구분하고 lifecycle 및 독립 사례를 함께 둔다. 정상/legacy/corrupt builder를 구분하며 pooled sequence·전역 TRUNCATE/임시 CHECK 격리를 보존한다. Golden API/hash 및 도메인적으로 필요한 InOrder는 유지한다.  
Estimated scope: 대상 테스트의 parser·fixture/helper·migration/time assertion, 순차 정리 가능.  
Regression risk: Medium — fixture가 의도한 실제 writer/과거 오류를 숨기거나 기대값을 production 계산기로 대체할 위험.  
Related findings: BE-013, BE-037, BE-038, BE-041.

### BE-040 — 공통 HTTP·일부 future timeout 부재

ID: BE-040  
Severity: Low  
Category: Testing / Execution reliability  
Affected modules: PostgreSQL test infrastructure  
Evidence: **코드 확인**, 07 §7. [WorkE2ETestBase](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkE2ETestBase.java)의 HttpClient/HttpRequest에 connect/request timeout이 없다. [SalesSlipNumberPostgresE2ETest](../../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesSlipNumberPostgresE2ETest.java)의 invokeAll/future.get도 무제한이며 공통 JUnit timeout을 찾지 못했다.\
Problem: 서버 응답이나 worker가 멈추면 해당 요청/시험의 대기 상한이 없다.  
Why it matters: CI가 오래 대기하고 실패 원인 자료를 얻기 어려울 수 있다. 운영 backend 자체의 timeout 결함으로 확대하지 않는다.  
Example change scenario: 테스트 서버가 deadlock/connection 문제로 응답하지 않는다.  
Recommended action: 예상 시험 시간을 고려한 HTTP·future·JUnit 상한과 종료/진단을 추가한다. 긴 migration/benchmark를 무조건 짧게 제한하지 않는다.  
Estimated scope: 공통 PG client·동시성 future helper·task 시간 정책.  
Regression risk: Low — 느린 CI의 false timeout을 피하도록 설정.  
Related findings: BE-038, BE-042.

### BE-041 — architecture gate의 공개 API·새 writer 탐지 한계

ID: BE-041  
Severity: Low  
Category: Testing / Architecture enforcement  
Affected modules: architecture tests, farm/mutation, work, sales  
Evidence: **테스트 코드 확인**, ARC-001·02 §2.3·07 §5. [ModuleBoundaryInventoryTest](../../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java)는 compiled Entity/Repository 의존을 막지만 모든 application helper 공개 범위를 제한하지 않는다. query 검사는 정규식이다. [OrchidGroupWriterArchitectureTest](../../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupWriterArchitectureTest.java)는 변경 메서드 이름 inventory에 의존한다.\
Problem: 새 mutator를 목록에 넣지 않거나 동적/alias SQL을 쓰면 일부 위반이 gate 범위 밖에 남는다.  
Why it matters: structure test 통과를 모든 쓰기·transaction·domain invariant 보장으로 해석하면 잘못된 확신이 생긴다. 현재 예외 inventory 0·compiled gate·PG fence가 보완하므로 실제 ownership 붕괴로 판정하지 않는다.  
Example change scenario: OrchidGroup에 새 상태 변경 메서드를 추가하거나 internal helper를 타 모듈에서 사용한다.  
Recommended action: 공개 계약 allowlist/가시성을 좁히고 새 mutator가 writer gate에 포함되는지 자동 검사한다. raw DB fence negative 시험은 별도로 유지하고 query regex 한계를 문서화한다. SQL parser 도입은 실제 필요가 확인될 때 결정한다.  
Estimated scope: architecture inventory/타입 접근 규칙·writer gate 회귀.  
Regression risk: Low — 의도된 복구 writer·기존 값 계약을 잘못 금지할 가능성.  
Related findings: BE-019, BE-024, BE-039.

### BE-042 — query-count·benchmark의 측정 경계 공백

ID: BE-042  
Severity: Medium  
Category: Testing / Performance regression gates  
Affected modules: benchmark/query test infrastructure, farm, work, auction, sales, settlement  
Evidence: **기존 gate 통과 및 추가 PG 측정**, 06 §2/6·07 §6/9. GET 중심 query gate는 BE-027/028의 write/legacy mapper를 놓쳤고 SQL 1/7회로 BE-029/030의 과다 적재도 통과했다. Work benchmark는 100×20 및 이력 1건, Search는 501/5,001·공백 1개다. Hibernate 준비 SQL 수는 JdbcTemplate·실행 round trip·rows·heap·lock wait를 모두 측정하지 않는다.  
Problem: query 수와 제한된 fixture 성공을 쓰기/하위 fan-out·메모리·운영 latency 방어로 확대하기 어렵다. 시간 median/p95는 기록값이며 SLA gate가 아니다.  
Why it matters: 실제로 확인된 N+1/과다 적재가 기존 CI를 통과한다. 신규 regression probe는 현재 /tmp에 있어 영구 gate가 아니다.  
Example change scenario: target/attempt 수가 커지거나 처음 미연결 정산을 대량 처리한다.  
Recommended action: BE-027~036별 권고를 영구 회귀로 옮기고 SQL 증가식·Entity/rows/할당량·응답 bytes를 추가한다. 쓰기에는 commit/flush·lock 시간을 분리한다. 부하는 통제된 fixture에서 측정하고 시간 절대값을 즉시 CI 실패 조건으로 만들지 않는다.  
Estimated scope: path별 PG query/Entity gate·benchmark fixture/계측·조건부 운영 부하 실험.  
Regression risk: Medium — 전역 Hibernate 통계/병렬화·환경 변동의 false failure 및 과도한 임계값 완화.  
Related findings: BE-015, BE-027, BE-028, BE-029, BE-030, BE-031, BE-032, BE-033, BE-034, BE-035, BE-036.

### BE-043 — 현행 정책 문서와 구현의 차이

ID: BE-043  
Severity: Low  
Category: Documentation / Operational contracts  
Affected modules: farm, work, sales; backend 기준 문서  
Evidence: **현재 문서/코드 대조**, 03 §11. [docs/04-architecture.md](../../docs/04-architecture.md)의 Farm 예약 Legacy/Engine 분기 설명은 현재 Engine 직접 경로와 다르다. [DOMAIN_RULES.md](../../docs/api/DOMAIN_RULES.md)의 전체 Work 완료 대상 조건에는 실제 CANCELED terminal 예외 설명이 필요하다. 일반 수정 권한의 ADR 차이는 BE-009로 분리했다.\
Problem: 다음 작업자가 사용할 기준 문서에 제거된 경로와 불완전한 terminal 규칙이 남는다.  
Why it matters: 개선 시 Legacy 재도입 또는 정상 완료 예외 제거를 유도할 수 있다. 단독 런타임 장애는 없어 Low다.  
Example change scenario: 문서만 보고 새 예약 경로를 추가하거나 완료 validation을 정리한다.  
Recommended action: 현재 코드/정책 합의를 기준으로 의미가 달라진 설명만 갱신한다. API DTO 목록을 중복 작성하지 않고 schema가 바뀔 때만 명세를 재생성한다.  
Estimated scope: architecture/domain rule 설명·관련 정책 문서 대조; BE-009의 정책 결정은 별도.  
Regression risk: Low — 문서 수정; 잘못된 설명을 새 구현 근거로 삼지 않도록 확인.  
Related findings: BE-004, BE-009, BE-014, BE-019.

### BE-044 — 정산 설정의 저장값과 실행 capability 구분 부족

ID: BE-044  
Severity: Low  
Category: Capability contracts / MVP scope  
Affected modules: settlement, partner, sales  
Evidence: **현재 코드 확인**, 05 E. [SettlementUnit](../../backend/src/main/java/com/greenhouse/backend/settlement/domain/SettlementUnit.java)에 MONTHLY_BATCH가 있고 [PartnerSettlementSettings](../../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerSettlementSettings.java)는 settlementUnit/ruleJson/autoSettleEnabled를 저장·응답한다. 실제 실행은 직접 전표 입금과 경매장+경매일 정산이며 이 설정들에 대응하는 방식별 실행 dispatch는 없다.\
Problem: 설정을 표현/저장할 수 있다는 계약과 실제 지원 업무를 쉽게 혼동할 수 있다. 새 주간/월간 정산 aggregate가 없다는 것 자체는 현재 MVP 결함이 아니다.  
Why it matters: 값 추가/설정만으로 자동 정산이 수행된다고 잘못 판단하면 변경 범위를 과소평가한다. 현재 잘못된 금액 생성이나 자동 정산 promise 위반은 확인하지 않았다.  
Example change scenario: MONTHLY_BATCH를 설정하거나 새 unit을 추가한 뒤 전표 grouping·입금 분배가 구현됐다고 판단한다.  
Recommended action: 실제 실행 capability와 보관용 설정 의미를 계약/정책에 명시한다. 새 방식이 승인되면 grouping·입금 분배·원본 전표 제한·잔액 중복 방지·취소 보존을 먼저 정의한다. 이번 감사로 범위 밖 정산 기능을 구현하지 않는다.  
Estimated scope: 현재는 capability/설명·설정 검증 범위; 신규 정산은 별도 업무 모델·DB·API 프로젝트.  
Regression risk: Medium — 기존 설정값 보존, 클라이언트 지원 범위 해석; 향후 실제 금액 모델 변경은 High.  
Related findings: BE-008, BE-019, BE-022, BE-036.

## 원본 문제의 통합 대응표

아래 대응은 같은 증상이 아니라 원인·수정 책임을 기준으로 한다. 예를 들어 CQ-001과 PERF-08은 중간 상세 응답을 쓰기 결과로 사용하는 같은 변경으로 개선되므로 BE-015에 합쳤다. 반대로 caller의 잘못된 key(BE-001), key 자체가 없는 경매 요청(BE-005), 일반 생성 재시도 계약 부재(BE-008)는 발생 지점과 수정 계약이 달라 분리했다.

| 원본 | 통합 ID / 처리 |
| --- | --- |
| 02 ARC-001 | BE-019; gate 한계는 BE-041 |
| 02 ARC-002 | BE-020 |
| 02 ARC-003 | BE-021 |
| 02 ARC-004 | BE-022 |
| 02 ARC-005 | BE-012 |
| 03 DC-01 | BE-001 |
| 03 DC-02 | BE-003 |
| 03 DC-03 | BE-004 |
| 03 DC-04 | BE-002 |
| 03 DC-05 | BE-005 |
| 03 DC-06 및 §9.1의 경로별 누적/group-zone 순서 위험 | BE-006. 실제 교착과 미재현 경로를 구분 |
| 03 DC-07 | BE-007 |
| 03 §9.2 일반 생성의 HTTP identity 없음 | BE-008 |
| 03 §9.3 NOT VALID·교차 DB 불변식 | BE-011. 예약/금액/lot 동치의 실제 결함은 BE-001/003/007; DB가 모든 snapshot 동치를 보장해야 한다는 독립 요구로 확대하지 않음 |
| 03 §10 감사 범위·필드 누락 | BE-010; 상태 유지 수량 이력 누락은 BE-007 |
| 03 §11 활성/판매 상태 분산 | BE-004 |
| 03 §11 일반 수정과 ADR 정책 차이 | BE-009 |
| 03 §11 architecture Legacy 설명·CANCELED terminal 누락 | BE-043 |
| 04 CQ-001 | BE-015 (PERF-08과 통합) |
| 04 CQ-002 | BE-016 |
| 04 CQ-003 | BE-012 (ARC-005와 통합) |
| 04 CQ-004 | BE-017 |
| 04 CQ-005 | BE-018 |
| 04 CQ-006 | BE-023 |
| 04 CQ-007 | BE-024 |
| 04 CQ-008 | BE-039 (07의 brittle/fixture 문제와 통합) |
| 04 CQ-009 | BE-026 |
| 04 CQ-010 | BE-025 |
| 05 A: 유형/handler/계보/seed 대응 | BE-014; 효과 형식은 BE-012/013. 이미 지원하는 사용자 기록형 유형 추가 자체는 문제 아님 |
| 05 B: 상태 의미 확장 | BE-004/009. string 저장·enum 미사용 자체는 별도 결함 아님 |
| 05 C: 예약 정책/lifecycle/source key | BE-001/004/006/008. reserve→outbound에서만 차감하는 승인된 lifecycle 자체는 유지 |
| 05 D: 속성 전달·과거 JSON/hash·snapshot/backfill | BE-012/013/016, 감사 대응은 BE-010. 서로 다른 업무 snapshot을 저장하는 것 자체는 필수 복잡성 |
| 05 E: 설정과 실제 방식의 차이 | BE-044; 감사 결합은 BE-022. 새 주간 정산 모델·분배·이중 채권 위험은 신규 기능의 설계 조건이며 현재 이중 채권 결함으로 집계하지 않음 |
| 05 F: 비 HTTP 채널의 전제 | BE-019/008/021/015. 신뢰/validation/context 연결의 조건부 비용이며 미배포 Agent 보안 취약점으로 집계하지 않음 |
| 06 PERF-01~07 | 순서대로 BE-027~033 |
| 06 PERF-08 | BE-015 |
| 06 PERF-09~11 | 순서대로 BE-034~036 |
| 06 §2/6 benchmark 누락·관측 단위 한계 | BE-042; 개별 원인과 측정/CI 방어 개선 책임을 구별 |
| 07 §2/3.2 외부 test transaction·§7 후속 실패 도달 불명확 | BE-037 |
| 07 §4 충돌 관측·다단계 lock cycle 누락 | BE-038; 실제 cycle 원인은 BE-006 |
| 07 §5 architecture inventory/regex 사각지대 | BE-041/019 |
| 07 §6 query/benchmark 공백 | BE-042 및 BE-027~036 |
| 07 §7/8 brittle·fixture 중복/seed/시간/격리 | BE-039; 무제한 HTTP/future 대기는 BE-040 |
| 07 §9 누락된 재고/금액/경매 sequence | BE-001~007에 해당 영구 회귀 포함. 별도의 동일 결함 ID를 만들지 않음 |

## 확인 후보와 독립 finding으로 올리지 않은 사항

| 사항 | 재검토 결과 / 남은 확인 |
| --- | --- |
| 프로필 동시 편집·Farm 배치의 실제 deadlock | 정적 확인 후보. BE-006과 관련하지만 현재 유실 갱신/교착을 재현했다고 주장하지 않음 |
| 전체 CHECK negative 조합·운영 constraint coverage | PG 핵심 fence/FK/UNIQUE는 시험함. 모든 제약과 legacy 행은 미확인; BE-011/037 후속 검증에 포함 |
| 인증 켠 핵심 workflow의 모든 권한 조합 | Auth integration은 별도로 존재하고 PG 업무 suite는 auth disabled. 전체 조합 미검증이며 인증 부재나 배포 권한 취약점의 증거는 아님; 새 채널은 BE-019 전제를 검증 |
| 구조 수량 int 경계·상태별 추가 전이 | domain 수량 balance와 기존 제한의 보완 범위는 있으나 모든 장기 합계 경계를 조사 완료한 것은 아님. Sales overflow(BE-003)를 다른 경로의 재현으로 확대하지 않음 |
| 타 모듈 Entity 직접 참조·소스 의존 순환·외부 Native SQL | 현재 architecture inventory에서 위반 0. 검사 한계는 BE-041이며 확인되지 않은 위반을 추가하지 않음 |
| 잠금 후 재검증·snapshot별 복사·Work/Farm 이중 원장 | 동시성 및 서로 다른 과거 사실을 보호하는 필요한 동작. 단순 중복/파일 수로 제거하지 않음 |
| migration/복구·공개 legacy endpoint | 실제 운영/호환 경로다. 데이터 제거 조건 미확인 상태에서 dead code로 판단하지 않음 |
| Mockito InOrder·API/hash golden·reflection/raw SQL | 필요한 순서/저장 호환·과거 오류 fixture일 수 있음. 결과 독립성/DB 검증을 함께 확인하며 전부 brittle로 분류하지 않음 |
| 일반 명칭·많은 의존/인자·테스트 개수·JaCoCo/PIT 부재 | 독립적인 운영 위험 증거가 없음. 구체 값 전달/정책 결합/회귀 공백만 finding으로 유지 |

## 적용 순서와 검증 상태

1. **BE-001/002/003/004/005**: 재현을 올바른 기대값의 영구 회귀로 옮기고 예약 identity·이력 보존·금액 범위·상태 정책·경매 replay를 수정한다. /tmp 진단의 잘못된 결과 기대값을 그대로 정상 회귀로 복사하지 않는다. 재고/금액 불일치 자료는 대사 후 복구한다.
2. **BE-006/007/009/010/011**: 다단계 lock 순서·수량 보정 사실·변경 권한·감사·legacy 제약의 범위를 확정한다. 기존 rollback/receipt/snapshot/입금 보존 시험을 유지한다.
3. **BE-027~030**: 이미 측정된 N+1/불필요한 Entity 적재를 국소 수정하고 크기별 query/Entity gate를 추가한다. **BE-031~036**은 대표 규모/plan/heap/lock 측정 후 처리 단위를 정한다.
4. 계약 정리는 BE-012/013의 영속 호환성과 BE-020/021의 atomicity를 먼저 확인한다. BE-016/018/023~026/039~041 같은 Low를 먼저 정리해 운영 결함 해결을 늦추지 않는다.

이번 통합에서 새 테스트를 실행하거나 production/test/DB/API 코드를 바꾸지 않았다. 동일 HEAD에서 완료된 검증과 XML을 재확인했다.

| 기존 검증 | 결과 / 통합 시 활용 |
| --- | --- |
| 기본 `./gradlew test --rerun-tasks` | 113개 클래스·525 invocation, 실패/오류/skip 0 |
| 전체 `./gradlew workE2eTest` | 35개 클래스·177 invocation, 실패/오류/skip 0. 03/06 당시의 집중 실행 한계를 이후 07에서 보완 |
| `workBenchmark -PworkBenchmarkEnforce=true` | 2개 invocation, 실패/오류/skip 0. 제한된 query gate이며 운영 SLA 검증 아님 |
| frontend `npm run check` | 동일 HEAD의 기존 성공 결과 재사용 |
| 03 임시 domain probe / 06 임시 performance probe | 각 문서의 재현 입력·관찰값을 통합 근거로 사용. 각각 PG 8/10개 진단 및 Java 도메인 관찰; 영구 저장소 테스트가 아님 |

기본/PG/benchmark XML은 `backend/build/test-results/{test,workE2eTest,workBenchmark}`에 있다. 03/06/07에 명시된 /tmp probe·로그는 단기 진단 자료이므로 문서의 재현 조건과 측정 경계를 함께 남겼다. 통합 문서의 44개 ID·필수 필드·관련 ID·로컬 링크를 검사했다.

미실행/미확인: 결함 수정 후 회귀(아직 코드 미수정), 운영 DB/자료의 제약 상태·불일치 건수, 실제 대규모 실행계획·heap/GC·동시 부하/SLA·soak, 모든 권한/DB negative 조합, CI branch protection. 기존 전체 suite 성공이 위 findings의 해결을 의미하지 않는다.
