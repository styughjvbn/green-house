# Backend 감사 개선 진행

- 작업일: 2026-10-03~2026-10-06
- 브랜치: `fix/backend-sales-reservation-consistency`
- 시작 기준: `80106917232a671b5a489ad8e59d37b63a06dffe` (`develop`)
- 우선순위 기준: [08 통합 findings](08-findings.md), [09 최종 평가](09-final-assessment.md)의 P0. 기존 감사 문서는 수정 전 판단의 근거로 보존한다.
- 읽기 기준: 차수별 변경 기록은 당시의 구현·검증 이력이다. 현재 후속 작업과 보류 상태는 마지막 [남은 작업](#남은-작업)에서 확인한다.

## 1차 변경 — BE-001 판매 수정 예약 identity

상태: 코드 수정 및 회귀 검증 완료. 기존 운영 데이터의 불일치 여부는 확인하지 않았다.

### 수정 전 재현

영구 PostgreSQL 회귀 중 규격·품목 메모·전표 메모 수정과 같은 총액의 배분 변경 7건을 기존 구현에 먼저 실행했다. 5건이 실패했다.

- 규격·품목 메모만 수정하면 allocation 5개를 유지하면서 예약이 5개에서 0개로 사라졌다. 품목만 수정되면 root JPA version이 증가하지 않아 재예약을 이전 Mutation의 replay로 처리했다.
- 같은 총액을 유지한 채 다른 그룹으로 배분을 옮기면 기존 source key와 새 command fingerprint가 충돌했다.
- root 전표 메모 수정 2건은 정상 동작했다. 수정 종류에 따라 version 변경 여부가 달랐다.

### 구현

- [SalesSlipUpdateService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java)가 각 수정 실행에 UUID를 한 번 발급한다. 기존 root 전표 잠금과 최상위 트랜잭션 안에서 해제·재예약이 같은 변경 식별자를 공유한다.
- [SalesSlipInventoryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java)는 수정에 `EDIT:<UUID>:RELEASE` / `EDIT:<UUID>:RESERVE`를 사용한다. identity는 기존 Mutation source key에 영속화한다. 최초 생성·출고·취소의 기존 key 형식은 유지한다.
- [OrchidGroupMutationResult](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationResult.java)는 실제 적용과 replay를 구분한다. Sales는 replay 시 이동 이력을 다시 생성하지 않는다. 이를 확인하기 위한 별도 조회는 추가하지 않았다.
- Sales 재고 helper는 `MANDATORY`로 호출자의 트랜잭션을 요구한다. 재고, Mutation, 전표·배분, 이동 이력, 잔액·감사의 기존 원자적 변경 경계를 유지한다.
- 수정 API 재호출은 새로운 편집이다. 클라이언트 요청 키 기반의 HTTP 멱등 처리를 추가한 것은 아니다. HTTP schema, 저장 fingerprint 형식, Flyway migration은 변경하지 않았다.
- 판매 기능 문서, 도메인 규칙, 백엔드 구현 기준에 수정 identity와 replay 계약을 반영했다.

### 회귀 방어

[SalesInventoryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesInventoryPostgresE2ETest.java)에 PostgreSQL 10건을 추가했다.

| 시나리오 | 보호하는 결과 |
| --- | --- |
| 규격·품목 메모·전표 메모 각각 2회 수정 후 작성중 취소 / 출고 후 취소 | 예약 유지, 배분별 이동 이력, 출고 수량 차감 및 취소 복구, 수정 snapshot 시점 |
| 같은 총액의 배분을 두 그룹 사이에서 3회 변경 후 출고·취소 | 새 배분에 예약 반영, 이전 그룹 예약 해제, fingerprint 충돌 방지 |
| 다른 전표가 같은 그룹 7개를 예약한 상태에서 5개 전표 수정·출고·취소 | 다른 전표 예약 보존, snapshot에 다른 전표 예약 반영 |
| 같은 예약 Mutation 2회 replay | 수량·Mutation·배분별 이동 이력 중복 방지 |
| 재예약 이동 저장을 테스트용 DB CHECK로 실패시킨 후 재시도 | 전표·배분·예약·Mutation·이동·감사 rollback, 재시도 성공 |

Mutation integration test에도 실제 적용/replay 구분 검증을 추가했다. 기존 판매 생성·경매 출하·취소·동시 예약·과예약 거절·FK·HTTP snapshot 시험을 함께 유지한다.

테스트 확대 중 기존 fixture의 `RESTART IDENTITY`가 Hibernate의 남아 있는 pooled sequence 할당과 충돌했다. 해당 클래스는 기존 `resetKeepingSequences()`를 사용하도록 바꿨다. 데이터 초기화는 유지하고 sequence 재시작만 피한다. 같은 두 번째 그룹 fixture는 기존 동시성 시험과 새 배분 시험에서 공유한다.

### 검증

- 집중 검증: Mutation integration 3건, 판매 PostgreSQL 20건 성공.
- 백엔드 전체 `./gradlew test`: 113개 클래스, 525건 성공. 기존 architecture 및 query-count regression 포함.
- PostgreSQL 전체 `./gradlew workE2eTest`: 35개 클래스, 187건 성공. 기존 177건에 새 회귀 10건 추가.
- `./gradlew spotlessCheck`, `git diff --check`: 성공.
- 프론트엔드 `npm run check`: 성공.
- 1차 전체 검증 직후에는 이 진행 문서의 결과만 갱신했다. 이후 BE-002의 별도 수정·검증은 아래에 기록한다.

## 2차 변경 — BE-002 경매 출하 취소의 이력 보존

상태: 코드 수정 및 전체 회귀 검증 완료. 기존 삭제 이력의 운영 영향·복원 가능 여부는 확인하지 않았다.

### 수정 전 재현

새 PostgreSQL 회귀 6건을 기존 구현에 실행했고 모두 실패했다. 낙찰·부분 낙찰·유찰·반환 추정 결과를 등록한 뒤 `WAITING`으로 상태를 보정하면 전표 취소가 성공했다. 경매 시도는 남고 상태 이력이 없는 legacy 조건, 결과 없이 상태 이력만 남은 조건에서도 취소로 기록을 삭제할 수 있었다.

### 구현과 원인

- Auction의 취소 불가 조회는 실제 경매 시도·상태 이력, 판매·반환 수량과 현재 상태를 함께 확인한다. 변경 가능한 현재 상태만으로 과거 기록을 대체하지 않는다. Sales의 `availableActions`와 취소 실행은 같은 Auction 조회 기준을 사용하며, 기존 정산 연결 검사도 유지한다.
- 출하 삭제는 모든 lot를 ID 오름차순으로 잠근 뒤 기록을 다시 조회한다. 결과·반환·보정·상태 변경도 lot root를 직접 잠근다. 쓰기 잠금 조회의 shipment fetch graph를 제거해 부모 행 잠금이 조회 편의 때문에 섞이지 않게 했다. 삭제·전표 연결 해제·재고 복구·Mutation·이동·감사는 기존 Sales 최상위 트랜잭션에 참여한다.
- 첫 경쟁 시험에서 정산 연결 조회가 lot Entity를 잠금 전에 로딩해, 기다리던 취소가 최신 버전과 충돌하는 문제가 드러났다. 정산 연결에 필요한 lot→shipment ID 조회를 projection으로 바꿨다. 모듈 간 기존 ID Map 계약은 유지하며 Repository projection은 Auction 안에서 값으로 변환한다. 불필요한 Entity loading이 단순 조회 비용을 넘어 잠금 이후 판단에 영향을 주는 경로를 제거했다.
- 처리 이력이 없는 대기 출하의 정상 취소·수량 복구는 유지한다. 기록이 생긴 출하는 취소로 삭제할 수 없다. 새 shipment 취소 모델이나 경매 상태 전이 재설계는 추가하지 않았다.
- HTTP schema·enum·오류 형식·Flyway migration은 변경하지 않았다. 새 기록 존재 조회는 V16의 shipment/lot/attempt/history 인덱스를 사용할 수 있는 소유 모듈 JPQL이다. 목록의 취소 판단은 일괄 조회를 유지한다.
- 판매·경매 정책 문서, 도메인 규칙, 백엔드 잠금 기준을 갱신했다.

### 회귀 방어

[AuctionShipmentCancellationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/AuctionShipmentCancellationPostgresE2ETest.java)에 PostgreSQL 10건을 추가했다.

| 시나리오 | 보호하는 결과 |
| --- | --- |
| 4종 경매 결과 → `WAITING` 보정 → 전표 취소 | 취소 거절, 출하·시도·결과·상태 이력·전표·재고·Mutation·감사 보존, capability 일치 |
| 시도는 있고 상태 이력은 없는 legacy 자료 | 현재 상태·수량·상태 이력만으로 실제 시도 존재를 놓치지 않음 |
| 결과 없이 상태를 변경한 뒤 다시 `WAITING` | 상태 이력도 취소로 삭제하지 않음 |
| 이력이 없는 출하 취소 | shipment/lot 제거와 전표 연결 해제·수량 복구의 기존 동작 유지 |
| 정상 취소·삭제를 flush한 뒤 후속 실패·재시도 | 모든 관련 테이블 rollback, 취소 가능 상태 복원, 재시도 성공 |
| 결과 입력이 먼저 lot 잠금 획득 | 기다린 취소가 최신 기록으로 거절되고 결과·이력과 출하 수량 보존 |
| 취소가 먼저 lot 잠금 획득 | 기다린 결과 입력은 삭제된 lot로 거절, 결과·시도·상태 이력의 orphan 생성 없음 |

경쟁 시험은 두 트랜잭션의 PostgreSQL backend PID와 `pg_blocking_pids`로 실제 잠금 대기를 확인한 뒤 승자를 commit한다. 시간 지연만으로 경쟁이 발생했다고 판정하지 않는다. 테스트 초기화는 데이터를 지우되 Hibernate pooled sequence와 충돌하지 않게 sequence를 유지한다.

### 검증

- 집중 검증: 판매·경매·정산·capability·기존 query-count 35건, PostgreSQL 판매 20건 및 경매 취소 9건 성공. 이후 후속 실패 rollback 시험 1건을 추가했다.
- 프론트엔드 `npm run check`: 성공.
- 백엔드 전체 `./gradlew test`: 113개 클래스, 525건 성공. architecture 및 query-count regression 포함.
- PostgreSQL 전체 `./gradlew workE2eTest`: 36개 클래스, 197건 성공. 경매 취소 신규 10건과 이전 BE-001 회귀 모두 포함.
- `./gradlew spotlessCheck`, `git diff --check`: 성공.
- 전체 검증 이후 변경은 이 진행 문서의 결과 갱신뿐이며 실행 코드·테스트는 바꾸지 않았다.

## 3차 변경 — BE-003 판매 금액 overflow

상태: 코드·DB 제약·정책 문서 수정 및 전체 회귀 검증 완료. 기존 운영 금액은 대사하거나 자동 보정하지 않았다.

### 수정 전 재현

- 도메인 회귀 14건 중 13건이 기존 구현에서 실패했다. 금액 초과·잘못된 수량/단가를 거절하지 않았고, 품목 수정·추가·교체·재계산에서 잘못된 상태를 허용했다. 이 수에는 자기 품목 목록으로 교체하면 목록이 비는 기존 실패도 포함한다.
- PostgreSQL 생성 3건·수정 3건 모두 기존 구현에서 실패했다. 수량 2×단가 15억원은 음수로, 수량 3×단가 15억원은 양수 205,032,704원으로 돌아왔다. 각각 15억원인 두 품목의 합계도 초과를 거절하지 않았다. 단순 비음수 검사로는 양수 overflow를 방어할 수 없다.

### 구현

- `SalesSlipItem`이 생성·수정의 수량·단가 검증과 `Math.multiplyExact`를 소유한다. 모든 검증을 필드 변경 전에 수행해 실패한 품목 수정이 메모·수량·단가를 일부 바꾸지 않는다.
- `SalesSlip`이 품목 합계를 `Math.addExact`로 계산한다. 추가·교체는 합계 검증 후 품목을 연결하고, 기존 품목 수정 후 재계산도 같은 규칙을 사용한다. 교체 입력은 먼저 복사해 자신의 목록을 전달해도 안전하게 처리한다. 새 범용 금액 추상화는 추가하지 않았다.
- 품목과 전표의 현재 INTEGER 저장 범위를 유지한다. 초과는 기존 `400 / VALIDATION_ERROR`로 거절하며 잘라 저장하거나 0원으로 보정하지 않는다. HTTP DTO·schema는 변경하지 않아 OpenAPI와 생성 TypeScript를 다시 만들지 않았다.
- 최상위 판매 생성·수정 트랜잭션을 유지한다. 수정 중 이미 수행한 예약 해제, Mutation, 이동 이력과 품목·배분·스냅샷·감사·잔액은 금액 검사 실패 시 전체 rollback한다.
- V35는 품목 수량 양수·단가/금액 비음수·수량×단가와 금액의 일치, 전표 합계 비음수 CHECK를 추가한다. DB 곱셈은 BIGINT로 수행해 제약 자체의 INTEGER overflow를 피한다. 여러 품목의 합계는 행 CHECK로 보호할 수 없으므로 도메인의 정확 합계를 함께 유지한다.
- 제약은 `NOT VALID`로 추가한다. 기존 금액·입금·잔액을 backfill하지 않고 새 행과 기존 행 갱신을 즉시 검증한다. 기존 위반 품목은 메모 변경도, 음수 총액 전표는 상태 변경도 거절될 수 있다. 운영 대사·감사 가능한 복구·전체 제약 검증은 별도 작업이며 배포 문서에 적용 순서를 반영했다.
- 기존 V27→V34 Work 통합 migration 시험은 해당 범위로 target을 고정했다. 원장 통합 migration 목록 시험도 소유하는 V21~V34로 조회 범위를 제한했다. 미래 migration이 추가될 때마다 해당 통합 맥락의 기대 개수·버전을 바꾸는 취약성을 제거하고, V34→V35 금액 제약 업그레이드는 별도 실제 PostgreSQL 시험에서 검증한다.

### 회귀 방어

- [SalesSlipAmountRulesTest](../backend/src/test/java/com/greenhouse/backend/sales/domain/SalesSlipAmountRulesTest.java): 도메인 14건. 음수·양수 overflow, null/잘못된 수량·단가, 실패 시 품목/전표 상태 보존, 최대 합계·부분입금·완납, 0원 및 자기 목록 교체를 검증한다.
- [SalesAmountPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAmountPostgresE2ETest.java): PostgreSQL 14건. HTTP 생성 실패 3건·서비스 수정 실패 3건에서 관련 14개 테이블과 기존 상세·예약을 비교하고 정상 재시도도 확인한다. 기존 정상 수정의 감사·스냅샷·재고 이동이 남은 상태에서 후속 실패를 검증한다. 최대 금액 저장·잔액·완납과 동일 키 재요청, 0원 경매 출하, SQL 우회 시 품목 5종·음수 전표 거절을 검증한다. 입금 replay는 수신·연결 이벤트를 포함한 전체 snapshot 보존으로 확인한다. SQL 배열과 JSONB는 JDBC 객체 identity 대신 DB의 JSON 값으로 비교한다.
- [SalesAmountMigrationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAmountMigrationPostgresE2ETest.java): 음수·양수 overflow legacy 자료를 V34에 넣고 V35로 갱신하는 2건. 기존 행 불변, 신규 삽입·기존 행 갱신 차단, 위반이 남아 있는 상태의 제약 검증 실패, Flyway 재기동 시 추가 적용 없음과 checksum 검증을 확인한다.

### 검증

- 집중 검증: 도메인 금액·판매 상태 25건 성공. PostgreSQL 금액·migration·기존 Work 업그레이드 시험에서 입금 이벤트 개수 기대를 보완한 뒤 해당 입금 회귀 성공.
- 백엔드 전체 `./gradlew test`: 114개 클래스, 539건 성공. 신규 도메인 14건과 기존 architecture·query-count 회귀 포함.
- 첫 PostgreSQL 전체 실행: 38개 클래스, 213건 중 212건 성공. 새 V35가 추가돼 기존 원장 통합 migration 목록의 고정 기대 1건이 실패했다. 해당 조회 범위를 V21~V34로 고정한 뒤 전체를 다시 실행했다.
- PostgreSQL 전체 `./gradlew workE2eTest` 최종 실행: 38개 클래스, 213건 성공. 신규 16건과 BE-001·BE-002를 포함한 기존 197건 모두 성공.
- 프론트엔드 `npm run check`, `./gradlew spotlessCheck`, `git diff --check`: 성공.
- 백엔드 전체 성공 이후 실행 코드 변경은 없다. 이후 변경은 PostgreSQL 전용 migration 시험의 조회 범위와 정책·결과 문서뿐이다. 해당 시험은 최종 PostgreSQL 전체 실행에 포함했고, 최종 전체 검증 뒤에는 이 문서의 결과만 갱신했다.

## 4차 변경 — BE-004 판매 가능 상태 정책 통일

작업일: 2026-10-04. 상태: 코드·정책 문서 수정 및 전체 회귀 검증 완료. 기존 운영 예약은 자동 변경하지 않았다.

### 수정 전 재현

- 도메인 13건 중 판매 불가 상태의 신규 예약 거절 7건이 실패했다. 주의·이상·병해충·종료·폐기·판매 완료·생성 취소 상태 모두 양수 가용 수량만 있으면 예약했다.
- PostgreSQL 조회 7건·HTTP 생성 7건도 모두 실패했다. 판매 불가 상태가 선택 목록에 포함되고, 해당 ID를 직접 지정한 생성 요청이 `201`로 전표와 예약을 확정했다. 조회·writer와 이미 정책을 적용하는 판매 가능 수량 집계가 서로 다른 재고를 대상으로 삼았다.

### 구현과 정책 구분

- `OrchidGroup.reserve`는 신규 예약 수량을 변경하기 전에 `OrchidGroupStatusPolicy.isSaleable`을 검증한다. 실제 쓰기는 기존 Mutation Engine의 난 묶음 ID 순 잠금 안에서 검증한다. Sales에서 별도의 상태 문자열 목록이나 가용 수량 보정 규칙을 추가하지 않았다.
- 판매 불가 목록은 정책의 경고·비활성 목록을 합쳐 정의한다. 검색·집계는 같은 정책 목록을 JPQL 조건으로 받고, 상태 필터도 판매 제한과 함께 적용한다. Farm application의 기존 상태 DTO와 Sales 선택 schema를 유지한다.
- Work의 농장·동·다이·구역·ID 대상 조회와 잠금 후 활성 재검증, 자동 그룹 조회도 같은 정책의 비활성 목록을 받는다. Repository의 중복 상태 문자열 4곳을 제거했다. 주의·이상·병해충과 양수 실물 수량이 있는 전량 예약 묶음은 작업 대상에 계속 포함한다. 판매 가능 수량과 실제 작업 투입 가능 수량은 서로 다른 판단이다.
- 수정의 해제·재예약 중 신규 예약 단계도 현재 상태를 검증한다. 판매 불가 묶음에 그대로 재예약하는 수정은 전체 rollback한다. 기존 예약을 해제하고 다른 판매 가능 묶음으로 배분을 옮기는 수정은 허용한다. 같은 묶음의 상태를 정상으로 바꾼 뒤 정상 재시도하는 흐름도 검증했다.
- 기존 예약의 해제·출고·출고 취소 복구는 기존 예약 수지로 처리한다. 신규 예약 금지 때문에 이미 확정된 예약을 자동 해제하지 않는다. 이미 적용한 예약 Mutation은 상태 변경 후에도 같은 key·내용의 replay로 반환하고 새 side effect를 남기지 않는다.
- `availableActions`의 수정은 다른 묶음으로 재배분할 수 있다는 의미로 유지한다. 수정 payload의 현재 배분이 모두 유효하다는 보장은 아니며 쓰기에서 새 예약 대상을 잠그고 검증한다. 조회 이후 상태 변경도 이 검증을 우회하지 못한다.
- DB의 현재 상태가 판매 불가여도 기존 예약은 남을 수 있으므로, 상태별 예약 수량을 항상 0으로 강제하는 CHECK는 추가하지 않았다. 이번 변경은 도메인 신규 예약 자격을 바로잡으며 기존 불변식·트랜잭션·DB 스키마를 유지한다. 운영 과거 예약의 건강 상태·업무 판단은 별도 검토 대상이다.
- 도메인 모델, 판매·작업 정책, API 도메인 규칙과 백엔드 구현 기준을 갱신했다. Controller·DTO·enum·OpenAPI schema 변경은 없어 생성물을 갱신하지 않았다.

### 회귀 방어

- [OrchidGroupReservationStatusTest](../backend/src/test/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupReservationStatusTest.java): 13건. 판매 불가 7종의 신규 예약 거절과 상태·수량 보존, 정상/기존 사용자 상태 허용, 경고 전환 후 기존 예약 해제·출고·복구를 검증한다.
- [SalesSaleabilityPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesSaleabilityPostgresE2ETest.java): 26건. 판매 불가 7종의 선택·직접 생성 제한, 집계 및 Work/자동 그룹 membership 구분, 기존 정상 수정 후 실패 시 14개 테이블·상세·스냅샷·감사 rollback, 건강한 묶음으로 배분 이동/상태 정상화 후 재시도, 경고 전환 후 출고 snapshot과 취소 수지, 상태 변경 후 예약 Mutation replay, 전량 예약 및 사용자 상태 호환을 검증한다.
- 경쟁 2건은 Sales 생성과 Farm Engine 상태 변경의 PostgreSQL backend PID·`pg_blocking_pids`로 실제 대기를 확인한다. 상태 변경이 먼저 잠그면 기다린 신규 예약이 최신 상태로 거절되고 side effect는 0이다. 예약이 먼저 잠그면 확정 예약을 보존한 채 이후 상태를 변경한다. 상태 writer 시험의 상세 값은 먼저 잠근 행에서 구성한다. 이는 Engine 경계의 경쟁 검증이며 다른 최상위 서비스의 잠금 전 Entity 로딩까지 해결했다고 판정하지 않는다.
- [CoreQueryRegressionTest](../backend/src/test/java/com/greenhouse/backend/CoreQueryRegressionTest.java): 정상·불가 묶음 각각 1·10·50개에서 SQL 1회로 정상 선택만 반환하는 3건을 추가했다. lazy mapper 또는 반복 정책 조회를 새로 만들지 않는다.

### 검증

- 집중 검증: 도메인 예약·불변식, 자동 그룹, 기존 Sales Mutation 및 query-count 성공. 새 query-count 3건과 PostgreSQL 26건 모두 성공.
- 백엔드 전체 `./gradlew test`: 115개 클래스, 555건 성공. 새 도메인 13건·query-count 3건과 기존 architecture·정합성 회귀 포함.
- PostgreSQL 전체 `./gradlew workE2eTest`: 39개 클래스, 239건 성공. 새 상태 정책 26건과 BE-001~BE-003을 포함한 기존 213건 모두 성공.
- 프론트엔드 `npm run check`, `./gradlew spotlessCheck`, `git diff --check`: 성공.
- 최종 전체 검증 뒤에는 이 진행 문서의 결과만 갱신했다. 실행 코드와 테스트는 바꾸지 않았다.

## 5차 변경 — BE-005 경매 결과·반환 요청 멱등 처리

작업일: 2026-10-04. 상태: 코드·API 계약·정책 문서 수정 및 전체 회귀 검증 완료. 기존 경매 자료의 중복 여부는 조사하지 않았다.

### 수정 전 재현

- PostgreSQL에서 자동 차수의 부분 낙찰 10개를 같은 요청 키·내용으로 두 번 전송했다. 기존 구현은 두 번째를 새 차수로 처리해 판매 수량 20개·시도 2건을 만들었다.
- 유찰 상태의 부분 반환 10개를 같은 요청으로 두 번 전송했다. 기존 구현은 반환 수량 20개로 누적했다. 최초 반환 fixture의 유찰 입력에는 validation에 걸리는 0개 결과 행이 있었으며, 정상 유찰 입력으로 보완한 뒤 반환 중복 반영을 별도로 재현했다.
- lot row lock은 두 쓰기를 직렬화할 뿐 재전송을 구분하지 않는다. 경매일·명시적 차수 UNIQUE도 자동 차수와 반환에는 요청 identity가 될 수 없었다.

### 구현과 계약

- 결과 입력 application 명령과 반환 확인 요청에 필수 `idempotencyKey`를 추가했다. 구형 HTTP 요청은 400으로 거절한다. application 직접 호출에도 키의 누락·공백·길이를 검증하며 호환 생성자로 새 키를 자동 생성하는 우회 경로는 추가하지 않았다.
- `AuctionTrackingService`가 기존 lot root 잠금 후 `(lot, RESULT/RETURN, key)` receipt를 먼저 확인한다. 같은 바인딩 입력은 최초 응답을 반환하고 다른 입력은 `409 / AUCTION_REQUEST_KEY_CONFLICT`로 거절한다. JSON 객체 속성은 정렬하며 결과 행 순서와 입력 문자열·선택 필드의 null을 보존한다. 요청 키는 lot·업무별 범위이며 서로 다른 lot나 결과/반환 간에는 같은 값도 독립적이다.
- replay 검증은 현재 수량·상태 검증보다 먼저 수행한다. 자동 차수와 반환 수량 null은 최초 실행에서만 현재 lot를 기준으로 해석한다. 전량 낙찰·반환 완료 후의 재전송과 후속 경매/반환 뒤의 과거 재전송도 안전하다. 동일 경매일·명시적 차수에 다른 새 키로 결과를 추가하는 기존 금지 규칙은 유지한다.
- 최초 변경은 lot·시도·결과·상태 이력을 flush해 실제 ID를 확정한 뒤 응답 snapshot을 저장한다. 최초 응답과 receipt가 같은 ID·시점을 가진다. 이 flush는 commit이 아니며 receipt 저장 실패에도 전부 rollback한다. receipt 이외의 중간 접수 계층이나 범용 Engine, 다른 모듈의 Repository 의존은 추가하지 않고 Auction 안에서 처리한다.
- V36은 receipt의 UNIQUE, lot FK와 삭제 제한, 업무 종류·공백 키·지문·응답 대상 CHECK 및 NOT NULL을 추가한다. 조회는 UNIQUE의 lot 선두 인덱스를 사용한다. 실패한 요청은 receipt를 남기지 않아 정상 내용으로 같은 키를 다시 사용할 수 있다. 기존 결과·반환은 요청 identity가 없어 receipt를 추정 backfill하지 않는다.
- 판매 hook이 같은 lot·업무의 미확정 키를 유지한다. 탭 세션 저장소로 화면 재진입·같은 탭 새로고침에도 유지하며, 저장소가 차단된 경우에는 현재 화면 메모리에서 유지한다. 성공 응답을 받으면 그 키만 완료 처리한다. 뒤늦은 병렬 응답이 다음 업무의 새 키를 지우지 않으며 query invalidation 실패와 키의 성공 확인을 분리한다. 실제 새 업무는 성공 확인 뒤 새 키를 쓴다. 생성 TypeScript schema에서 키 계약을 가져온다.
- API 생성 스크립트와 `npm run api:types`로 전체 OpenAPI·Auction slice·TypeScript를 갱신했다. 판매 기능·도메인 규칙·트랜잭션 기준·API 재전송 사용법과 구버전 writer 동시 실행 금지/배포 순서를 문서에 반영했다.

### 회귀 방어와 한계

- [AuctionCommandIdempotencyPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/AuctionCommandIdempotencyPostgresE2ETest.java): PostgreSQL 40건. 실제 판매 예약·출하로 생성한 lot에서 부분 결과/반환 재전송, 4종 결과 상태와 전량·생략 반환, 후속 업무 뒤 과거 응답 replay, 정산 행·금액, 내용 14종 충돌, 키 validation, 실패 후 키 재사용, 객체 순서/결과 행 순서, 명시적 차수, 키 범위, receipt 저장 실패와 관련 17개 테이블 rollback을 검증한다. 충돌·retry에서는 행 값·version·시각·이력·감사·Mutation·정산을 DB JSON 값으로 비교한다.
- 경쟁 5건은 PostgreSQL backend PID·`pg_blocking_pids`로 두 번째 요청의 실제 대기를 확인한다. 결과/반환의 동일 키·동일 입력은 winner를 replay하고, 서로 다른 입력은 winner commit 후 충돌한다. 선행 요청이 rollback하면 기다린 동일 요청이 한 번 적용된다.
- [AuctionCommandReceiptMigrationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/AuctionCommandReceiptMigrationPostgresE2ETest.java): legacy 부분 낙찰·부분 반환을 V35에 넣고 V36으로 갱신한다. 기존 5개 업무 테이블 불변·빈 receipt·재실행 0건·checksum, SQL 우회 시 UNIQUE/FK/CHECK/NOT NULL·lot 삭제 제한을 검증한다.
- [auction-request-keys.test.mjs](../frontend/test/auction-request-keys.test.mjs): 6건. 미확정 재시도·성공 후 실제 추가 업무·대상과 업무 변경·늦은 병렬 응답·탭 재기동·저장소 차단을 검증한다. 실제 브라우저에서 응답 유실을 주입하는 E2E는 실행하지 않았다.
- 최초 응답 snapshot은 당시 경매장 이름과 전체 lot 이력을 포함한다. 현재 상태·이름을 확인하려면 상세 조회를 사용한다. receipt의 지문·영속 응답 schema를 변경할 때는 호환을 별도 검토해야 하며, 이력이 늘면 snapshot 저장량도 증가한다. receipt의 임의 TTL 삭제는 중복 방어를 무효화한다.
- 새 탭·새 세션에서 새 키로 보낸 같은 내용은 실제 다음 업무와 구분할 수 없다. 응답 유실 후 새 키로 입력하기 전에는 상세와 이전 처리 결과를 확인해야 한다. 이번 변경은 기존 수량 보정의 actor·감사 공백, 반환에 따른 별도 재고 복구 정책, 정산 snapshot 변경 가능성 또는 모듈 전체 잠금 순서를 해결하지 않는다.

### 검증

- 집중 검증: 기존 Auction·Clock integration 10건, 신규 PostgreSQL 41건과 프론트 키 회귀 6건 성공.
- 백엔드 전체 `./gradlew test`: 115개 클래스, 555건 성공. 기존 architecture·query-count·도메인·integration 회귀 포함.
- PostgreSQL 전체 `./gradlew workE2eTest`: 41개 클래스, 280건 성공. 신규 41건과 BE-001~BE-004를 포함한 기존 239건 모두 성공.
- 프론트엔드 `npm run check`: 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 전체 검증 뒤에는 이 진행 문서의 상태·결과만 갱신했다. 실행 코드·API 생성물·테스트는 바꾸지 않았다.

## 6차 변경 — BE-006 판매 배분 교차 수정 잠금 순서

작업일: 2026-10-04. 상태: 판매에서 재현된 교착 수정·정책 문서 갱신 및 전체 검증 완료. BE-006의 Farm·Work 배치 범위는 남긴다.

### 수정 전 재현

- 서로 다른 거래처의 작성중 전표가 각각 G1 10개·G2 20개를 예약한 뒤, G1→G2 / G2→G1로 동시에 배분을 바꾸는 PostgreSQL 회귀를 기존 구현에 먼저 실행했다.
- 두 선행 순서 모두 `orchid_groups` tuple lock의 `deadlock detected`로 실패했다. 선행 요청의 기존 예약 해제 직후를 barrier로 고정하고 후행 PostgreSQL backend PID가 선행 PID를 기다리는 것을 확인한 뒤 진행했다. DB가 교착 희생 요청을 rollback했으며 단순 timeout을 교착의 근거로 사용하지 않았다.
- 기존/신규 대상을 따로 잠그는 두 단계가 원인이었다. 각 Mutation·조회가 ID를 정렬해도 이미 보유한 기존 묶음 잠금보다 작은 신규 ID를 나중에 취득할 수 있었다. 거래처가 서로 달라 partner 잠금으로도 직렬화되지 않았다.

### 구현과 스냅샷

- [SalesSlipUpdateService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java)는 전표와 이전·신규 거래처를 잠근 뒤, 기존 예약 해제 전에 allocation 합집합을 잠근다. [SalesSlipAllocationFactory](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationFactory.java)가 기존 배분·새 요청의 ID를 수집한다. 기존 배분 합계 validation을 같은 정의로 사용하고, 다른 모듈의 Entity·Repository를 직접 사용하지 않는다.
- Farm application의 [OrchidGroupReader.lockGroups](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java)는 전체 ID를 중복 제거·정렬한 뒤 500개씩 기존 root 잠금 쿼리로 취득한다. `MANDATORY`로 최상위 수정 트랜잭션을 요구하며 별도 짧은 트랜잭션을 열지 않는다. DTO·연관 상세를 읽어 사용하지 않는 snapshot을 만들지 않는다.
- 선잠금은 수량 변경이 아니다. 기존 해제→새 품목·배분 조립→새 예약 순서를 유지하고, 새 allocation의 `CREATION` snapshot은 해제 후의 최신 실물·예약 값을 저장한다. 선잠금 시점의 값으로 저장하면 자기 기존 예약까지 스냅샷에 남으므로 사전 조회값을 재사용하지 않는다. 두 거래처의 교차 수정, 다른 전표 취소, 구조 변경의 선후에 따른 snapshot을 검증했다.
- 기존 수정 UUID, Mutation과 이동 이력의 원자성, 거래처 미수·감사·출고 정책은 유지한다. 선잠금 중 대상 누락 또는 새 예약 실패 시 최상위 트랜잭션이 전체 변경을 rollback한다. DB schema·Controller·DTO·enum 변경은 없어 Flyway/OpenAPI/생성 TypeScript를 변경하지 않았다.
- 수정 1회에 합집합의 500개 단위 잠금 조회가 추가된다. 전체를 한 번 정렬한 뒤 분할하므로 청크 사이에도 순서를 유지한다. 기존 새 배분의 snapshot 조회와 Mutation 대상 조회는 해당 단계에서 수행한다. 추가 잠금은 같은 트랜잭션의 재진입이며 ID별 추가 DTO 조회를 만들지 않는다.

### 명시적 root 잠금 순서와 남은 범위

아래는 이번 코드에서 확인한 업무 root 순서다. 잔액 요약·번호·FK·writer fence 등 내부 잠금의 전수 목록이나 모든 경로의 무교착 증명으로 사용하지 않는다.

| 경로 | 확인한 순서 | 검증/범위 |
| --- | --- | --- |
| DIRECT 생성 | 거래처 → 전표 번호 → allocation 묶음 ID 순 | 기존 전체 회귀 유지 |
| DIRECT 수정 | 전표 → 이전·신규 거래처 ID 순 → 기존·신규 allocation 묶음 합집합 ID 순 | 이번 교차 수정·실패/대기 회귀 |
| DIRECT 작성중 취소 | 전표 → 거래처 → 현재 allocation 묶음 ID 순 | 수정과 취소 양방향 경쟁 |
| DIRECT 출고 완료 | 전표 → 현재 allocation 묶음 ID 순 | 기존 출고 snapshot·수량 차감 회귀 |
| 출고/출하 취소 | 전표 → DIRECT는 거래처 → allocation 묶음 ID 순 → 복구 구역 ID 순 → AUCTION은 lot ID 순 | 기존 배치 충돌·경매 이력 보존 회귀 유지 |
| Engine 구조 변경 | 원본 묶음 ID 순 → 결과 구역 ID 순 | 이번 Sales와 Engine의 양방향 경쟁. Work 전체 접수/실행 순서까지 증명한 것은 아님 |

- `OrchidGroupCommandService.updateBatch`는 아직 입력별로 상세 조회·묶음·구역 잠금을 누적한다. `StructureChangeRecordService.createStructureChangeRecords`도 전체 원본 ID를 배치 제외 집합으로 모으지만, 기록 실행은 입력 순서별이다. 각 하위 실행의 정렬이 전체 유스케이스 순서를 보장하지 않는다.
- 이 두 배치의 실제 교착과 서로 다른 원본 집합이 구역만 공유하는 경쟁은 이번에 재현하지 않았다. 배치 전체의 원본·관련 구역 합집합, 기존 Work/receipt 순서, 잠금 전 Entity 조회와 최신 상태 검증을 함께 검토하는 후속 변경이 필요하다. 이번 Sales 수정으로 BE-006 전체를 완료 처리하지 않는다.

### 회귀 방어

- [SalesAllocationLockOrderPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAllocationLockOrderPostgresE2ETest.java): 12건. 거래처 교차 수정 2건·다른 전표 취소 2건·같은 전표 수정/취소 2건·Engine 구조 변경 2건·선행 실패 후 대기 수정 1건·겹친/중복 배분 1건·대상 누락 rollback 1건·최상위 트랜잭션 요구 1건이다.
- 경쟁 9건은 선행 잠금을 barrier로 고정하고 `pg_blocking_pids`로 실제 대기를 확인한다. 대기 후 최신 상태, snapshot·수량·예약·이동/Mutation 개수, 원장 reconciliation을 검증한다. 취소가 먼저 확정되면 기다린 동일 전표 수정이 거절되고 관련 13개 테이블 snapshot은 그대로다. 새 예약 실패 후에도 기다린 다른 수정이 정상 진행하며 실패 전표·품목·스냅샷은 보존된다.
- Engine 경쟁은 실제 PostgreSQL에서 두 원본을 분주하고 변경 후 수량 80개 또는 수정 선행 시 100개가 판매 snapshot에 남는지 검증한다. 원장·실물·예약 수지는 검증하지만 이 fixture는 Engine application을 직접 호출하므로 Work 기록/효과의 추가 atomicity 검증으로 간주하지 않는다.
- [OrchidGroupReaderLockTest](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReaderLockTest.java): 3건. 역순·중복 1,001개 ID의 전체 정렬과 500/500/1 분할, 빈 목록의 무조회, 첫 청크 누락 시 이후 잠금 중단을 검증한다. 상태·연관 DTO 조회가 추가되지 않는 것도 확인한다.

### 검증

- 집중 검증: Reader 잠금 3건, 기존 Sales Mutation 계약 2건·금액 규칙 14건·query-count 19건 성공. 확대한 PostgreSQL 12건 중 11건 성공 후, 취소 선행 시험의 기존 오류 문구에 대한 잘못된 기대를 제거하고 해당 양방향 2건을 다시 실행해 성공했다. 검증은 메시지 문자열 대신 거절과 취소 상태·전체 snapshot 보존을 사용한다.
- 백엔드 전체 `./gradlew test`: 116개 클래스, 558건 성공. 기존 architecture·query-count·도메인·integration 회귀와 신규 Reader 잠금 3건을 포함한다.
- PostgreSQL 전체 `./gradlew workE2eTest`: 42개 클래스, 292건 성공. 신규 경쟁·rollback 12건과 기존 280건을 포함하며 실패·오류·생략은 없다.
- 프론트엔드 `npm run check`: 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 전체 검증 뒤에는 진행 문서의 상태·결과·표현만 갱신했다. 실행 코드·테스트는 바꾸지 않았다.

## 7차 변경 — BE-006 Farm 단건·일괄 상세 수정 잠금 순서

작업일: 2026-10-04. 상태: Farm 교착 수정·정책 문서 갱신 및 최종 전체 검증 완료. Work 다품종 구조 기록과 다른 경로 간 전역 잠금 순서는 후속 범위다.

### 수정 전 재현

- 기존 구현에 PostgreSQL 회귀 4건을 먼저 실행했다. 같은 두 묶음을 반대 입력 순서로 수정하는 두 선행 순서에서 `orchid_groups` tuple lock 교착을 재현했다. 서로 다른 원본 집합이 같은 두 구역을 반대 순서로 사용하는 두 선행 순서에서도 `bed_zones` tuple lock 교착을 재현했다.
- 선행 요청의 첫 `UPDATE_DETAILS` 직후 flush와 barrier를 사용하고, 후행 backend PID의 `pg_blocking_pids` 대기를 확인한 뒤 선행 요청을 진행했다. 네 경우 모두 실제 `deadlock detected`와 두 PID의 순환 대기를 확인했다. 구역 경쟁 fixture는 서로 다른 하우스의 구역을 사용하며 timeout을 교착 근거로 사용하지 않는다. 실제 SQL의 잠금 대상은 `FOR NO KEY UPDATE OF`의 묶음 또는 구역 root다.
- 기존 `updateBatch`는 입력마다 묶음 → 구역을 잠그고 다음 입력으로 넘어갔다. 전체 묶음 집합을 정렬하는 것만으로는 원본 집합이 다른 구역 경쟁을 해결하지 못한다. 단건·일괄 모두 잠금 전 `findById`의 값을 무변경 판단과 감사의 변경 전 값에 사용했다.

### 구현과 유지한 계약

- [OrchidGroupCommandService](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java)의 단건·일괄 수정은 대상 묶음 전체 ID 순 → 잠금 뒤 확인한 현재 구역 전체 ID 순 → 연관 상세 일괄 로딩 순서다. 묶음·구역 집합은 각각 전체 중복 제거·정렬 후 500개씩 취득한다. 구역 ID는 잠긴 Entity에서 얻으므로 이동 대기 후 구역도 최신 값이다.
- 최상위 application 트랜잭션을 유지한다. 사전 잠금은 상태 변경이나 Mutation 접수가 아니며 다른 짧은 트랜잭션을 열지 않는다. 수정·수량 불변식·상태 전이는 기존 Engine과 Entity가 소유한다. 범용 잠금 coordinator나 새 모듈 간 Entity 계약은 추가하지 않았다.
- 감사·응답의 품종·입고·위치 연관은 모든 잠금 취득 뒤 기존 `findDetailsByIds`로 500개씩 읽는다. 기존 항목별 `findById` 두 호출을 제거하고 동일 persistence context의 잠긴 Entity를 Engine 실행 전후에 사용한다. 감사와 무변경 판단은 선행 요청 commit 또는 rollback 후 상태를 기준으로 한다.
- 실제 적용과 응답은 기존 입력 순서를 유지한다. 같은 묶음이 중복된 기존 요청도 순서대로 적용하며 각 중간 상태의 snapshot·Mutation·감사를 보존한다. 위치 교환을 최종 상태만으로 허용하는 새 배치 정책은 추가하지 않았다. 후행 항목 실패는 앞선 수정·Mutation·감사까지 rollback한다.
- 무변경 요청도 대상·구역을 잠그므로 잠금 범위와 보유 시간이 늘어난다. 500개 이내 무변경 배치의 조회 3회는 일괄 잠금·연관 로딩 비용이다. 실제 변경은 기존 항목별 Mutation·배치 충돌 검증·감사 쿼리를 계속 실행하므로 전체 쓰기의 query count가 데이터 건수와 무관하다고 주장하지 않는다.
- Controller·요청/응답 DTO·DB schema는 변경하지 않았다. Flyway·OpenAPI·생성 TypeScript 갱신은 없다. 입력 값은 기존처럼 명시적인 보정 값이며, 화면 revision에 대한 새 충돌 계약이나 요청 키 기반 멱등 처리는 추가하지 않는다.

### 회귀 방어와 한계

- [FarmUpdateLockOrderPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/FarmUpdateLockOrderPostgresE2ETest.java): 15건. 역순 묶음 2건·서로 다른 묶음의 역순 구역 2건·외관상 무변경 요청 대기 1건·단건/일괄 양방향 2건·후행 항목 실패 1건·선행 실패 후 대기 배치 1건·이동 후 최신 구역/감사 1건·누락 대상 1건·중복 대상 순서 1건·무변경 이력 보존 1건·조회 수 회귀 2건이다.
- 경쟁 9건은 실제 PID 대기를 확인한다. 후행 배치가 대기 전에 일부 Mutation을 실행하지 않는지, 최종 수량·revision·응답 순서·감사 전후 값과 모드, 원장 reconciliation을 검증한다. 선행 실패 후에는 원래 상태부터 후행 요청만 반영하며, 일반 실패와 누락 대상은 관련 5개 테이블의 JSON 값을 비교한다.
- 이동 경쟁은 Engine의 실제 `MOVE`와 Farm 수정의 DB 경계를 검증한다. Work 작업 접수·효과·취소의 추가 atomicity 시험으로 간주하지 않는다. 경쟁 쿼리의 timeout은 시험 실패 상한이며 성공 근거는 실제 대기와 최종 상태다.
- 조회 회귀는 품종이 각각 다른 1개/4개 묶음의 무변경 배치에서 연관 응답을 조립해도 3회만 조회하고 저장 값·이력이 불변인지 확인한다. 한 persistence context에서 같은 품종을 반복해 조회하는 fixture로 N+1을 숨기지 않는다.
- [OrchidGroupCommandLockTest](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandLockTest.java): 1건. 역순·중복 1,002개 입력, 1,001개 묶음과 역방향으로 매핑된 1,001개 구역에서 전체 집합 정렬·500/500/1 분할·모든 묶음 후 모든 구역 잠금을 검증한다. 마지막 구역 누락 시 상세 조회·Mutation·감사에 진입하지 않는다.
- Work의 `StructureChangeRecordService.createStructureChangeRecords`는 여전히 기록별로 원본·구역 잠금을 누적한다. Work 배치끼리, Farm 수정과 Work 배치 사이, 다른 원본을 가진 Work 배치의 공유 목적 구역 경쟁은 후속 재현·수정 대상이다. 단건 Engine의 대상별 정렬이나 이번 Farm 선잠금만으로 이 범위를 완료 처리하지 않는다. 호출자가 이미 로딩한 Entity의 일반적인 persistence context 갱신 정책도 이번 수정의 범위가 아니다.

### 검증

- 초기 집중 PostgreSQL 11건 중 10건 성공. 무변경 fixture가 구형 화분 표기를 사용해 Engine의 변경 없음 validation에 걸린 1건은 원장 baseline 전 실제 Entity 표준 표기로 맞췄다. 운영 정책·코드를 완화하지 않았다. 이후 확장한 경쟁·rollback 13건과 기존 H2 일괄 수정·Engine 라우팅 회귀가 성공했다.
- 추가 조회 fixture의 중복 품종명으로 실패한 2건은 `(genus, name)` UNIQUE에 맞게 각 이름을 구분한 뒤 다시 실행해 성공했다. 집중 조회 회귀 2건과 1,001개 분할 잠금 시험 1건 성공. 운영 코드·DB 제약을 완화하지 않았다.
- 백엔드 전체 `./gradlew test`: 117개 클래스, 559건 성공. 신규 분할 잠금 1건과 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- PostgreSQL 전체 `./gradlew workE2eTest`: 43개 클래스, 307건 성공. 신규 Farm 15건과 기존 292건을 포함하며 실패·오류·생략은 없다.
- 프론트엔드 `npm run check`: 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 뒤에는 진행 문서의 상태·결과만 갱신했다. 실행 코드·테스트는 바꾸지 않았다.

## 8차 변경 — BE-006 Work 구조 변경 기록 배치 선잠금

작업일: 2026-10-04. 상태: Work·Farm 경쟁·멱등 처리 수정과 정책 문서 갱신 및 최종 전체 검증 완료. 일반 품종별 계획·폐기 기록의 누적 잠금 경로는 후속 검증 후보다.

### 수정 전 재현

- 기존 코드에 PostgreSQL 경쟁 6건을 먼저 실행했다. Work 배치의 반대 원본 순서 2건, 서로 다른 원본 집합이 같은 두 구역을 반대 순서로 사용하는 2건, Work 선행/Farm 일괄 수정 후행 1건에서 실제 `deadlock detected`를 확인했다. 묶음 또는 구역 tuple lock의 두 backend PID 순환 대기였다.
- Farm 수정 선행/Work 배치 후행 1건은 교착이 아니라 `WORK_TARGET_CHANGED`로 실패했다. Work가 계획 대상 Entity를 잠금 전에 로딩해 보유한 version과 수정 이후 상태가 충돌했다. 이 결과를 데이터 손상이나 lost update의 증거로 사용하지 않는다.
- 첫 `MOVE` 또는 Farm 상세 수정의 실제 Engine 실행·flush 직후를 barrier로 고정하고 `pg_blocking_pids`로 후행 요청의 대기를 확인했다. timeout이 아닌 실제 PG 교착과 상태 충돌을 근거로 삼았다.

### 구현과 원인 차단

- [StructureChangeRecordService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java)는 Receipt 검증 후 최초 실행 callback 안에서만 전체 원본·결과 구역 ID를 수집해 [StructureChangeRecordLockPort](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordLockPort.java)를 호출한다. 배치와 호환 단건 기록이 같은 경로를 사용한다. replay에는 이 잠금을 실행하지 않는다.
- Farm 소유 [FarmStructureChangeRecordLockAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmStructureChangeRecordLockAdapter.java)는 전체 원본 묶음 ID 순 → 잠금 뒤 확인한 현재 구역·요청 결과 구역 합집합 ID 순으로 잠근다. 각 집합을 전체 중복 제거·정렬 후 500개씩 취득하며 `MANDATORY`로 호출자의 트랜잭션 종료까지 유지한다. Work는 Farm Entity·Repository를 사용하지 않고 ID만 전달한다. adapter는 조회 DTO나 사전 snapshot을 만들지 않는다.
- 명시적 root 순서는 Receipt → 전체 원본 → 현재·결과 구역 → 신규 Work·실행이다. 이 Work root들은 해당 요청에서 처음 생성하는 미확정 ID다. 기존 계획의 실행·취소는 기존 Inbound/Work/execution root 선잠금 경로를 사용한다. 모든 writer·FK·내부 fence 잠금의 무교착 증명으로 확대하지 않는다.
- [WorkOperationPlanService.createStructureRecordPlan](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java)은 사전 잠금 후 대상과 수량을 해석한다. 기존의 `계획 대상 ID·전체 수량 = 실행 입력` validation을 aggregate 생성 앞으로 옮겼다. 이전 정의는 제거했다. 이 검증을 뒤에 두면 HOUSE/FARM 등 범위 요청이 실행 원본 밖의 묶음을 구역 잠금 후 추가로 잠글 수 있으므로, 사전 잠금만 추가하는 것으로는 충분하지 않았다.
- 기록·응답·Receipt 지문의 배열 순서를 유지하고, 남은 원본의 배치 제외 집합과 같은 배치의 해제 위치 재사용도 기존 순서로 처리한다. Engine·Work 효과·계보·감사·Receipt가 최상위 트랜잭션에서 확정되거나 rollback한다. 같은 키의 대기 요청은 선행 성공 후 replay하며, 선행 실패 후에는 key claim부터 정상 입력을 적용한다.
- 입력 원본 누락은 기존 `400 / VALIDATION_ERROR`, 목적 구역 누락은 기존 `404 / NOT_FOUND`를 유지한다. Controller·DTO·enum·DB schema·fingerprint 형식은 변경하지 않아 Flyway·OpenAPI·생성 TypeScript 갱신은 없다. 서로 다른 배치의 기록별 실행 키 목록은 기존처럼 별도 identity이며 의미 있는 입력 순서를 정렬하지 않는다.
- 사전 잠금 쿼리와 잠금 보유 시간이 늘어난다. 기존 기록별 계획·응답·효과·Mutation 조회는 계속 실행하므로 전체 쓰기의 query count 고정이나 벤치마크 개선으로 주장하지 않는다. 범용 lock manager·자동 재시도·별도 메시징은 도입하지 않았다.

### 회귀 방어와 남은 범위

- [WorkRecordBatchLockOrderPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkRecordBatchLockOrderPostgresE2ETest.java): 18건. 역순 원본 2건·서로 다른 원본의 역순 구역 2건·Farm/Work 양방향 2건·병렬 동일 키 1건·키 충돌 1건·후행 실패와 같은 키 재시도 1건·선행 실패 후 다른 키 대기 배치 1건·원본 종료 후 replay 1건·선행 실패 후 동일 키 정상 입력 1건·대기 후 수량 불일치 1건·호환 단건/배치 1건·트랜잭션 필수 1건·계획 범위 불일치 1건·목적 구역 누락 1건·원본 누락 HTTP 회귀 1건이다.
- 경쟁 11건은 실제 PID 대기를 확인한다. 후행 요청이 대기 전에 일부 Mutation을 실행하지 않는지, 입력 순서·완료 상태·수량·revision·효과/Mutation 연결·Receipt membership과 원장 reconciliation을 검증한다. 변경 전 칸 좌표는 Work 효과에 연결된 Mutation Entry의 `before_state`를, 계획의 최신 속성은 Work target의 snapshot을 확인한다. 계획의 위치 snapshot에 없는 칸 좌표를 있다고 가정하지 않는다.
- 후행 기록 실패·충돌·대기 후 수량 불일치·누락 대상은 관련 13개 테이블의 JSON 값을 비교한다. 원본이 종료된 뒤의 replay에는 port가 호출되면 실패하는 spy를 설정해 최초 실행의 잠금이 재적용되지 않는 것도 검증한다. `MANDATORY`는 실제 Spring/PG 경계에서 확인했다.
- [FarmStructureChangeRecordLockAdapterTest](../backend/src/test/java/com/greenhouse/backend/farm/application/transformation/FarmStructureChangeRecordLockAdapterTest.java): 2건. 역순·중복 원본 1,001개와 현재·결과 구역 1,003개 합집합에서 그룹 전체 후 구역 전체 정렬·500개 분할·누락 시 중단을 확인한다. 상태·연관 DTO 조회는 추가하지 않는다.
- 이번 경쟁은 실제 Work 기록 접수·계획·실행·효과 저장과 Farm 수정 application을 끝까지 호출한다. 앞선 Engine 경계 시험과 달리 Receipt·Work·Mutation·감사의 함께 rollback하는 결과를 검증한다. API schema를 변경하거나 일반 계획·입고·포트 경로까지 새 잠금 순서로 이식한 변경은 아니다.
- 일반 품종별 계획의 `WorkOperationPlanService.createBatch`는 여전히 품종 그룹별 대상 잠금을 누적한다. `DiscardRecordService.create`도 이 계획 경로를 사용한다. 해당 경로끼리 또는 이번 기록 경로와의 경쟁은 정적 후속 후보이며 이번에 실제 교착을 재현하지 않았다. 이 후보와 모듈 전체의 잠금 전 조회 정책까지 완료 처리하지 않는다.

### 검증

- 최초 신규 PostgreSQL 13건 중 10건 성공. 3건은 계획의 위치 snapshot에 없는 좌표 필드를 조회한 시험 오류였으며, 실제 좌표 보존 경계인 Mutation Entry로 수정했다. 이후 신규 17건과 기존 Work 멱등 회귀 8건, Movement 배치 integration 및 신규 adapter 단위 2건 성공.
- 최초 백엔드 전체 561건 중 560건 성공. 원본 누락을 사전 잠금이 404로 반환해 기존 HTTP 400 계약 시험 1건이 실패했다. 운영 코드를 기존 `VALIDATION_ERROR` 계약에 맞게 수정하고 새 PostgreSQL HTTP 회귀를 추가했다. 이 실패로 최초 전체 PostgreSQL task는 실행되지 않았다.
- 오류 응답 수정 뒤 기존 HTTP 회귀 1건·adapter 단위 2건과 신규 PostgreSQL HTTP 회귀 1건 모두 성공. 기존 400 계약을 완화하거나 테스트 기대값을 바꾸지 않았다.
- 최종 백엔드 전체 `./gradlew test`: 118개 클래스, 561건 성공. 신규 adapter 단위 2건과 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 44개 클래스, 325건 성공. 신규 Work 기록 18건과 기존 307건을 포함하며 실패·오류·생략은 없다.
- 프론트엔드 `npm run check`: 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공. 이후 수정은 백엔드 오류 응답 보존·회귀 추가와 진행 문서이며 프론트·API schema는 변경하지 않았다.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 뒤에는 진행 문서의 상태·결과만 갱신했다. 실행 코드·테스트는 바꾸지 않았다.

## 9차 변경 — BE-006 일반 품종별 계획·폐기 기록의 전체 대상 잠금

작업일: 2026-10-04. 상태: 교착 수정·정책 문서 및 최종 전체 검증 완료. 앞서 남긴 일반 품종별 계획·폐기 기록 후보까지 확인해 BE-006의 예정 수정 범위를 마무리했다.

### 수정 전 재현

- [WorkPlanBatchLockOrderPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkPlanBatchLockOrderPostgresE2ETest.java)의 경쟁 6건을 기존 코드에 먼저 실행했다. 일반 배치 계획 선행/Farm 수정 후행, 폐기 기록 선행/Farm 수정 후행, 서로 겹치는 두 계획의 반대 품종 순서 양방향에서 실제 PostgreSQL `deadlock detected` 4건을 확인했다. `orchid_groups` tuple lock에서 두 backend PID가 서로의 transaction을 기다렸다.
- 조회 순서의 첫 품종과 전체 묶음 ID 순서가 다른 fixture다. 위치 범위 조회의 대상 순서·제외 집합에 따라 두 배치가 A→B 또는 B→A로 aggregate를 만들었다. 각 품종 내부 ID 정렬로는 품종 사이 누적 잠금을 정렬하지 못했다.
- 선행 요청의 첫 aggregate 생성 또는 실제 Farm Engine 수정 직후 flush와 barrier를 두고 `pg_blocking_pids`로 실제 후행 대기를 확인했다. timeout을 교착 근거로 사용하지 않는다.
- Farm 수정 선행 2건은 기존 `WORK_TARGET_CHANGED`로 거절되고 오래된 계획·효과를 저장하지 않았다. 이번에 이를 정상 적용으로 바꾸거나 자동 재시도로 숨기지 않는다. 변경 전 대상 해석과 잠금 중 version 충돌을 다루는 기존 계약이다.

### 구현과 유지한 계약

- [WorkOperationPlanService.createBatch](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java)는 제외 대상을 뺀 전체 선택을 품종별 aggregate 생성 전에 잠근다. 여러 품종으로 분리되는 경우에만 별도 전체 선잠금을 호출한다. 단일 품종·일반 작업 유형은 기존 aggregate creator가 같은 전체 집합을 잠그므로 추가 조회를 하지 않는다.
- Farm 소유 [FarmWorkTargetResolver.lockAndValidateActive](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolver.java)는 전체 ID 중복 제거·정렬 → 500개씩 모든 root 잠금 → 500개씩 활성 자격 재검증 순서다. 기존 `MANDATORY`와 `WORK_TARGET_CHANGED` 매핑을 유지한다. 새 Entity·Repository 공개 계약이나 범용 lock manager를 만들지 않는다.
- 기존 aggregate creator의 자격 검증은 다른 생성 경로에서도 사용하므로 보존한다. 배치의 후행 품종은 이미 잠긴 root 집합만 재검증하며 더 낮은 새 ID를 뒤늦게 잠그지 않는다. 작업 제목·품종·대상·응답 순서와 제외 정책은 바꾸지 않는다.
- [DiscardRecordService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/DiscardRecordService.java)는 같은 계획 경로를 사용하므로 별도 잠금 정책을 복제하지 않는다. 최상위 트랜잭션과 전체 계획·실행·효과·Mutation·감사의 함께 확정/rollback하는 경계를 유지한다. 폐기는 묶음 수량 변경이며 이 경로에 구역 잠금을 추가하지 않았다. 이동 후 연관 폐기도 이미 보유한 원본 집합을 재사용한다.
- 일반 계획은 잠금 전 대상 해석을 유지하고 대기 중 Entity version이 바뀌면 요청 전체를 거절한다. 즉시 구조 기록의 선잠금 후 대상 해석 정책과 구분한다. snapshot을 현재 값으로 덮어쓰거나 자동 재시도하는 새 계약은 추가하지 않는다.
- 다품종 배치는 추가 잠금·자격 조회와 잠금 보유 시간 비용이 생긴다. 기록별 응답·효과 조회와 상태 변경 쿼리는 계속 실행하므로 전체 쓰기 query count가 일정하다고 주장하지 않는다. 기존 Work benchmark는 목록·상세·이력의 GET 측정이며 이 쓰기 교착·잠금 비용을 검증하지 않는다. 이번에는 별도 benchmark를 실행하지 않고 실제 PG 경쟁과 전체 query regression을 검증한다.
- Controller·DTO·HTTP error code·DB schema·Receipt fingerprint는 변경하지 않았다. Flyway·OpenAPI·생성 TypeScript 갱신은 없다. 키 없는 계획·폐기 기록에 재전송 멱등성을 새로 부여하지 않는다.

### 회귀 방어와 BE-006 범위

- 신규 PostgreSQL 12건: 계획/Farm 양방향 2건·폐기/Farm 양방향 2건·반대 품종 순서의 겹치는 계획 양방향 2건·계획/구조 기록 양방향 2건·후행 폐기 실패와 정상 재시도 1건·폐기 결과 누락/중복 2건·제외 대상 잠금 미취득 1건이다.
- 경쟁 8건은 실제 PID 대기와 대기 전 후행 aggregate/Mutation 미실행을 확인한다. 정상 계획의 대상 수량·품종별 순서, 폐기 효과의 Mutation 연결과 실제 변경 전 수량, 재고·revision, 완료 상태·Receipt/membership 및 원장 reconciliation을 확인한다.
- Farm 선행 후 충돌 2건과 폐기 실패 3건은 관련 13개 테이블 JSON 값을 비교한다. Farm 승자의 변경·감사는 flush 후 비교한다. 구조 기록 승자의 commit 시 Hibernate version·timestamp 갱신이 발생하므로 이 경쟁은 저장된 작업·대상·실행·효과·Receipt 수와 재고 상태로 후행 계획 미저장을 확인한다. commit 전 임의 flush 시점의 기술 필드까지 고정하는 oracle을 쓰지 않는다.
- 제외 대상은 다른 transaction의 실제 `FOR UPDATE NOWAIT` 조회가 계획 작성 중에도 성공하는지 확인한다. 대상 전체 선잠금이 제외 대상을 다시 포함하지 않는다.
- [FarmWorkTargetResolverLockTest](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolverLockTest.java): 4건. 역순·중복 1,001개 ID의 500/500/1 분할과 전체 root 잠금 후 활성 검사, 누락 root 중단, 비활성 target 거절, optimistic 충돌의 기존 domain error 보존을 확인한다.
- 감사에서 재현한 Sales 교차 수정, 후속 확인한 Farm 묶음/구역 배치, Work 구조 기록/Farm 경쟁, 일반 품종별 계획/폐기 경쟁의 수정 범위를 마무리한다. 모든 writer·FK·내부 fence의 무교착을 증명한 것으로 확대하지 않는다. 새 writer에는 현재 문서의 경로별 잠금 순서와 실제 경쟁 회귀를 적용한다.

### 검증

- 수정 전 PostgreSQL 6건: 실제 교착 4건, 기존 대상 변경 거절 계약 2건. 전체 선잠금 수정 뒤 같은 6건 모두 성공.
- 확장 집중 검증에서 신규 PG 12건 중 4건은 fixture/oracle 오류였다. 배드 최대 칸을 넘는 이동 좌표 2건과 flush 전 DB 값 비교 2건을 고쳤다. 이후 1건의 Work commit version·timestamp 비교 오류는 해당 경계의 저장 결과 검증으로 바로잡았다. 운영 validation·충돌 error code를 완화하지 않았다.
- 신규 단위 4건과 기존 폐기 단위 1건 성공. 확장 PG의 기존 구조 기록 18건·구조 실행 4건도 성공했고, oracle 수정 후 신규 12건 모두 성공.
- 최종 백엔드 전체 `./gradlew test`: 119개 클래스, 565건 성공. 신규 resolver 단위 4건과 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 45개 클래스, 337건 성공. 신규 일반 계획·폐기 12건과 기존 325건을 포함하며 실패·오류·생략은 없다.
- 프론트엔드 `npm run check`: 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 뒤에는 진행 문서의 상태·결과만 갱신했다. 실행 코드·테스트는 바꾸지 않았다.

## 10차 변경 — BE-007 경매 수량 변경의 이력과 직접 보정 제한

작업일: 2026-10-04. 상태: 수량 이력·보정 정책·V37·화면·계약 문서 및 최종 전체 검증 완료.

### 수정 전 재현

- [AuctionQuantityHistoryPolicyTest](../backend/src/test/java/com/greenhouse/backend/auction/domain/AuctionQuantityHistoryPolicyTest.java)의 신규 4건을 기존 코드에 먼저 실행했고 모두 실패했다. 두 번째 부분 반환과 같은 상태의 수량 보정에서 이력이 추가되지 않았고, 경매 결과 또는 반환 확인 뒤 직접 보정으로 기존 수량을 덮어쓸 수 있었다.
- 정산 금액 자체가 잘못 갱신됐다는 근거로 확대하지 않는다. 정산은 결과 행을 snapshot으로 보존하지만 lot 현재 수량이 그 사실과 달라지는 원인·보정 계약이 없었다.

### 구현과 정책

- [AuctionShipmentLot](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipmentLot.java)는 결과 반영·반환 확인·직접 수량 보정 전에 판매·대기·반환 수량을 포착하고, 상태 또는 수량이 달라지면 기존 [AuctionLotStatusHistory](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionLotStatusHistory.java)에 6개 전후값을 함께 저장한다. 상태 전이와 수량 변경의 판단은 한 내부 단계로 모았다. 같은 상태의 부분 반환·부분 낙찰·수량 보정을 모두 남기며 같은 상태·같은 수량은 새 history를 만들지 않는다.
- 직접 보정은 경매 시도가 없고 반환 확인일도 없는 lot에만 허용한다. 유찰·반환 추정도 시도이며 상태 수동 보정으로 제한을 우회할 수 없다. 실제 변경은 `409 / AUCTION_QUANTITY_ADJUSTMENT_LOCKED`다. 이미 저장된 값 그대로인 요청은 변경 없이 반환한다. null·음수·합계 overflow도 변경 전에 거절한다. 합계는 long으로 비교해 int overflow로 출하 수량과 같아지는 입력을 허용하지 않는다.
- 결과·정산·입금 및 확인된 반환을 직접 수정으로 덮어쓰지 않는 정책이다. 결과/반환 이후의 보상·정정 이벤트는 후속 기능 범위이며 이번에 구현하지 않는다. 정산 모듈의 Entity·Repository를 Auction에서 조회하거나 lot→partner/정산의 역방향 잠금을 추가하지 않았다. 실제 정산은 결과 행에서만 생성되므로 경매 시도 존재가 정산보다 앞선 변경 금지 경계다.
- 쓰기는 기존 lot root 잠금·최상위 application transaction을 유지한다. 이력 생성 실패는 lot 수량·시도·결과·반환·Receipt와 함께 rollback한다. 직접 보정·상태 변경 응답도 flush 후 history ID를 반환한다. 기존 결과·반환 Receipt key/fingerprint와 replay-before-validation 순서는 유지한다.
- 목록·상세는 `quantityAdjustmentAllowed`를 제공하며 화면은 그 값으로 보정 버튼을 제어한다. 목록 mapper는 이미 일괄 조회한 시도의 존재를 같은 lot 정책에 전달해 lazy collection 쿼리를 추가하지 않는다. UI는 이력에 보존된 수량 전후값을 표시하고 과거 null 값은 추정하지 않는다. 서버 상태는 기존 cache에 두고 dialog의 열림만 local UI state로 유지한다.
- 작업자는 반환·보정·상태 요청에서 기존 `RequestActorProvider`를 사용한다. 결과 입력에는 작업자 필드가 없어 해당 이력은 기존처럼 null이다. 새 인증/actor 정책이나 AuditEvent 이중 저장은 도입하지 않는다. 고정 사유와 입력 메모·처리 시점은 같은 history에 보존한다.

### V37과 영속 계약 호환

- [V37](../backend/src/main/resources/db/migration/V37__auction_history_quantity_snapshots.sql)은 history에 nullable 정수 6개를 추가한다. 기존 이력·현재 lot·결과·정산·입금·Receipt를 backfill 또는 재계산하지 않는다. 과거 수량 시점을 현재 lot에서 복원할 근거가 없기 때문이다.
- CHECK는 전부 null인 과거 행 또는 6개 모두 존재하는 비음수 snapshot을 허용한다. SQL CHECK의 null 통과를 피하도록 `num_nonnulls`로 완전성을 검사한다. snapshot은 수량 불일치 사실도 기록하므로 전후 합계를 DB CHECK로 같게 강제하지 않는다. 직접 보정의 출하 수량 수지는 domain이 검증한다. 신규 application이 완전한 snapshot을 만들지만 nullable legacy 행을 DB에서 완전히 금지한 것은 아니다.
- HTTP 응답에 nullable 수량 snapshot·보정 capability를 추가했다. Controller/DTO·테스트를 기준으로 `python3 scripts/generate_openapi.py`, `npm run api:types`를 실행해 전체 OpenAPI·Auction slice·생성 TypeScript를 갱신했다. API index의 위치나 endpoint 목록은 바뀌지 않았다.
- 기존 receipt JSON은 그대로 유지한다. 새 mapper는 신규 필드가 없는 기존 receipt를 읽고 해당 값은 null로 반환하며 현재 상태를 끼워 넣지 않는다. 이후 최초 실행 receipt에는 새 필드가 포함된다. 과거 이력과 과거 receipt의 미상 값은 최신 상세 값과 구분한다.

### 회귀 방어

- 신규 도메인 단위 6건: 같은 상태의 두 번째 부분 반환·보정, 경매 결과/반환 확인 이후 보정 거절, 무변경 요청의 중복 이력 방지, null·음수·int overflow 입력의 변경 전 거절.
- [AuctionQuantityHistoryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/AuctionQuantityHistoryPostgresE2ETest.java): 16건. 반환·보정·부분 낙찰의 전후값과 ID·actor·메모·timeline HTTP, 네 종류의 결과 뒤 보정 HTTP 409, 시도 없는 반환 확인 뒤 보정, 정산/완납 양쪽 보존, 이력 저장 실패 시 반환·Receipt 및 직접 보정 rollback, 병렬 반환, 결과/보정 양방향 경쟁, 구형 receipt 재조회다.
- 경쟁 3건은 root 잠금을 보유한 선행 요청과 후행 PID의 실제 `pg_blocking_pids` 대기를 확인한다. 후행 snapshot은 선행 commit 뒤 수량을 사용하며, 결과 선행 시 기다린 보정은 최신 시도 사실로 거절한다. 보정 선행 시 결과는 보정 이후 대기 수량을 사용한다.
- 반환·보정 실패 및 거절은 관련 10개 테이블 JSON 값을 비교한다. 반환 이력 저장 실패는 receipt key를 소비하지 않고 같은 키의 정상 재시도가 성공한다. 정산·입금 검증은 실제 rebuild·수동 입금 application을 호출해 결과·정산 행·잔액·이벤트·감사의 보존을 확인한다.
- [AuctionQuantityHistoryMigrationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/AuctionQuantityHistoryMigrationPostgresE2ETest.java): 1건. 별도 PostgreSQL DB에서 V36→V37 upgrade, legacy history/lot/receipt 값 보존, 수량 미상 유지, 불완전·음수 snapshot 거절, 수량 불일치 기록 허용, 재실행 0건과 Flyway validate를 확인한다.
- 기존 [CoreQueryRegressionTest](../backend/src/test/java/com/greenhouse/backend/CoreQueryRegressionTest.java)의 경매 페이지 회귀를 1·10·50개로 확대했다. 실제 이력 snapshot·capability를 조립해도 5회 이하 조회를 유지한다. 기존 query-count를 느슨하게 바꾸지 않았다.

### 검증과 남은 범위

- 수정 전 단위 4건 모두 실패. 수정 후 신규 6건과 기존 경매 tracking·목록 query 회귀를 포함한 집중 일반 테스트 35건 성공.
- PostgreSQL 신규 quantity 15건·migration 1건과 기존 경매 Receipt 40건, 총 56건 집중 성공. 이후 직접 보정의 history 저장 실패 rollback 1건을 추가했으며 최종 전체 검증에서 함께 확인한다.
- 기존 integration의 확인 반환 뒤 직접 수량 보정 기대값은 이력 보존 정책에 맞춰 거절과 capability 검증으로 변경했다. 초기 테스트 import 누락 컴파일 오류는 추가 후 재실행했다. 도메인 정책·DB 제약을 완화하지 않았다.
- 최종 백엔드 전체 `./gradlew test`: 120개 클래스, 573건 성공. 신규 도메인 6건과 확대된 1·10·50개 query-count 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 47개 클래스, 354건 성공. 신규 수량 이력 16건·V37 migration 1건과 기존 337건을 포함하며 실패·오류·생략은 없다. 마지막에 추가한 직접 보정 이력 저장 실패 rollback도 성공했다.
- 프론트엔드 `npm run check`: 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공. 브라우저 E2E는 실행하지 않았다.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 뒤에는 진행 문서의 상태·결과만 갱신했다. 실행 코드·테스트·API 생성물은 바꾸지 않았다.
- V37 이전 누락된 수량 이력과 과거 보정으로 달라진 lot/result/정산은 자동 복원·보정하지 않는다. 운영 데이터 영향은 아직 대사하지 않았다. 같은 상태의 신규 쓰기 보호와 기존 정산·입금 보존 범위를 완료 대상으로 삼는다.

## 11차 변경 — BE-008 판매 전표 생성의 요청 재전송 방어

작업일: 2026-10-04. 상태: 판매 생성 수정·V38·화면 키·API 계약·정책 문서 및 최종 전체 검증 완료. 입고·일반 Work 생성은 이번 범위가 아니다.

### 수정 전 재현과 범위

- [SalesCreationIdempotencyPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesCreationIdempotencyPostgresE2ETest.java)의 HTTP 신규 2건을 기존 코드에서 실행했다. 같은 `Idempotency-Key`와 입력의 재전송은 다른 전표·예약을 만들었고, 같은 키의 입력 변경도 201로 별도 생성됐다. 실제 HTTP timeout을 주입한 시험은 아니며 성공 응답을 무시하고 같은 요청을 재전송해 응답 유실 뒤의 서버 처리를 재현했다.
- 초기 시험의 없는 거래처 enum과 원장 cutover 누락은 fixture 오류였다. 실제 write fence를 활성화한 후 위 두 결함을 재현했으며 운영 guard를 완화하지 않았다. 기존 Mutation source key는 새 전표 ID를 기준으로 하므로 다른 생성 요청을 dedup하지 못한다.
- 입고 생성과 일반 Work 계획에는 요청 식별자가 여전히 없다. 기존 Work 접수는 구조 기록·실행·보상 등의 의미와 membership을 소유하므로 Sales 생성에 공유하지 않는다. 범용 멱등 framework나 모든 POST의 강제 키 요구를 추가하지 않았다.

### 생성 계약과 원자성

- [SalesSlipCreationService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java)는 키가 있는 생성에서 접수 PK를 원자적으로 claim하고 해당 행을 잠근다. 최초 입력 지문 확인 → 완료 응답 재조회 → 최초 업무 순이며 최신 거래처·재고·상태 validation은 최초 실행에만 적용한다. 기존 public application 진입점의 트랜잭션을 유지하고 두 진입점은 private 생성 코어를 사용해 self invocation에 의존하지 않는다.
- 키는 판매 생성 전체 namespace에서 유일하다. 같은 키·같은 입력은 당시 응답을 `201`로 반환하고 다른 입력은 `409 / SALES_CREATE_REQUEST_KEY_CONFLICT`다. 공백/초과 길이 키는 `400 / VALIDATION_ERROR`다. 키 생략은 과거의 독립 생성 계약을 유지하며 신규 접수를 만들지 않는다. 실제 동일 입력의 새 전표는 새 키를 사용한다.
- 요청 지문은 typed 입력의 정렬된 JSON property를 SHA-256으로 계산한다. 품목·배분 배열 순서와 null/명시 기본값은 그대로 구분하고 현재 Entity 값·인증 actor를 지문에 끼워 넣지 않는다. 초기 영속 계약이며 추후 필드 변경은 기존 지문과 최초 응답의 호환을 함께 검토해야 한다.
- 접수 → 기존 거래처·일별 번호·묶음 → 신규 전표/출하 순으로 수행한다. 접수 대기 전에 재고·거래처 잠금을 잡지 않는다. 최초 응답 전 cascade ID를 flush하고 생성 응답을 JSONB로 보존한다. 전표·예약/출고·Mutation·이동·snapshot·잔액·감사·접수를 한 트랜잭션에서 확정한다. 실패하면 일별 번호와 접수도 rollback하며, sequence ID의 공백은 허용한다.
- [V38](../backend/src/main/resources/db/migration/V38__sales_creation_receipts.sql)은 PK, 전표 FK/삭제 제한, 키/지문 CHECK, 전표 ID와 응답 ID의 일치·완료 쌍 CHECK를 추가한다. claim 중인 행은 ID/응답 모두 null이며 application이 결과를 확정한 뒤 commit한다. DB 자체가 수동으로 commit한 미완료 claim을 금지하는 것은 아니며 이 경우 자동 재실행하지 않고 실패한다. 기존 전표·재고·출하·Receipt는 수정하거나 요청 키를 backfill하지 않는다.

### 화면·API와 회귀 방어

- `POST /api/sales-slips`만 선택형 header를 추가했다. 생성/수정 공용 body와 수정 API는 바꾸지 않았다. Controller·테스트를 기준으로 전체 OpenAPI·Sales slice와 TypeScript를 재생성했으며 프론트 요청 키 타입은 생성된 header 계약을 사용한다.
- 판매 생성 화면은 성공 응답을 받기 전까지 하나의 요청 키를 유지하고, 성공 직후에만 해제한다. 오류·폼 닫기·입력 변경·같은 탭 재진입/새로고침에서 자동 새 키를 만들지 않는다. 수정 저장에는 생성 키를 보내지 않는다. 키는 local transport state로 관리하고 서버 응답은 기존 React Query cache에 유지한다. 폼 내용은 sessionStorage에 저장하지 않으며 저장소를 사용할 수 없으면 현재 mounted 화면에서만 키를 보존한다.
- PostgreSQL 신규 생성 26건: 최초/변경 재전송, 일반·경매 작성중/완료 4개 조합, 취소·거래처 비활성 후 replay, 전량 출고 후 replay, 새 키의 동일 입력 생성, 키 없는 호환 생성, 주요 입력 변경 6개, 잘못된 키 3개, 수량 validation 실패, 직접/경매 완료 후 Receipt 저장 실패 rollback 2개, replay query/entity count, commit/다른 입력/rollback 경쟁 3개다.
- 경쟁은 `pg_blocking_pids`로 접수 키를 보유한 선행 PID를 후행 PID가 기다리는지 확인한다. commit 시 같은 입력은 같은 최초 결과를 받고 다른 입력은 거절된다. 선행 rollback 시 대기 요청이 키를 새로 확보하고 한 전표·예약·이동만 저장한다.
- 생성·거절·late rollback은 19개 관련 테이블 JSON을 비교한다. late 실패는 실제 완료 전표·출하/lot·재고 차감·잔액 조율 이후 접수 완료 CHECK를 실패시키며 같은 키 재시도 성공까지 확인한다. replay는 접수 조회 2 SQL 이하·Entity 1개만 로딩하며 현재 전표 응답을 재조립하지 않는다. 최초 쓰기의 비용·큰 응답 snapshot 저장 비용에 대한 benchmark는 실행하지 않았다.
- [SalesCreationReceiptMigrationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesCreationReceiptMigrationPostgresE2ETest.java): PostgreSQL 별도 DB에서 V37→V38 upgrade, 기존 전표/품목 보존과 미상 identity 유지, PK/FK/삭제 제한·키/지문·불완전/잘못된 응답 제약, 재실행 0건과 Flyway validate를 확인한다.
- 프론트 순수 로직 신규 5건은 응답 유실/반복 submit·성공 이후 새 업무·늦은 응답·재진입/새로고침·브라우저 저장소 실패의 키 수명을 확인한다. 브라우저 dialog/HTTP E2E는 실행하지 않았다.

### 검증

- 수정 뒤 최초 HTTP 2건 성공. 확장 PostgreSQL 26건 중 2건은 시험 ObjectMapper의 날짜 배열/HTTP 문자열 비교 오류였다. 실제 최초 HTTP 응답을 기준으로 비교를 고쳤고, 확장 26건과 V38 migration 1건 모두 성공했다.
- 프론트 신규 순수 로직 5건과 전체 `npm run check`의 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공.
- 최종 백엔드 전체 `./gradlew test`: 120개 클래스, 573건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 49개 클래스, 381건 성공. 신규 판매 생성 26건·V38 migration 1건과 기존 354건을 포함하며 실패·오류·생략은 없다.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 이후에는 진행 문서의 상태·결과만 갱신했으며 실행 코드·테스트·API 생성물은 바꾸지 않았다.

## 12차 변경 — BE-008 입고 생성의 요청 재전송 방어

작업일: 2026-10-04. 상태: 입고 생성·V39·화면 키·API 계약·정책 문서 및 최종 전체 검증 완료. 일반 Work 계획·기록 생성은 이번 범위가 아니다.

### 수정 전 재현과 계약

- [InboundCreationIdempotencyPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/InboundCreationIdempotencyPostgresE2ETest.java)의 HTTP 신규 2건을 기존 코드에 먼저 실행했고 모두 실패했다. 같은 키·입력은 다른 입고 ID·완료 작업을 만들었고 같은 키의 변경 입력도 201로 신규 생성됐다. 성공 응답을 무시하고 재전송한 서버 처리 재현이며 실제 transport timeout 주입 시험은 아니다.
- 신규 품종은 기존 `속 + 품종명` 조회로 재사용될 수 있어 품종이 항상 중복된다는 근거로 확대하지 않는다. 내부 Mutation identity는 새 입고 ID를 사용하므로 HTTP 생성을 dedup하지 못한다. 즉시 배치 입고는 같은 위치의 재전송을 별도 생성으로 검증하므로 기존 위치 충돌도 반환할 수 있었다.
- `POST /api/inbound-records`만 선택형 `Idempotency-Key` header를 추가했다. 같은 키·입력은 최초 응답을 `201`로 반환하고 다른 입력은 `409 / INBOUND_CREATE_REQUEST_KEY_CONFLICT`다. 빈/공백/100자 초과 키는 `400 / VALIDATION_ERROR`다. 키 생략과 키 없는 application 생성은 기존 독립 생성 계약을 유지한다. 모든 입고 유형의 생성 namespace는 같고 Sales·Work 접수와는 독립적이다.

### 구현과 원자성

- [InboundRecordService](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java)는 Farm 소유 접수 PK의 원자 claim → 접수 root 잠금 → 지문 확인 → 완료 응답 재조회 → 최초 생성 순으로 처리한다. 현재 품종·배치·입고 상태·Work 유형의 validation과 actor 해석은 최초 업무에만 적용한다. 기존 public application 트랜잭션 진입점을 유지하고 private 생성 코어를 호출해 self invocation에 의존하지 않는다.
- 지문은 typed 입력의 JSON property를 정렬한 SHA-256이며 null/명시값과 배치 BigDecimal의 소수 자릿수를 구분한다. Work 실행 지문이나 과거 Mutation hash 계약을 변경하지 않는다. 최초 업무는 기존 품종 해석 → 입고 → 즉시 배치 Mutation/묶음 → 완료 Work/대상/효과 → flush/응답 → 접수 완료 순이다. 새로운 역방향 Work·원본 묶음 잠금을 추가하지 않았다.
- 신규 품종·입고·묶음·Mutation·Work/효과·기존 감사·접수를 한 트랜잭션에서 확정하거나 rollback한다. 키 대기는 품종 생성·배치·Work 처리 전에 일어난다. 실패하면 같은 키로 재시도할 수 있으며 품종 코드·Entity sequence의 공백은 기존처럼 허용한다. 유리병 입고·신규 품종에 별도 생성 AuditEvent를 추가한 변경은 아니므로 BE-010의 감사 범위까지 완료 처리하지 않는다.
- 최초 응답의 ID·timestamp와 당시 capability·결과 묶음을 보존한다. 수정·취소·포트 완료 이후의 replay도 이 응답을 반환하며 최신 상태로 덮어쓰지 않는다. 이후 업무 판단에는 현재 상세를 조회한다. 현재 조회 mapper를 replay에 호출하지 않는다.
- [V39](../backend/src/main/resources/db/migration/V39__inbound_creation_receipts.sql)는 PK, 입고 FK/삭제 제한, 키/지문 CHECK, 완료 ID/응답 쌍과 응답 ID의 일치 CHECK를 추가한다. claim 중인 쌍은 둘 다 null이며 application은 응답을 확정한 뒤 commit한다. 수동 commit한 미완료 접수를 DB 자체가 금지하지는 않으며 이 행은 자동 재실행하지 않는다. 기존 입고·품종·Work·묶음·Mutation을 변경하거나 identity를 추정 backfill하지 않는다.

### 화면·계약과 회귀 방어

- Controller·시험을 기준으로 전체 OpenAPI·Inventory slice와 생성 TypeScript를 갱신했고 프론트 생성 키 타입은 생성된 header 계약을 사용한다. 신규 endpoint나 기존 body/응답 schema 변경은 없다.
- 입고 생성은 성공 응답 직후 키를 해제하고 이후 query 무효화와 선택 처리를 수행한다. cache 갱신 실패를 새로운 생성으로 취급하지 않는다. 오류·폼 닫기·같은 탭 재진입/새로고침에서 키를 유지하며, 폼·서버 응답은 sessionStorage에 복제하지 않는다. 수정·취소·포트 경로에는 생성 키를 전달하지 않는다.
- 판매 화면의 키 보존 코드를 [공통 local transport helper](../frontend/src/shared/lib/pendingCreationRequestKey.ts)로 이동했다. 기존 Sales sessionStorage namespace·수명과 5개 회귀를 보존하고 입고 namespace를 분리했다. backend receipt와 fingerprint를 공통 framework로 이식하지 않았다. 신규 frontend 2건은 두 업무의 namespace/재진입 독립성과 입고의 미확인/늦은 응답 수명을 확인하며 기존 5건도 함께 성공했다.
- 신규 PostgreSQL 생성 43건: 기본/변경 재전송 2건, 5개 입고 유형×기존/신규 품종 10건, 유리병/즉시 배치 취소 후 replay 2건, 수정/포트 후 replay 2건, 입력 필드 변경 10건, 키 validation 3건, 성공 키의 잘못된 body 뒤 정상 replay 1건, 새 키/키 생략 2건, 접수 완료 CHECK 실패 2건, 비활성 Work 유형에 의한 후행 실패 1건, replay query/entity count 2건, 유리병/즉시 배치 commit·다른 입력·rollback 경쟁 6건이다.
- 경쟁은 `pg_blocking_pids`와 후행 `pg_stat_activity.query`로 접수 행에서 선행 PID를 기다리는지 확인한다. 같은 입력은 최초 결과를 받고 다른 입력은 stable code로 거절된다. 선행 rollback 시 대기 요청이 새 claim을 확보해 한 입고·품종·완료 Work·대상/효과·묶음만 만든다.
- 거절·late rollback·replay는 관련 17개 테이블 JSON을 비교한다. 후행 Work 비활성 실패와 접수 완료 CHECK 실패는 앞선 신규 품종·입고·Mutation/묶음·Work/효과까지 원복하며 같은 키 재시도 성공을 확인한다. replay는 유리병/즉시 배치 모두 2 SQL 이하·Entity 1개만 로딩한다. 최초 쓰기와 큰 snapshot의 비용 benchmark는 실행하지 않았다.
- [InboundCreationReceiptMigrationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/InboundCreationReceiptMigrationPostgresE2ETest.java): 별도 PostgreSQL DB에서 V38→V39 upgrade, 기존 입고/품종 보존과 신규 identity 미생성, PK/FK/삭제 제한·키/지문·불완전/잘못된 응답 제약, 재실행 0건과 Flyway validate를 확인한다.

### 검증

- 수정 전 HTTP 2건 실패, 수정 후 같은 2건 성공. 확장 PostgreSQL 생성 43건과 V39 migration 1건, 총 44건 집중 검증 성공.
- 프론트 신규 2건·기존 Sales 5건 집중 성공. 전체 `npm run check`의 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공. 브라우저 dialog/HTTP E2E는 실행하지 않았다.
- 최종 백엔드 전체 `./gradlew test`: 120개 클래스, 573건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 51개 클래스, 425건 성공. 신규 입고 생성 43건·V39 migration 1건과 기존 381건을 포함하며 실패·오류·생략은 없다.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 이후에는 진행 문서의 상태·결과만 갱신했으며 실행 코드·테스트·API 생성물은 바꾸지 않았다.

## 13차 변경 — BE-008 일반 Work 계획·완료 기록 생성의 재전송 방어

작업일: 2026-10-04. 상태: 일반 Work 생성·V40·화면 키·계약·정책 문서 및 최종 전체 검증 완료.

### 재현과 범위

- [WorkCreationIdempotencyPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCreationIdempotencyPostgresE2ETest.java)의 HTTP 2건을 기존 코드에서 먼저 실행했다. 같은 header·입력은 다른 계획 ID를 만들었고 같은 키의 변경 입력도 201로 별도 생성됐다. 최초 fixture의 없는 accessor는 실제 `pesticideWorkTypeId`로 고쳐 컴파일한 뒤 위 결함을 재현했다. 실제 HTTP timeout을 주입한 시험은 아니며 성공 응답을 무시하고 재전송한 서버 처리다.
- 범위는 [WorkOperationPlanService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java)의 일반 단건 계획·품종별 일괄 계획·일반 완료 기록이다. 선택형 `Idempotency-Key`를 세 HTTP 경로에 추가하고 기존 키 없는 application 호출·내부 구조 기록 조합·body·응답 schema는 유지한다. 입고 포트 계획과 키 없는 폐기 기록, 기존 실행/구조 변경/포트 body 키는 이번 범위가 아니다.
- 같은 경로·키·typed 입력은 최초 응답을 201로 반환하고 변경 입력은 기존 Work 오류인 `409 / IDEMPOTENCY_KEY_REUSED`다. 앞뒤 공백 제거 후 빈/공백/100자 초과 키는 `400 / VALIDATION_ERROR`다. 경로별 독립 scope이므로 재전송은 endpoint도 유지하고 실제 같은 내용의 새 작업에는 새 키를 사용한다. 키 없는 생성은 별도 작업으로 처리한다.

### 접수·잠금·snapshot·원자성

- [WorkCommandReceipts](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java)에 Work 생성 응답 전용 경로를 추가했다. 기존 Work 소유 접수 PK claim → 접수 root 잠금 → 지문 확인 → 완료 snapshot 재조회 → 최초 업무 순이며 대상 해석·활성 유형/actor 검증·묶음 잠금은 최초 실행에만 적용한다. `GENERAL_PLAN`, `GENERAL_PLAN_BATCH`, `GENERAL_RECORD`는 기존 즉시/구조 변경/포트/보상 scope와 독립적이다. Farm·Sales 접수나 범용 멱등 framework를 공유하지 않는다.
- Work의 기존 canonical 지문을 사용한다. JSON object 순서와 숫자 소수 자릿수는 정규화하고 배열 순서·문자열·null/명시값은 구분한다. 현재 actor·Entity 값으로 입력을 다시 만들지 않는다. 기존 public application 진입점의 트랜잭션과 private 생성 코어를 사용하며 self invocation에 별도 트랜잭션을 기대하지 않는다.
- 최초 실행의 전체 품종별 작업·대상·실행·일반 완료 효과·기존 감사, 결과 ID·응답 snapshot·생성 membership을 같은 트랜잭션에 확정한다. 접수 완료와 membership을 명시적으로 flush하며 어느 후행 저장이 실패해도 앞선 업무·접수를 rollback한다. 이 일반 기록은 기록형 효과이며 새 수량 Mutation이나 새로운 계획 AuditEvent를 추가한 변경은 아니다. 기존 구조 변경·폐기·Mutation 동작은 유지한다.
- 신규 일반 생성은 최초 시각·대상·진행 상태·capability를 snapshot에 보존한다. 작업명 변경·계획 시작·유형 비활성·취소 뒤에도 이 응답을 반환하고 현재 Work/대상을 재조회하지 않는다. 이후 업무 판단은 최신 상세를 사용한다. 기존 ID-only Receipt의 현재 상세 조회 계약은 바꾸지 않는다.
- 기존 생성 membership을 사용하므로 혼합 품종 계획은 동일 생성 묶음으로 조회할 수 있다. 응답/작업 ID 배열 순서를 보존하고 replay에서 membership을 재추가하지 않는다. 기존 실행·취소의 비생성 접수 의미도 유지한다.
- [V40](../backend/src/main/resources/db/migration/V40__work_creation_response_snapshots.sql)은 기존 Receipt에 nullable JSONB snapshot과 신규 일반 생성의 완료 쌍 CHECK를 추가한다. snapshot은 비어 있지 않은 object 배열·양수 숫자 ID·결과 ID의 순서 일치를 검사한다. 기존 지문/ID-only/미상 원문·membership·업무 행을 수정하거나 과거 응답을 추정 backfill하지 않는다. DB가 수동 commit한 미완료 claim 자체를 금지하지는 않으며 application은 이를 자동 재실행하지 않는다. snapshot 없는 완료 요청의 생성 재조회는 기존 `IDEMPOTENCY_REPLAY_UNAVAILABLE`로 실패한다.

### 화면·API·회귀 방어

- 일반 등록 화면은 공통 pending-key helper로 계획/완료 기록의 독립 sessionStorage scope을 유지한다. 오류·폼 닫기·새로고침·입력 변경에서 키를 바꾸지 않고 성공 응답 직후 해제한 뒤 기존 저장 callback/닫기를 수행한다. 폼·서버 응답은 storage에 복제하지 않으며 storage 차단 시 mounted 화면 안에서만 유지한다. 전용 결과 입력 dialog·포트 계획은 기존 호출을 유지한다.
- Controller·시험을 기준으로 전체 OpenAPI·Work slice·생성 TypeScript를 갱신했다. 프론트 생성 키는 생성된 header 타입을 사용하고 기존 공통 `requestApi`로 전달한다. 공유 helper의 신규 1건은 일반 계획/완료 기록 키의 독립성과 폼 재진입 수명을 확인하며 기존 Sales/Inbound 7건도 유지한다. 브라우저 dialog/HTTP E2E는 실행하지 않았다.
- 신규 PostgreSQL 생성 55건: 수정 전 재현 2건, 세 경로의 동일 요청/현재 변경/취소/접수 완료 실패/membership 실패/조회 증폭/키 없는 호환 각 3건(21건), 입력 필드 변경 9건, 키 validation 9건, 새 키/경로 scope 독립 1건, 혼합 품종 결과 순서/생성 관계 1건, 혼합 품종 membership 실패 rollback 1건, validation rollback 뒤 수정 재시도 1건, object 순서/숫자 표기 지문 1건, 세 경로×commit/다른 입력/rollback 경쟁 9건이다.
- 경쟁은 `pg_blocking_pids`와 후행 `pg_stat_activity.query`로 접수 행에서 선행 PID를 기다리는지 확인한다. 선행 commit의 같은 입력은 최초 응답, 다른 입력은 안정적인 409 code이며 선행 rollback 시 후행이 새 claim을 확보해 한 작업·대상·실행/효과·membership만 만든다. thread barrier/timeout을 사용하고 business lock 대기를 접수 대기로 오인하지 않는다.
- 후행 저장 실패와 replay/거절은 관련 13개 테이블 JSON을 비교한다. Receipt 저장 이후 membership 실패도 completed receipt·Work·효과·감사까지 원복한다. 혼합 품종은 모든 작업을 원복하고 같은 키로 전체 재시도 성공을 확인한다. replay는 세 경로 모두 2 SQL 이하·Entity 1개 로딩이다. 큰 대상/최초 응답 snapshot의 저장·heap 비용 benchmark는 실행하지 않았다.
- [WorkCreationSnapshotMigrationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCreationSnapshotMigrationPostgresE2ETest.java)는 별도 PostgreSQL DB의 V39→V40 upgrade에서 과거 원문 미상/알려진 ID-only Receipt·실제 Work·membership 보존, snapshot 미생성, 세 신규 scope의 완료 쌍·잘못된 JSON·ID 배열 순서·필수 지문 CHECK, 재실행 0건과 Flyway validate를 확인한다. 기존 PK/FK/UNIQUE를 변경하지 않는다.

### 검증

- 수정 전 HTTP 2건 실패, 수정 후 동일 2건 성공. 확장 실행의 6건은 application Map의 Long/Integer를 `valueToTree`로 비교한 시험 표현 차이였다. 실제 wire JSON round-trip 비교로 고쳤으며 HTTP 최초/재전송 비교와 DB/query 조건은 완화하지 않았다. 확장 생성 55건·V40 migration 1건, 총 PostgreSQL 56건 집중 성공.
- 프론트 신규 1건·기존 7건 집중 성공. 전체 `npm run check`의 포맷·생성 타입 drift·전체 순수 로직 시험·lint·production build 성공.
- 최종 백엔드 전체 `./gradlew test`: 120개 클래스, 573건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 53개 클래스, 481건 성공. 신규 일반 Work 생성 55건·V40 migration 1건과 기존 425건을 포함하며 실패·오류·생략은 없다.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 이후에는 진행 문서의 상태·결과만 갱신했으며 실행 코드·테스트·API 생성물은 바꾸지 않았다.

## 14차 변경 — BE-010 전표 생성·묶음 metadata 감사 완전성

작업일: 2026-10-04. 상태: 전표 생성·묶음 metadata 감사 코드·정책 문서 및 최종 전체 검증 완료.

### 범위와 원인

- 수정 전 PostgreSQL HTTP 시험에서 전표 생성은 성공했지만 `SALES_SLIP / CREATED` 감사가 없었다. 묶음 메모 변경도 업무 데이터에는 반영됐지만 감사의 변경 필드에 메모가 없었다. 기존 fixture의 비표준 화분 표시값까지 보정되는 혼입은 cutover 전 표준 표시값으로 바로잡아 metadata 단독 변경을 검증한다.
- [SalesSlipCreationService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java)는 최초 생성 코어의 예약·필요한 출고/출하·잔액 처리와 flush 뒤 최종 전표를 `CREATED / SALES_MANAGEMENT`로 기록한다. 기존 Sales 소유 snapshot과 Audit 값 계약을 사용하며, actor·세션·요청·브라우저 식별자는 기존 인증 요청 맥락에서 읽는다. HTTP 맥락 없는 application 호출의 actor는 추정하지 않는다.
- 생성 접수 replay는 최초 생성 코어에 진입하지 않아 새 감사나 실행자 덮어쓰기가 없다. 키 없는 별도 생성은 각각 감사한다. 감사·전표·예약/차감·Mutation·이동·경매 출하·잔액·접수는 기존 최상위 생성 트랜잭션에 참여하며 감사 저장 실패도 전체를 rollback한다.
- [OrchidGroupAuditSupport](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupport.java)의 필드명 목록과 값 배열은 동일 속성을 위치로 맞추고 있었으며 배치 유형·트레이 수·분할 배치 허용·메모가 모두 빠져 있었다. 필드명/값 Map 한 곳에서 기존 공통 comparator로 변경 여부를 계산한다. 기존 필드명·순서·source·비활성 action 분류를 유지하며, metadata 단독 변경도 단건/일괄 보정 감사에 포함한다.
- 메모는 비교용 snapshot에만 포함하고 영속 감사 전후 값에서 제외한다. 실제 변경 필드 `memo`와 맥락의 `redactedFields`만 보존해 메모 교체·삭제도 감지하며 원문을 복제하지 않는다. 원본 메모와 Mutation 업무 사실은 기존대로 보존한다. 이 제외 정책을 기존 Sales 전표/품목 메모 정책까지 확대하는 변경은 아니다.
- Farm의 전체 묶음→전체 구역 선잠금과 잠금 이후 snapshot 시점은 유지한다. 동일 값 요청은 Mutation·감사를 추가하지 않으며, 감사 실패나 후행 일괄 항목 실패는 선행 변경까지 원복한다. 과거 누락된 감사의 값·실행자를 추정 backfill하지 않는다.

### 회귀 방어와 계약

- [DomainAuditCompletenessPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/DomainAuditCompletenessPostgresE2ETest.java) 신규 29건은 실제 PostgreSQL·Spring MVC/security filter·서비스 트랜잭션을 사용한다. application 실패 주입은 테스트 바깥의 독립 트랜잭션으로 검증하며, 비교에는 관련 21개 테이블의 전체 행 JSON을 사용한다.

| 시나리오 | 건수 | 보호 결과 |
| --- | --- | --- |
| 최초 HTTP 재현 회귀 | 2 | 전표 생성 주체·요청 맥락과 메모 단독 변경 필드 |
| 4개 metadata × 단건/일괄 수정 및 동일 값 재요청 | 8 | 실제 변경 필드·전후 값·실행자·맥락, 중복 감사/Mutation 방지 |
| 메모 교체/삭제 | 2 | 변경 감지와 전후 감사·맥락의 원문 제외 |
| 일반/경매 × 작성중/출고·출하 완료 생성 및 다른 실행자의 replay | 4 | 최종 전표 snapshot과 최초 실행자·응답 보존 |
| 4종 생성 × 키 유무의 감사 CHECK 실패 및 재시도 | 8 | 예약·출고/출하·Mutation·출하 이력·잔액·접수의 전체 rollback |
| 단건/일괄 metadata 감사 CHECK 실패 | 2 | 원본·Mutation·감사의 전체 rollback |
| 후행 일괄 항목 배치 충돌 | 1 | 선행 metadata와 감사까지 rollback |
| 감사 저장이 불가능한 상황의 무변경 요청 | 1 | 불필요한 감사·Mutation 미생성 |
| 키 없는 두 신규 전표 | 1 | 각각의 생성·실행자 감사와 접수 미생성 |

- 기존 [SalesCreationIdempotencyPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesCreationIdempotencyPostgresE2ETest.java)의 4종 생성과 실제 commit/입력 충돌/rollback 경쟁에도 생성 감사 1건을 명시 검증한다. 기존 접수 완료 CHECK 실패의 전체 rollback 비교는 새 생성 감사까지 포함한다. Farm 기존 잠금 경쟁 회귀는 전체 PostgreSQL 검증에서 유지한다.
- 기존 경매 취소 회귀의 전체 행 비교는 JDBC 배열 객체의 동등성에 의존하고 있었다. 새 생성 감사의 `changed_fields`가 포함되도록 snapshot을 행 전체의 JSON 문자열로 바꾸며, 감사·배분·Mutation·재고와 기존 13개 비교 테이블 및 rollback/거절 조건은 유지한다.
- 도메인·기능 요약·판매/작업 기능 문서와 API 도메인 규칙에 생성 주체·최종 상태·메모 제외·재전송·rollback 정책을 갱신했다. HTTP body/응답/header·enum·capability·DB schema·저장 지문은 변경하지 않아 OpenAPI/생성 TypeScript와 Flyway는 갱신하지 않는다. 신규 감사 조회 화면/API도 추가하지 않는다.
- BE-009의 일반 수정 허용 범위는 감사 보강과 다른 정책 판단이다. 기존 일반 수량·상태·배치 보정 계약은 유지하며, 이 변경으로 보정/실사 gate와의 정책 차이를 해결했다고 판정하지 않는다. Auction 자체 이력·WorkEffect·Mutation도 계속 업무 사실의 원장이며 모든 경로를 일반 AuditEvent로 중복 수집하지 않는다. 입고/inline 신규 품종 생성 주체 등 다른 감사 범위의 필요성은 별도 판단한다.

### 검증

- 기존 일반 감사 unit/integration/rollback·Sales 수정 감사 집중 검증 10건과 신규 PostgreSQL 최초 2건 성공.
- 확장 PostgreSQL 신규 29건·기존 판매 생성 26건, 총 55건 성공. 첫 확장 실행의 DB 비교 fixture 테이블명 오류는 실제 migration의 저장 테이블 21개로 바로잡았으며 전체 행 비교와 rollback 조건을 유지했다.
- 최초 PostgreSQL 전체 510건 중 기존 JDBC 배열 snapshot 비교 7건이 실패했다. 행 전체 JSON 값 비교로 수정한 경매 취소 10건 집중 성공 후 PostgreSQL 전체를 재실행했다. 마지막 실행 코드/시험 변경은 PostgreSQL 전용 helper이며 일반 실행 코드와 프론트 코드는 그대로다.
- 백엔드 전체 `./gradlew test`: 120개 클래스, 573건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 54개 클래스, 510건 성공. 신규 감사 29건과 기존 481건을 포함하며 실패·오류·생략은 없다.
- 프론트엔드 전체 `npm run check`: 성공. 브라우저 E2E와 신규 쓰기 비용 benchmark는 실행하지 않았다.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 이후에는 진행 문서의 상태·결과만 갱신했으며 실행 코드·테스트·API 생성물은 바꾸지 않았다.

## 15차 변경 — BE-011 기존 CHECK 제약의 대사·검증 절차

작업일: 2026-10-04. 상태: 운영 도구·정책 문서 및 최종 전체 검증 완료. 운영 DB의 제약 상태·위반 행은 확인하지 않았다.

### 범위와 구현

- V14·V21의 4개 미검증 CHECK와 BE-003에서 추가한 V35의 금액 CHECK 2개를 대상으로 삼는다. 기존 데이터 보존을 위한 `NOT VALID`는 유지하며, 미확인 운영 자료 때문에 자동 Flyway validation이나 임의 수량/금액 backfill을 추가하지 않는다.
- [공유 inventory](../scripts/data-audit/domain-constraint-catalog.sql)는 대상 테이블·제약 이름만 선언하고, 설치된 정의·CHECK 식·종류·`convalidated`를 PostgreSQL catalog에서 읽는다. 업무 predicate를 운영 도구에 다시 작성하지 않는다. 같은 이름의 다른 종류나 누락된 제약을 실제 정상 0건과 구별한다.
- [read-only 대사](../scripts/data-audit/audit-domain-constraints.sql)는 `REPEATABLE READ / READ ONLY` 한 트랜잭션에서 DB·계정·트랜잭션 시작 시각·snapshot ID와 6개 보고를 JSONL로 반환한다. 실제 CHECK 식의 FALSE를 위반으로 세고 NULL 허용 의미를 유지한다. 전체 위반 건수와 정렬한 최대 50개 ID만 반환하며 메모·품목 원문은 읽기 결과에 포함하지 않는다. RLS로 일부 행만 보이는 연결은 오류로 실패한다.
- 보고 상태는 `MISSING`, `NOT_CHECK`, `VIOLATIONS`, `UNVALIDATED`, `VALIDATED`다. 누락/종류 불일치의 위반 건수는 null이다. 명령 종료 0은 대사 완료이며 DB 검증 완료와 다르다. 이 보고는 allocation 합계·Mutation head·전표/품목 합계·입금/잔액의 교차 불변식을 대신하지 않는다.
- [제약별 validation](../scripts/data-audit/validate-domain-constraint.sql)은 명시한 inventory의 실제 CHECK 하나만 독립 트랜잭션에서 검증한다. 값은 psql literal로 처리하고 식별자는 DB metadata에서 quote해 미지정·알 수 없는 이름·SQL 문자열·다른 종류를 DDL 대상으로 사용하지 않는다. 이미 검증한 제약은 DDL을 반복하지 않으며 결과는 commit 이후의 catalog를 다시 읽는다.
- 대사·검증 SQL은 잠금 대기 3초, 각 statement 5분 상한을 둔다. 위반 또는 잠금 실패 시 해당 validation과 제약 상태를 원복하고 기존 행·이력은 보존한다. 한 제약의 성공이 이후 다른 호출의 실패로 원복되는 전체 배치 계약은 아니다. 마지막 read-only 보고로 6개 전체 상태를 다시 판단한다.
- 배포 문서에 최신 백업 사본 rehearsal→위반/원장 대사→이력 보존 복구→재대사→제약별 validation→최종 상태 확인을 추가하고 backend migration 기준에도 연결했다. 운영 primary의 실제 scan 비용·잠금 시간과 기존 불량 행은 별도 확인 대상이다. runtime application·HTTP/API·기존 Flyway 파일·저장 snapshot/지문은 변경하지 않는다.

### 회귀 방어

- [DomainConstraintOperationsPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/DomainConstraintOperationsPostgresE2ETest.java) 신규 24건은 현재 release 전체 Flyway를 적용한 격리 PostgreSQL 18 DB에서 실제 psql 파일을 실행한다. read-only role, 부분 validation, 해당 CHECK의 위반으로 실패하는 validation과 전체 public 테이블의 행·기존 CHECK metadata 보존을 확인한다. legacy 위반 fixture는 설치된 CHECK 정의를 보존하고 테스트 안에서 잠시 제거·재등록한 상태이며 실제 운영 위반을 발견했다는 의미는 아니다.

| 시나리오 | 건수 | 보호 결과 |
| --- | --- | --- |
| SELECT 전용 계정의 정상 행 대사 | 1 | 실제 read-only/isolation·snapshot, 미검증 상태, inventory와 현재 미검증 CHECK의 일치 |
| 6개 CHECK별 legacy 위반과 validation 실패 | 6 | 정확한 위반 건수/ID·대상 제약 오류, 새 변경 보호와 행/metadata 보존 |
| 6개 CHECK별 정상 validation 및 반복 | 6 | 해당 제약만 검증, 이력 보존, Flyway validate·재실행 0건 |
| 제약 누락·다른 종류 | 2 | 정상 0건으로 오인하지 않고 validation 거절 |
| 미지정·빈·미등록·SQL 문자열 입력 | 4 | 임의 DDL 대상 선택과 데이터 변경 방지 |
| 위반 105행 | 1 | 전체 건수와 50개 표본 상한·정렬 |
| 다른 설치 CHECK 식과 NULL | 1 | predicate 복제 없이 실제 식 평가, CHECK의 NULL 의미 유지 |
| 행을 숨기는 RLS | 1 | 불완전한 가시성으로 false clean 보고 방지 |
| 다른 트랜잭션의 exclusive lock | 1 | validation 잠금 timeout·미검증 상태 보존 |
| 일반 유효 UPDATE의 잠금과 validation | 1 | 일반 쓰기가 열려 있어도 validation 완료, commit 이후 위반 0건 |

- `workE2eTest`의 Gradle 입력에 실제 SQL 3개를 포함했다. SQL만 수정해도 시험이 이전 결과를 `UP-TO-DATE`로 재사용하지 않는다. 현재 Docker Compose와 기존 PostgreSQL suite의 18 버전을 사용하며 다른 PostgreSQL 버전은 이번에 실행하지 않았다.

### 검증

- 최초 시험 컴파일의 Testcontainers import 경로는 설치된 2.0.5 jar의 실제 경로로 바로잡았다. 신규 22건, 일반 쓰기 경쟁을 포함한 확장 23건과 기존 V35 upgrade 2건 성공.
- 최종 집중 PostgreSQL 신규 24건·기존 V35 migration 2건, 총 26건 성공. H2 결과로 제약·잠금·psql 동작을 판정하지 않는다.
- 백엔드 전체 `./gradlew test`: 120개 클래스, 573건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 55개 클래스, 534건 성공. 신규 운영 제약 24건과 기존 510건을 포함하며 실패·오류·생략은 없다.
- 프론트엔드 전체 `npm run check`: 성공. 운영 백업/primary 대사·실제 validation·대용량 scan benchmark와 브라우저 E2E는 실행하지 않았다.
- `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 이후에는 진행·운영 문서의 상태/결과·표현만 갱신했으며 SQL·실행 코드·테스트·API 생성물은 바꾸지 않았다.

## 16차 변경 — BE-012 포트 실행의 typed command 유지

작업일: 2026-10-04. 상태: 포트 실행 범위 구현·회귀·정책 문서 및 최종 전체 검증 완료. BE-012의 다른 효과/JSON 경계는 후속 범위다.

### 원인과 범위

- 포트 전용 API는 이미 `InboundPottingCommand`·`InboundPottingResultInput`을 받지만 내부에서 Map → 범용 HTTP 요청 DTO → Farm HTTP DTO로 재변환했다. 저장 JSON 형식이 실행 계약을 대신해, 필드 변경을 compiler가 검증할 수 없는 것이 이번 범위의 원인이다. 포트와 일반 대상 완료가 서로 다른 호환 입력을 제공하는 의미는 유지한다.
- `InboundPottingOperationService` → `WorkOperationProgressService` → 효과 processor/handler → Farm 실행까지 기존 application 명령을 전달한다. Farm 실행 service는 HTTP DTO를 소비하지 않고 기존 result 필드를 직접 사용한다. handler에서 명령의 입고 ID와 실제 작업 대상 일치를 확인한 뒤 Mutation을 적용한다.
- 포트의 저장·지문 비교와 구형 Map 대상 완료 해석은 조회 없는 `InboundPottingCommandCodec`에 둔다. 전용 실행은 저장용 Map을 실행 입력으로 다시 읽지 않는다. 날짜의 기존 array 표현·null 키·결과 배열 순서·제외되는 identity 필드를 유지하며 구형 ISO 날짜·누락 optional 필드·알 수 없는 키의 거절도 보존한다.
- 새로운 범용 handler framework나 저장 schema version을 추가하지 않았다. HTTP/OpenAPI·Flyway·효과 identity·조회 응답·트랜잭션 경계·잠금 순서를 바꾸지 않는다. 기존 receipt·효과 지문과 legacy 효과 키를 재사용한다.

### 회귀 방어

- codec 신규 8건: rich/nullable 저장 JSON fixture와 고정 SHA-256 golden 2개, 결과 모든 필드·순서, array/ISO 날짜, optional 부재, identity/미등록 키의 기존 거절.
- handler 신규 2건: typed payload를 직접 소비하며 저장 Map을 다시 해석하지 않음, 다른 입고 ID의 typed 명령을 Mutation 이전 거절.
- PostgreSQL 신규 9건: 전용 실행의 필드·순서·효과/Mutation link·JSON/지문, receipt 없는 현재/legacy 효과 키 replay, 구형 대상 완료의 fingerprint 유무와 완료일 생략 replay, 다른 내용 거절, 전용/구형 경로의 두 번째 배치 실패와 효과 저장 CHECK 실패 시 전체 public 행 보존·같은 요청 재실행.
- JSON fixture는 이번 변경 이전 포트 저장 계약을 고정한 시험 자료이며 운영 DB의 실제 이력을 추출한 자료가 아니다. legacy 키/지문 부재 회귀도 격리 DB의 호환 fixture다. rollback은 행·이력을 비교하며 sequence 번호 공백을 실패로 취급하지 않는다.
- 기존 포트 단건·일괄 기록·계획 재사용·조회·수량/Mutation routing 및 형제 입고 병렬 실행·취소 회귀를 최종 전체 검증에 포함한다.

### 검증

- 초기 시험의 AssertJ generic 추론 모호성을 명시적인 JSON tree 비교로 수정했다. PostgreSQL receipt 삭제 fixture는 실제 `receipt_key`로 수정했다. 제품 코드의 실패는 관찰하지 않았다.
- 최종 집중 단위 신규 10건·PostgreSQL 신규 9건과 기존 형제 입고 병렬 실행·취소 6건, 총 25건 성공. 기존 `InboundPottingPlanIntegrationTests`·효과 processor/store 집중 검증도 성공했다.
- 백엔드 전체 `./gradlew test`: 122개 클래스, 583건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 56개 클래스, 543건 성공. 신규 9건과 기존 534건을 포함하며 실패·오류·생략은 없다.
- 프론트엔드 전체 `npm run check`, `./gradlew spotlessCheck`, `git diff --check`: 성공. 전체 백엔드 검증은 8분 12초 소요했다. 새 benchmark와 브라우저 E2E는 실행하지 않았다.
- 최종 전체 검증 이후에는 진행 문서의 완료 상태·검증 결과만 갱신했다. 실행 코드·테스트·저장 fixture·HTTP/API 계약은 바꾸지 않았다.

### 남은 범위

- BE-012 전체를 완료로 판정하지 않는다. 공통 `WorkEffectCommand.payload`의 Object 경계, `WorkExecutionResult`의 Map 결과, 구조 변경/상세/수량/계보 JSON reader의 중복과 format별 precedence는 후속 범위다. 이번 포트 refactor로 다른 handler의 타입 안전성이 함께 개선되었다고 판단하지 않는다.
- BE-013의 schema version·과거 지문 corpus·rolling writer 정책은 이번 변경과 별도다. 신규 golden은 포트의 현재 저장 계약만 고정하며 다른 유형이나 운영 과거 버전 전체의 호환성을 증명하지 않는다.

## 17차 변경 — BE-012 고정 효과 결과의 타입 유지

작업일: 2026-10-04. 상태: 고정 효과 결과 계약·회귀·정책 문서 및 최종 전체 검증 완료. 명령 Object·구형 JSON reader는 후속 범위다.

### 원인과 범위

- 16차는 포트 입력의 왕복을 제거했지만, 고정 결과도 handler에서 기존 record를 만든 직후 Map으로 변환했다. Work와 Farm의 실행 계약이 저장 형식에 묶이고 이동 identity·보정 수량 수지 같은 후속 필드는 별도로 Map에 붙었다. 이번에는 기존 결과 값이 실제 실행 계약을 대신하도록 한다.
- `WorkExecutionResult`는 한정된 `WorkEffectResultDetails`를 보유한다. 구조 변경·identity 유지 이동·포트·폐기·보정 writer는 기존 typed 값을 그대로 반환하고 효과·대상 실행·보정 이력의 저장 지점에서 `storedDetails()`로 변환한다. Map을 받는 생성자는 두지 않아 새 고정 writer의 조기 Map 변환을 컴파일 단계에서 발견할 수 있다.
- `identityPreserved`와 `quantityBalances`를 각각 구조 변경·보정 값에 포함했다. false인 identity와 빈 수량 수지 필드는 기존처럼 JSON에서 생략한다. 단일/복수 원본의 compatibility 필드·결과 배열 순서·null 날짜와 위치·폐기 사유 생략/trim을 유지한다.
- 자유 기록·입고 부가 정보·현장 동기화 snapshot은 기존 JSON 의미가 계약이므로 명시적인 JSON 결과를 사용한다. 기존 효과 replay도 저장 JSON을 이 경로로 반환하며 미등록 필드·null·형식·순서를 새 고정 타입이나 현재 Farm Entity로 복원하지 않는다. handler·결과 ID·Mutation link는 기존 저장 행과 연결 행에서 유지한다.
- 기존 합식의 여러 대상에 결과를 저장할 때는 변환을 반복문 밖에서 한 번 수행한다. 이동 후 폐기의 연결 정보는 기존처럼 대상 실행 이력에 별도로 붙이고 원래 효과의 사실을 덮어쓰지 않는다.
- 새 범용 handler framework·저장 schema version·DB migration·HTTP/OpenAPI 변경은 없다. 명령 지문·효과 identity·수량 정책·잠금·최상위 transaction·감사/Mutation 생성 순서는 유지한다.

### 회귀 방어

- 신규 `WorkExecutionResultTest` 19건: 저장 JSON golden 13종, 과거 JSON replay 3종, 자유 기록 3종. 단일/복수 원본·identity 유지·legacy 합식/생성/이동·포트·폐기·수량/날짜 보정의 필드 유무·null·숫자·배열 순서를 고정한다. 이 golden은 기존 코드의 저장 경계 계약을 명시한 시험 자료이며 운영 이력을 추출한 corpus는 아니다.
- 효과 store 기존 회귀를 강화해 typed 결과 저장 이후에는 기존 JSON·결과 ID 순서·Mutation link로 replay함을 확인한다. 효과 processor와 Farm 포트 handler도 typed 값을 그대로 전달하는 회귀를 유지한다.
- PostgreSQL 기존 회귀를 강화해 identity 유지 이동과 포트의 효과/대상 JSON 전체를 비교한다. 수량 보정의 `quantityBalances` 전후 사실과 최초 효과 snapshot 보존, 날짜만 보정할 때 필드 생략 및 replay 보존을 확인한다. 기존 rollback·병렬 보정·취소·계보·대사 회귀도 최종 전체 검증에 포함한다.
- 새 포트 JSON 비교의 기대값이 LongNode, 실제 파싱 값이 IntNode인 시험 표현 차이를 발견했다. 두 값을 JSON으로 동일하게 읽어 비교하도록 수정했다. 저장 데이터 차이나 제품 코드 오류로 판정하지 않는다.

### 검증

- 최종 집중 단위 5개 클래스 37건·PostgreSQL 4개 클래스 30건, 총 67건 성공. 저장 JSON golden 신규 19건과 기존 processor/store/포트/이동/수량/병렬 보정·취소 회귀를 포함한다.
- 백엔드 전체 `./gradlew test`: 123개 클래스, 602건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- 최종 PostgreSQL 전체 `./gradlew workE2eTest`: 56개 클래스, 543건 성공. 강화한 저장 JSON·수량 수지·날짜 보정 replay와 기존 DB/이력 회귀를 포함하며 실패·오류·생략은 없다.
- 프론트엔드 전체 `npm run check`, `./gradlew spotlessCheck`, `git diff --check`: 성공. 전체 백엔드 검증은 8분 12초 소요했다. 새 benchmark·브라우저 E2E와 운영 DB 대사는 실행하지 않았다.
- 최종 전체 검증 이후에는 진행 문서의 완료 상태·결과만 갱신했다. 실행 코드·테스트·golden fixture·HTTP/API 계약은 변경하지 않았다.

### 남은 범위

- 고정 writer 결과의 Map 경계는 이번 범위에서 이식했다. BE-012의 공통 명령 `payload` Object 및 구조 변경/상세/수량/계보 JSON reader의 중복·format별 precedence는 남는다. 현장 동기화 snapshot 계약은 JSON으로 유지하며 Farm snapshot을 Work 모델에 복제하지 않았다.
- 타입 유지가 handler와 payload/result의 모든 조합을 compiler로 검증한다는 뜻은 아니다. BE-014의 유형/handler/효과 분류 대응과 BE-013의 version·과거 지문 corpus·rolling writer 정책은 별도다. 기존 이력을 추정해 바꾸거나 누락된 field를 현재 값으로 채우지 않는다.

## 18차 변경 — BE-012 저장 효과 JSON reader 통합

작업일: 2026-10-04. 상태: 공통 효과 reader·형식별 호환·query-count 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- 고정 writer의 타입 유지 이후에도 대상 응답·상세 응답·계보·수량 보정이 같은 저장 JSON을 각자 읽었다. 중복 제거 과정에서 대상의 합집합을 상세의 우선순위로 바꾸거나 문자열 ID를 계보의 수량 사실에 포함하면 과거 응답과 보정 의미가 달라진다. 이 차이가 기존 버그라는 재현은 없으므로 정책 차이를 유지한다.
- `WorkEffectJsonCodec`에 조회 없는 해석을 모은다. 대상은 숫자 ID의 순서 있는 합집합·중복 제거, 상세는 문자열 ID도 허용하는 첫 유효 목록·중복 보존을 사용한다. 상세의 연결 행 fallback·적용 결과 행 우선순위·위치/품종 참조 조립은 기존 응답 조립 측에 유지한다.
- 계보·보정은 숫자 ID/수량만 인정한다. 일부 유효한 결과 행이 있으면 구형 합식의 총량 fallback을 사용하지 않으며, 같은 ID의 마지막 수량과 처음 등장 순서를 유지한다. 투입 수량 object의 잘못된 키·수량은 기존처럼 예외를 발생시킨다.
- 구형 포트의 생성 ID/요청 결과는 위치별로 대응시킨다. 크기 불일치·빠진 행·문자열 ID/수량·중복 ID에서는 부분 수량을 채택하지 않는다. 상세의 빈 행 압축 규칙을 이 경로에 공유하지 않는다. 생성 ID 목록이 없으면 command JSON을 읽지 않는 기존 단락 평가도 유지한다.
- writer·저장 JSON·효과 지문·HTTP/OpenAPI·DB schema·트랜잭션·잠금 순서는 변경하지 않는다. codec은 Entity·Repository·현재 Farm 상태를 읽지 않으며 저장 값을 보정하거나 새 공통 handler framework를 도입하지 않는다.

### 회귀 방어

- reader JSON fixture 10종으로 서로 다른 대상/상세 ID 의미와 결과 수량을 동시에 고정한다. 대상 응답의 실제 `from` 경로도 같은 fixture로 검증한다. 구형 합식 fallback·부분/중복 결과·문자열/소수·null/잘못된 행을 포함한다. 운영 이력에서 추출한 corpus는 아니다.
- 신규 codec 시험 21건, 수량 보정 service 시험 2건. 포트 위치별 수량·부분/중복 거절·저장 결과 우선·누락된 command·잘못된 투입 snapshot 거절과 null/순서/상세 행 압축을 검증한다.
- 기존 상세 전체 응답 golden에 혼합 ID 우선순위와 빈 행 압축 2종을 추가해 총 18종을 비교한다. 새 대상·상세·수량 service 회귀는 변경 전 구현에도 실행해 기존 consumer 동작과 기대값의 일치를 확인했다.
- PostgreSQL 상세 query-count에 1/10/50개 효과·서로 다른 참조 ID를 추가한다. 문자열 ID·우선순위·빈 행·미등록 품종/위치의 과거 표시를 실제 HTTP 경로에서 확인하면서 일괄 조회 비용을 고정한다. 기존 보정 개수 0/1/10/50 회귀도 유지한다.

### 검증

- 변경 전 consumer 구현으로 집중 4개 클래스 30건 성공. 신규 codec 자체 시험은 별도로 추가된 구현을 검증하며 모든 reader를 변경 전 구현과 대조했다는 뜻은 아니다.
- 최종 집중 단위/integration 5개 클래스 32건·PostgreSQL 3개 클래스 14건, 총 46건 성공. 계보·수량 보정·이동의 기존 회귀와 신규 호환 reader를 포함한다. 집중 검증은 56초 소요했다.
- 실제 PostgreSQL 상세 효과 1/10/50개에서 쿼리 6개, 보정 0/1/10/50개에서 4개를 확인했다. 이 범위를 넘어서는 모든 조회의 비용을 증명한 것은 아니다.
- 백엔드 전체 `./gradlew test`: 125개 클래스, 625건 성공. 실패·오류·생략은 없다. 기존 architecture·query-count·도메인·integration 회귀를 포함한다.
- PostgreSQL 전체 `./gradlew workE2eTest`: 56개 클래스, 546건 성공. 기존 rollback·동시성·취소·멱등성·수량/금액·DB 제약 회귀를 포함하며 실패·오류·생략은 없다.
- 프론트엔드 전체 `npm run check`, `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 백엔드 전체 검증은 8분 32초 소요했다. 새 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 최종 전체 검증 이후에는 진행 문서의 완료 상태·검증 결과만 갱신했다. 실행 코드·테스트·fixture·HTTP/API 계약은 변경하지 않았다.

### 남은 범위

- BE-012의 공통 `WorkEffectCommand.payload` Object, 구형 구조 변경 입력의 HTTP DTO 복원은 남는다. 보정 감사 이벤트의 수량 수지 fold와 현장 동기화 snapshot은 이번 공통 효과 reader 범위에 포함하지 않는다. BE-013의 저장 version·과거 지문 corpus·rolling writer 정책도 별도다.

## 19차 변경 — BE-012 공통 명령 타입과 구형 입력 경계

작업일: 2026-10-04. 상태: 공통 명령·구형 입력/receipt 호환·rollback 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- 공통 `WorkEffectCommand`와 즉시 기록 service의 payload가 Object여서 HTTP DTO·문자열 등 임의의 값도 실행 계약에 들어갈 수 있었다. 구형 분갈이·분주·합식은 handler에서 HTTP DTO로 JSON을 복원했고 현장 동기화 handler도 HTTP DTO를 요구했다.
- 기존 구조 변경·포트와 구형 분갈이·현장 동기화 application 값을 `WorkEffectPayload`로 제한한다. 공통 command·즉시 기록·접수 값 및 `payloadAs`에 이 제한을 적용한다. 이 과정에서 기존 processor 시험의 문자열 payload가 컴파일되지 않아 실제 application 명령을 전달하도록 교체했다.
- 구형 분갈이 API는 Farm의 입구 mapper에서 `LegacyRepotCommand`로 변환한다. 효과 handler는 application 값과 기존 그룹 상속 정책을 사용하며 구형 대상 완료의 Map도 같은 값으로 해석한다. 합식의 저장 형식은 호환 mapper의 전용 값으로 해석한다. handler에서 HTTP DTO 복원·소비를 제거하고 root unknown 허용/중첩 unknown 거절 의미를 유지한다.
- 현장 동기화도 application 값으로 전달한다. 기존 Mutation 생성과 before/after snapshot을 유지하며 HTTP endpoint의 `FEATURE_ON_HOLD` 정책을 활성화하지 않는다. 이미 적용한 입고의 이력 저장에서는 사용하지 않는 request payload를 제거한다. 입고 효과의 저장 JSON과 지문은 기존 details를 계속 사용한다.
- 구형 분갈이·현장 동기화의 전체 요청 metadata는 기존 즉시 기록 receipt 지문의 일부다. 값에 title·memo·key 등 호환 필드를 보존하여 간소화된 구조 변경 명령으로 바꿨을 때의 과거 receipt 충돌을 막는다. 날짜 배열 표현·null·원문 공백·분갈이 key trim·상속 ID 정렬·결과 배열 순서를 유지하며 새로운 지문 version·schema·migration·HTTP 계약은 추가하지 않는다.
- 허용 명령의 집합을 제한해도 handler/명령의 모든 조합을 컴파일러가 보장하지는 않는다. 명령별 기존 runtime 검사·구형 fallback을 유지한다. BE-014의 유형/handler/효과 대응은 별도이며 범용 handler framework를 도입하지 않는다.

### 회귀 방어

- 구형 분갈이 JSON/fingerprint fixture 4종: 전체 입력·optional 누락·unknown root·배열 날짜/null 상속. 과거 HTTP DTO와 새 application 값의 전체 직렬화와 고정 SHA-256을 비교하고 구형 Map에서 동일 값으로 복원함을 검증한다. 이 자료는 저장 계약의 시험 fixture이며 운영 요청 corpus는 아니다.
- 현장 동기화 service가 전달한 application 값과 과거 DTO의 JSON/고정 지문·원문 공백을 비교한다. handler의 관측 값·업무일·사유·전후 snapshot/Mutation link 및 다른 명령을 DB 조회 전에 거절하는 경로를 검증한다.
- 기존 분갈이/분주/합식 integration·상속·부분 수량·processor·포트 회귀를 실행한다. 구형 reader의 root/중첩 unknown 정책과 typed 입력이 별도 persistence Map을 다시 읽지 않는 경로도 보호한다.
- PostgreSQL에 과거 HTTP DTO로 계산한 receipt 지문을 비교/재설정하고 새 실행의 동일 요청 replay·metadata 변경 거절·원래 효과 JSON 보존을 확인한다. 현장 동기화 service의 효과 DB 제약 실패는 수량·Mutation·작업/대상/실행·receipt를 모두 rollback하고 같은 key로 재시도한다. HTTP 보류 정책도 별도로 확인한다.

### 검증

- 최종 집중 단위/integration/architecture 7개 클래스 47건·PostgreSQL 3개 클래스 24건, 총 71건 성공. 분갈이/분주/합식·상속·포트·병렬 즉시 기록·rollback·구형 접수 지문·보류 API를 포함한다. 집중 검증은 1분 7초 소요했다.
- 새 PostgreSQL 시험의 오류 메시지 기대와 baseline Mutation 0건 가정을 수정했다. 충돌은 안정적인 error code로, rollback은 사전에 존재한 원장·감사 행 및 묶음 전체 상태와 비교한다. 제품의 rollback 누락으로 판정하지 않는다.
- `python3 scripts/generate_openapi.py`: 성공. 재생성한 전체 명세·slice에 diff가 없다. 공개 요청/응답 schema·enum·capability가 유지되어 TypeScript 타입 재생성은 필요하지 않다.
- 백엔드 전체 `./gradlew test`: 127개 클래스, 634건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함하며 실패·오류·생략은 없다.
- PostgreSQL 전체 `./gradlew workE2eTest`: 56개 클래스, 549건 성공. 기존 rollback·동시성·취소·멱등성·수량/금액·DB 제약 회귀를 포함하며 실패·오류·생략은 없다.
- 프론트엔드 전체 `npm run check`, `./gradlew spotlessCheck`, `git diff --check`: 성공. 최종 백엔드 전체 검증은 8분 30초 소요했다. 새 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 최종 전체 검증 이후에는 진행 문서의 완료 상태·검증 결과만 갱신했다. 실행 코드·테스트·fixture·HTTP/API 계약은 변경하지 않았다.

### 남은 범위

- BE-012의 감사 권고 중 기존 typed command/result 유지와 저장/호환 JSON 경계 정리(16~19차)는 완료 범위로 삼는다. 공통 Object/handler HTTP DTO 경계를 이식했으며 자유 입력·snapshot JSON은 명시적인 계약으로 유지한다. 보정 감사 이벤트의 수량 수지 fold·BE-013 저장 version/과거 지문 corpus·BE-014 handler 대응은 후속 범위다. 운영 DB의 기존 요청을 추정해 보정하지 않는다.

## 20차 변경 — BE-013 Mutation v1 지문과 스냅샷 계약

작업일: 2026-10-04. 상태: Mutation v1 지문·schema guard·snapshot/replay 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- Mutation의 최상위 지문 payload는 명시적이었지만 details·입고 항목·변환 원본/결과·이동/수량/보정 항목·원인 Mutation은 현재 application record를 직접 직렬화했다. 중첩 record에 nullable 필드를 추가해도 기존 저장 hash가 바뀔 수 있었다.
- 중첩 값까지 기존 v1 JSON shape의 private projection으로 복사한다. 기존 정규화·null·배열 순서·ID 정렬·숫자 scale·조건부 필드 생략을 유지하고 지문 version marker를 새로 쓰거나 과거 hash를 재계산하지 않는다. 의미 있는 신규 필드를 v1에서 무시하는 확장 정책은 허용하지 않는다.
- 명령 17종·중첩 application 값 8종·snapshot 1종의 필드 집합을 HEAD 기준 fixture로 고정한다. field 추가/삭제는 기존 golden hash가 통과하더라도 별도 계약 시험에서 실패한다. 선언 순서와 private projection 구현은 검사하지 않는다. 이 검사는 영속 계약 변경 검토를 요구하기 위한 의도적인 schema guard다.
- Snapshot writer/reader·head equality·Farm/Work 지문 알고리즘은 변경하지 않는다. 누락과 null을 현재 값·0·false로 채우지 않으며 canonical 위치의 소수 둘째 자리/초과 정밀도 거절을 회귀로 고정한다. 현재 v1은 V20 cutover 이후 형식이며 이전 실험 원장의 호환 보증이 아니다.
- 구현 기준과 배포 문서에 형식별 version/reader-first 준비·구형 writer 병행·rollback 조건을 추가한다. coverage engine/snapshot version과 manifest version·writer version을 구분하며 현재 최소 writer 검사가 Work·Sales 전체를 차단한다고 가정하지 않는다.

### 회귀 방어

- 기존 명령 17종의 고정 hash fixture는 변경하지 않았다. 보정 원인이 있는 생성 취소·현재 Mutation을 원인으로 한 보정·부분 변환의 해제 위치도 기존 payload 의미와 비교한다.
- Snapshot golden 4종은 완전한 숫자 입력·문자열 소수 위치·optional 누락·명시적 null의 저장 JSON/고정 SHA-256/canonical roundtrip을 보호한다. 미상 값과 0/false가 같은 head/hash가 되지 않으며 unknown field와 손실성 canonical 반올림을 거절하는지 검증한다.
- PostgreSQL 신규 2건: 과거 직접 record 직렬화로 계산한 지문을 저장/재설정하고, 이후 현재 수량이 바뀐 뒤에도 원래 Mutation/Entry/snapshot을 replay한다. 같은 key의 메모 변경은 충돌하며 Mutation/Entry가 늘지 않는다. 실제 Hibernate jsonb reader는 누락 필드를 null로 보존하고 현재 값으로 복원하지 않는다. sparse JSON은 시험에서 구성한 계약 자료이며 해당 자료를 정상 ACTIVE head로 승인하는 시험이 아니다.
- 집중 단위/architecture 3개 클래스 17건·PostgreSQL 3개 클래스 13건, 총 30건 성공. 기존 state-chain migration·대사·병렬 수량·write fence 회귀를 포함하며 38초 소요했다.

### 검증

- 백엔드 전체 `./gradlew test`: 128개 클래스 640건 성공. 기존 architecture·query-count·도메인·integration 회귀를 포함하며 실패·오류·생략은 없다.
- PostgreSQL 전체 `./gradlew workE2eTest`: 56개 클래스 551건 성공. 기존 rollback·동시성·멱등성·수량/금액·DB 제약·state-chain 복구 회귀를 포함하며 실패·오류·생략은 없다.
- `./gradlew spotlessCheck`, 프론트엔드 `npm run check`, `git diff --check`: 성공. 최종 백엔드 전체 검증은 8분 34초 소요했다. 공개 API·DB schema 변경은 없어 Flyway/OpenAPI/생성 TypeScript 갱신은 없다. 새 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 최종 전체 검증 이후에는 진행 문서의 완료 상태·검증 결과만 갱신했다. 실행 코드·시험·fixture·HTTP/API 계약은 변경하지 않았다.

### 남은 범위

- BE-013은 부분 완료다. Work·Sales·Auction 등 다른 저장 지문/응답의 형식별 projection·version 선택, 실제 신규 속성의 구형/신형 reader와 head/복원 migration, 구버전 writer 병행·rollback 실행 시험 및 운영 과거 요청 corpus 대사가 남는다. 이번 변경은 새 속성·version dispatcher·운영 backfill을 도입하지 않는다.

## 21차 변경 — BE-013 Sales·일반 Work 생성 지문

작업일: 2026-10-04. 상태: 생성 지문 projection·호환 fixture·구형 접수 replay·정책 문서 및 최종 검증 완료.

### 원인과 범위

- Sales 생성 접수는 현재 명령·품목·배분 record를 직접 직렬화했고, 일반 Work 단건/품종별 일괄 계획·완료 기록은 HTTP DTO 전체를 해시했다. 선택 필드 추가가 과거 receipt 지문에 자동 반영될 수 있었다.
- 두 모듈에 기존 v1 필드를 명시하는 접수 지문 전용 값을 둔다. Sales 중첩 품목·배분도 복사하며 Work 일괄 요청의 operation wrapper와 자유 details 전체를 보존한다. 이 값은 실행 명령·HTTP DTO·domain validation의 대체물이 아니다.
- Sales의 날짜 문자열·정렬된 record property·숫자 표현과 Work의 날짜 배열·객체 키 정렬·숫자 정규화를 그대로 사용한다. null/default·원문 문자열·대상/제외/배분 배열 순서를 임의 정규화하지 않는다. null collection/element를 projection 단계에서 새로 거절하지 않으며 기존 업무 검증을 유지한다.
- Sales 3개 요청 record·일반 Work 2개 DTO의 필드 집합을 HEAD 기준 fixture로 고정한다. 새 의미 있는 필드를 v1에 누락시킨 채 fixture만 갱신하지 않으며, 20차에서 정한 version/판별·writer 배포 정책을 적용한다. DB 접수·key 범위·완료 응답·membership·트랜잭션·잠금·HTTP 계약은 변경하지 않는다.

### 회귀 방어

- 제품 코드 변경 전에 기존 serializer로 baseline golden 회귀 4건을 먼저 통과시켰다. 최종 Sales 5종·Work 5종 자료는 full/null/default/empty·배열 순서·경매와 선택 metadata의 고정 SHA-256을 비교한다. Work의 단건과 일괄 wrapper를 각각 보호하며 전체 직렬화도 기존 DTO/명령과 비교한다. 운영 요청 corpus로 간주하지 않는다.
- 자유 details의 미상 추가 key·명시적 null·숫자 scale·중첩 객체 순서가 기존 비교 의미를 유지한다. Sales invalid null collection/element도 기존 hash 의미를 유지하며 projection에 validation을 중복하지 않는다.
- PostgreSQL 신규 7건: 직접 기존 요청 직렬화로 구한 hash를 저장/재설정한 뒤 일반·경매의 작성중/완료 판매 4조합을 취소하고 거래처를 비활성화해도 최초 응답을 replay한다. 일반 Work 3경로도 현재 제목·유형이 바뀐 뒤 최초 응답을 replay한다. 같은 키의 다른 메모/제목은 안정적 conflict code로 거절하고 접수·수량·Mutation·업무 행·membership·감사 전체를 보존한다.
- 첫 PostgreSQL 실행에서 새 시험 SQL의 Work 접수 키 컬럼명과 Sales jsonb cast를 수정했다. 제품의 지문·rollback 결함으로 판정하지 않는다.

### 검증

- 최종 집중 단위/architecture 4개 클래스 17건·PostgreSQL 2개 클래스 88건, 총 105건 성공. 기존 병렬 생성·rollback·snapshot·query-count·key 범위 회귀를 포함하며 46초 소요했다.
- 백엔드 전체 `./gradlew test`: 130개 클래스 646건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 56개 클래스 558건 성공. 두 task 모두 실패·오류·생략은 없다. 최종 전체 검증은 8분 46초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. 공개 API·DB schema 변경은 없어 Flyway/OpenAPI/생성 TypeScript 갱신은 없다. 새 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증에 포함된 Work 계약 시험의 JSON 비교를 객체 필드 선언 순서에 의존하지 않도록 보완했다. 고정 지문·배열 순서·원문·null 비교는 유지한다. 보완한 시험의 재컴파일·집중 3건과 spotlessCheck가 6초 내 성공했다. 제품 코드·PostgreSQL 시험·fixture는 전체 검증 시작 이후 변경하지 않았으며 이후에는 진행 문서만 갱신했다.

### 남은 범위

- BE-013은 부분 완료다. 일반 생성 이외 Work 실행·구조 기록·포트·취소·보정·효과와 Farm 입고·Auction 접수의 형식 고정, 저장 응답의 필드 확장, 실제 신규 형식의 version dispatcher/upgrade와 구버전 writer 병행·rollback 실행 시험, 운영 과거 요청 corpus 검증은 남는다. 기존 응답 reader와 저장 자료를 추정 보정하지 않는다.

## 22차 변경 — BE-014 구조 변경 효과와 계보 분류 계약

작업일: 2026-10-04. 상태: 분류 계약·strategy 연결·rollback/replay 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- Work 계보 조회가 저장 handler를 현재 WorkType code로 해석했고, Farm 조회는 실행 strategy와 별도의 문자열 switch로 관계를 선택했다. handler와 작업 유형 이름이 다르면 계보에서 누락될 수 있었고 신규 구조 정의가 실행돼도 조회의 switch가 빠질 수 있었다.
- Work 정의에 저장 구조 handler 해석 계약을 분리한다. 현재 구조 정의의 이름은 자동 연결하며 이동의 역사적인 `MOVE`·`MOVEMENT` 이름은 같은 정의로 연결한다. 일반 작업 유형 해석·template fallback·effectKind는 바꾸지 않는다. 신규 정의/저장 이름은 이 계약으로 확장하며 새 enum/범용 handler framework를 추가하지 않는다.
- Work 계보 application 값이 저장 handler에서 해석한 구조 정의를 제공한다. Farm은 그 정의에 대응하는 기존 strategy의 `lineageType`을 사용한다. 관계의 두 번째 switch를 제거하고 strategy의 계보 관계 누락도 기동 시 거절한다. 현재 Entity metadata에서 과거 관계를 재구성하지 않는다.
- 새 구조 실행 결과의 저장 handler가 해당 정의와 일치하지 않으면 효과 저장 전에 실패한다. 앞선 Farm 변경은 최상위 트랜잭션에서 rollback한다. 기존 효과 replay는 현재 handler를 실행하거나 새 검사를 거쳐 다시 저장하지 않는다. 기존 target 효과·회차 key/원본 행이 있는 효과의 조회 범위, 취소 이력 보존, DB/JSON 지문과 잠금 순서는 유지한다.

### 회귀 방어

- 저장 이름 5종과 비구조/unknown/null 분류, 현재 WorkType 이름 해석의 독립성을 확인한다. 기존 capability·template·system/active 조합 회귀도 실행한다.
- 네 실행 strategy와 저장 이름 5종의 계보 관계를 대조하며 필수 strategy/계보 관계 누락을 기동 전에 거절한다. Processor는 두 이동 이름을 받아도 ATTRIBUTE_CHANGE를 바꾸지 않으며 unknown/다른 구조/기록 전용 결과는 저장 전에 거절한다.
- Work 조회는 효과 1/20건 모두 두 번의 Repository 일괄 호출만 수행하고 현재 WorkType을 읽지 않는다. 이는 Repository 호출 회귀이며 실제 SQL query-count benchmark로 주장하지 않는다. 일반 target 효과와 원본 행이 있는 구형 operation 효과의 포함/제외 및 수량을 보존한다.
- PostgreSQL 신규 7건: 실제 seed의 이동/분갈이/분주/합식과 이동의 다른 저장 이름을 실행→상세/양방향 계보→취소→실행·취소 replay까지 확인한다. 작업 이름·활성이 바뀌어도 저장 분류를 보존하고 replay가 업무 행을 늘리지 않는다. 실제 Mutation 적용 후 잘못된 결과 이름을 주입한 2건은 난 묶음·원장·계보·Work/효과·접수·감사를 모두 rollback하고 같은 실행 key로 재시도한다.
- 최초 PostgreSQL 시험에서 기존 seed 유형 중복 생성·비활성 상태 잔존·Hibernate 할당 중 sequence 재시작을 수정했다. 신규 클래스는 기존 seed를 재사용하고 sequence를 유지한다. 해당 fixture 실패를 제품의 분류·rollback 결함으로 판정하지 않는다.

### 검증

- 집중 단위/integration/architecture 6개 클래스 54건·PostgreSQL 2개 클래스 11건, 총 65건 성공. PostgreSQL은 41초 소요했으며 기존 구조 실행·수량/이력·병렬 완료 회귀도 포함한다.
- `python3 scripts/generate_openapi.py`: 성공. 전체 명세·slice에 diff가 없다. 공개 요청/응답·enum·capability와 생성 타입 계약은 유지하므로 TypeScript 타입 재생성은 필요하지 않다.
- 백엔드 전체 `./gradlew test`: 131개 클래스 662건 성공. 기존 architecture·capability·지문·query-count·integration 회귀를 포함하며 실패·오류·생략은 없다.
- PostgreSQL 전체 `./gradlew workE2eTest`: 57개 클래스 565건 성공. 기존 수량/금액·DB 제약·병렬 실행·취소·rollback·멱등성 회귀를 포함하며 실패·오류·생략은 없다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. 전체 백엔드 검증은 8분 47초 소요했다. DB schema 변경은 없어 Flyway는 추가하지 않았다. 새 benchmark·브라우저 E2E·운영 unknown handler 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·저장/HTTP 계약은 변경하지 않았다.

### 남은 범위

- BE-014의 현재 분류 중복과 저장 이름/유형 결합은 이번 범위로 완료한다. 운영의 unknown 저장 code 유무와 복구는 대사하지 않았고 임의 backfill하지 않는다. 미래의 새 유형은 동일 등록·저장 이름·strategy·전체 흐름 회귀를 추가해야 하며 모든 미래 확장을 자동 구현하는 계약은 아니다. 일반 사용자 기록 template나 보류 기능을 새로 활성화하지 않는다.

## 23차 변경 — BE-015 구조 변경 기록의 중간 상세 조회 제거

작업일: 2026-10-04. 상태: 구조 변경 단건·배치 기록 수정·query-count/rollback/replay 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- 기존 Work benchmark는 목록·상세·이력 GET의 query count와 응답 시간을 측정한다. 일반 완료 기록의 120 target query-count 회귀도 확인했지만, 구조 변경 기록의 계획→시작→실행→최종 응답 경로는 포함하지 않는다. 기존 검증의 통과를 이 쓰기 경로의 조회 비용 보장으로 취급하지 않는다.
- 구조 기록의 계획은 Work 내부 aggregate를, 실행은 완료 상태를 가진 같은 모듈의 aggregate를 반환한다. 시작은 상태 전이만 수행하고 기록은 ID만 모아 최종 상세를 한 번 조립한다. Entity를 다른 모듈에 공개하거나 새 반환 wrapper/framework를 추가하지 않는다. 단독 계획·시작·실행 API의 상세 응답은 유지한다.
- 내부 쓰기는 호출 트랜잭션을 필수로 요구한다. 최상위 기록 트랜잭션, Receipt → 전체 원본 → 현재/결과 구역 → 새 Work 잠금 순서, source 전체 수량 검증, 실행 잠금과 replay 지문 검증, 실행 후 전체 대상 완료 확인은 유지한다. 효과·대상·Mutation·계보·감사와 역사적 snapshot 생성도 기존 쓰기 지점에서 수행한다.
- 배치의 남은 원본 제외 집합을 입력 순서대로 갱신하고 Receipt의 ID 순서를 최종 응답에 유지한다. 완료 접수는 원본이 비활성화됐어도 새 기록 잠금을 취득하지 않는다. ID 기반 replay는 현재 상세를 반환하는 기존 계약이며 일반 Work 생성의 최초 응답 snapshot 계약으로 바꾸지 않는다.

### 측정과 회귀 방어

- 제품 수정 전에 PostgreSQL 신규 시험 5건을 기존 구현에서 통과시켰다. 실제 기록 1건·8건의 HTTP 쓰기를 측정하며 Fixture의 baseline 원장을 만든 뒤 통계를 초기화한다. 시각은 주입된 Clock을 고정하여 DB의 microsecond 반올림과 JVM nanosecond 표현 차이로 응답 비교가 실패하지 않게 했다. 제품의 시각 정책은 바꾸지 않는다.
- 상세 보정 집계와 상세 target 일괄 조회는 1건에서 각각 4→1회, 8건에서 각각 25→1회다. 중간 조립 3회 × 기록 수를 제거하고 최종 일괄 조회만 남긴다. 실제 Hibernate query 실행 횟수를 검사하며 service mock 호출 횟수로 대체하지 않는다.
- 같은 고정 시각·seed·요청에서 전체 prepared statement는 1건 117→81회, 8건 958→586회다. 이 값은 SELECT뿐 아니라 flush의 DML·sequence 접근도 포함하므로 제거한 조회 개수나 운영 지연 개선율로 해석하지 않는다. 전체 쓰기는 기록 건수에 따라 증가하며 일정한 query count를 주장하지 않는다. 측정 실행마다 `backend/build/work-query-count/structure-record-{1,8}.json`에 전체 prepared statement와 Hibernate query별 실행 횟수를 생성한다. 이 자료는 완전한 native SQL trace나 응답 시간 benchmark가 아니다.
- 신규 5건은 1/8건의 상세 조회 상한·완료 진행률·입력 역순 보존·별도 상세 GET 비교·replay 응답/업무 행 불변성, 호환 단건 기록, 미지원 유형의 계획/접수 rollback, 최종 응답 실패 후 전체 rollback·동일 key 재시도를 보호한다. fault injection은 최종 응답 단계에만 사용하고 실제 수량·Mutation·Work 쓰기를 실행한다.
- 기존 PostgreSQL 구조 기록/Farm 경쟁·역순 구역 잠금·원본 변경 후 재검증·후행 실패 rollback·같은 key 대기/재시도·비활성 원본 replay 및 구조 실행 회귀를 함께 실행했다. 기존 이동의 연계 폐기·분갈이/분주/합식·단건 조회 계약 integration과 architecture 회귀도 포함한다.

### 검증

- 수정 전 PostgreSQL 신규 5건 성공. 최초 실행의 신규 응답 비교 실패는 고정 Clock fixture로 수정한 뒤 제품 변경 전에 재검증했다.
- 집중 일반/integration/architecture 5개 클래스 32건·PostgreSQL 3개 클래스 27건, 총 59건 성공. 실패·오류·생략은 없다. 1분 10초 소요했다.
- 백엔드 전체 `./gradlew test`: 131개 클래스 662건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 58개 클래스 570건 성공. 실패·오류·생략은 없다. 전체 백엔드 검증은 9분 4초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. `python3 scripts/generate_openapi.py`도 성공했고 전체 명세·slice와 생성 타입에 diff가 없다. 공개 API·DB schema 변경이 없어 TypeScript 재생성과 Flyway 추가는 필요하지 않다. 기존 응답 시간 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-015는 부분 완료다. 입고 포트의 중간 target/progress 응답과 중복 최종 조회, 이동에 연결된 폐기 기록의 응답 조립, Sales의 재잠금·snapshot/최종 조회, 정산 snapshot과 현재 표시 참조 조회는 별도 경로 분석·회귀가 남는다. 이 중 잠금 후 재확인과 역사적 snapshot 보존에 필요한 조회는 비용만으로 제거하지 않는다. 운영 잠금 대기·지연·대규모 쓰기 benchmark는 미측정이다.

## 24차 변경 — BE-015 입고 포트의 중간 상세 응답과 중복 완료 제거

작업일: 2026-10-04. 상태: 포트 신규/활성 계획·단독 실행·기록 수정·query-count/snapshot/rollback/replay 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- 기존 Work benchmark와 일반 완료 기록의 query-count 검사는 포트 쓰기를 측정하지 않는다. 23차 구조 기록의 회귀도 포트 대상·입고 갱신을 포함하지 않는다. 기존 포트 JSON/지문·배치/취소·형제 입고 경쟁 회귀를 먼저 확인하고 신규 PostgreSQL 18건을 제품 수정 전 구현에서 통과시켰다.
- 신규 단건·품종별 포트 계획은 Work 내부 aggregate/ID를 반환하는 쓰기 경로와 외부 상세 응답 조립을 분리한다. 기존 단독 계획 API는 최종 상세를 조립한다. 내부 생성·재개·대상 완료·배치 기록은 호출 트랜잭션을 필수로 요구하며 새 공통 framework나 반환 wrapper를 추가하지 않는다.
- 포트 기록은 기존 효과와 새 실행의 작업 ID를 입력 순서대로 중복 제거해 최상위 Receipt에 전달한다. request별 시작/재개/현재 상세·대상 완료 상세와 하위 service의 마지막 getAll을 제거하고 최상위 기록이 최종 상세를 한 번 읽는다. 단독 포트 실행도 기존 Receipt/효과 검증 후 마지막 상세만 읽는다.
- 전체 완료는 이미 대상 완료에서 수행하는 공통 종료 정책을 사용한다. 상세 DTO의 pending/inProgress/partial/failed 집계를 다시 읽고 완료 API를 재호출하지 않는다. 실패·부분 완료 대상은 완료하지 않고, 건너뜀·취소 대상은 기존 종료 정책을 유지한다. 같은 계획의 첫 실행이 상태를 바꾸면 후속 실행은 그 managed aggregate의 현재 상태로 처리한다.
- 연결 입고 ID 순 → 기존 Work root → 실행 root 잠금, 새 계획 생성 후 실행 대상 잠금 재조회, 대상 완료 직전 현재 입고의 수량·보관 위치 snapshot 갱신, 효과 key/지문 검증과 저장 시점은 유지한다. 효과·Farm 생성 Mutation·난 묶음·입고/Work 상태·감사·Receipt를 같은 최상위 트랜잭션에 확정하거나 rollback한다. 구형 `POTTING:<key>`와 현재 입고별 effect key, 일반 대상 완료 API의 replay 계약도 유지한다.

### 측정과 회귀 방어

| 실제 HTTP 실행 경로 | 상세 보정 집계 기존 → 수정 | 전체 prepared statement 기존 → 수정 |
| --- | --- | --- |
| 새 계획, 포트 1건 / 8건 | 6 / 20 → 각각 1 | 190 / 845 → 103 / 520 |
| 기존 PLANNED 계획, 1건 / 8건 | 5 / 19 → 각각 1 | 118 / 615 → 69 / 412 |
| 기존 PAUSED 계획, 1건 / 8건 | 5 / 19 → 각각 1 | 118 / 615 → 69 / 412 |
| 기존 IN_PROGRESS 계획, 1건 / 8건 | 5 / 19 → 각각 1 | 113 / 610 → 64 / 407 |
| 2품종·포트 8건의 신규 계획 | 21 → 1 | 891 → 534 |
| 호환 단독 실행·신규 계획 | 5 → 1 | 166 → 98 |

- 동일 seed·고정 Clock·요청에서 측정한다. 생성/실행의 prepared statement에는 SELECT 외에 flush DML·sequence 접근이 포함된다. 위 수치를 제거한 SELECT 수나 운영 응답 지연 개선율로 해석하지 않는다. 기록 건수에 따른 실제 쓰기와 현재 입고 재검증 비용은 남는다.
- 실행 시 `backend/build/work-query-count/inbound-potting-*.json`에 prepared statement와 Hibernate query별 실행 횟수를 생성한다. 실제 query 실행의 상세 집계를 1회로 검사하며 service mock 호출 횟수로 대체하지 않는다. 완전한 native SQL trace·응답 시간 benchmark는 아니다.
- 신규 18건은 네 계획 상태의 1/8건과 복수 품종, 단독 신규 실행, 별도 상세 조회/완료 Receipt replay의 내용·ID 순서·업무 행 불변성을 확인한다. 공유 계획은 첫 대상 완료 후 진행 상태와 변경된 입고 수량/위치 snapshot을 보존하고 일시정지 후 마지막 대상을 완료한다. 저장된 형제 실행의 실패·부분 완료·건너뜀·취소 상태도 기존 종료 판정을 유지한다.
- 구형 효과와 새 실행 혼합에서는 기존 작업을 중복 생성하지 않고 최초 등장 ID 순서를 유지한다. 구형 효과를 다른 내용으로 재사용하면 안정적인 conflict code로 거절한다. 후행 항목의 배치 검증 실패와 모든 포트 쓰기 후 최종 응답 실패는 입고·Work·효과·Mutation·난 묶음·접수·감사를 모두 rollback하며 같은 key로 재시도한다.
- 기존 snapshot의 `pottingDueDate`는 최초 HTTP의 ISO 문자열과 DB JSONB를 읽은 날짜 배열이 다를 수 있다. 새 시험은 최초 응답의 문자열 표현도 검사하고 이 필드에 한해 날짜 값으로 상세/replay를 대조한다. 제품 JSON·저장 지문을 정규화하지 않는다. 초기 시험의 이 표현 차이와 전체 효과 수에 입고 생성 효과를 포함했던 fixture assertion, assertion overload의 컴파일 오류는 제품 변경 전에 수정했다.

### 검증

- 수정 전 PostgreSQL 신규 18건 성공. 수정 후 집중 일반/integration/architecture 5개 클래스 35건·PostgreSQL 4개 클래스 38건, 총 73건 성공. 실패·오류·생략은 없고 1분 25초 소요했다.
- 기존 포트의 고정 JSON/지문·구형 effect key·generic 대상 완료/replay·DB 효과 저장 실패·수량/배치 rollback과 포트 취소·형제 입고 병렬 실행, 23차 구조 기록 query-count도 함께 실행했다.
- 백엔드 전체 `./gradlew test`: 131개 클래스 662건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 59개 클래스 588건 성공. 실패·오류·생략은 없다. 전체 백엔드 검증은 9분 14초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. `python3 scripts/generate_openapi.py`도 성공했고 전체 명세·slice와 생성 타입에 diff가 없다. 공개 API·DB schema 변경이 없어 TypeScript 재생성과 Flyway 추가는 필요하지 않다. 응답 시간 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-015는 부분 완료다. 이동에 연결된 폐기 기록, Sales의 재잠금과 예약/출고 snapshot·최종 조회, 정산 snapshot과 표시 참조 재조회는 남는다. 포트의 기존 효과 matching 메모리 순회, 실행별 필수 입고 재검증·전체 대상 완료 조회, 큰 batch 입력과 운영 잠금 대기는 이번 변경으로 일괄 최적화하거나 측정하지 않았다. 현재 상세를 반환하는 ID 기반 Receipt와 최초 생성 응답 snapshot을 혼합하지 않는다.

## 25차 변경 — BE-015 폐기 기록과 품종별 계획의 중간 상세 조회 제거

작업일: 2026-10-04. 상태: 일반 계획/폐기 내부 쓰기 경계 수정·query-count/연계 취소/rollback/replay 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- 기존 Work benchmark는 읽기 경로를, 일반 완료 기록 query-count 검사는 해당 쓰기 경로만 측정한다. 이동 연계 폐기의 계획·시작·대상별 완료·연결 후 상세 조립과 독립 폐기·품종별 계획의 반복 조회는 포함하지 않았다. 기존 이동/폐기 integration과 전체 대상 잠금·생성 Receipt 회귀를 확인하고 신규 PostgreSQL 16건을 수정 전 컴파일된 제품 코드에서 통과시켰다.
- 품종별 일반 계획은 내부 managed aggregate 목록을 반환하고 공개 API는 전체 생성 후 상세를 한 번 조립한다. 독립 폐기는 전체 계획 ID로 대상을 일괄 조회하고 내부 시작·대상 완료를 사용해 최종 상세만 조립한다. 반복문에 새 Repository 조회를 추가하지 않는다.
- 이동 연계 폐기는 외부용 폐기 응답을 생성하지 않고 이동 실행에 연결 작업 ID만 전달한다. 완료 후 부모 연결과 제목을 확정하는 시점은 유지하며 제목은 생성된 대상의 품종 snapshot을 사용한다. 현재 품종을 다시 조회하거나 새로운 반환 wrapper·framework를 도입하지 않는다.
- 내부 계획·대상 완료·연계 폐기는 호출 트랜잭션을 필수로 요구한다. 최상위 트랜잭션, 전체 대상/구역 선잠금, 실행 대상 잠금과 완료 직전 현재 상태 재검증, 효과 저장·감사·Mutation·수량 변경을 유지한다. 이동 효과 저장 후 대상 결과에 연결 폐기 ID를 넣는 기존 저장 경계도 유지한다.
- 수량의 입력 비례 배분·최대 나머지와 낮은 ID 동률 우선, 품종별 작업 분할과 대상 순서, 연결 폐기 단독 취소 거절과 부모 공동 취소를 유지한다. 일반 생성 Receipt는 최초 응답 snapshot, 구조 실행 Receipt는 기존 ID 기반 replay 계약을 유지한다. 독립 폐기 endpoint에 새 멱등 계약을 추가하지 않는다.

### 측정과 회귀 방어

| 실제 HTTP 실행 경로 | 상세 보정 집계 기존 → 수정 | 전체 prepared statement 기존 → 수정 |
| --- | --- | --- |
| 전체 이동 + 연계 폐기, 1 / 8 대상 | 5 / 12 → 각각 1 | 185 / 562 → 131 / 396 |
| 부분 이동 + 연계 폐기, 1 / 8 대상 | 5 / 12 → 각각 1 | 151 / 512 → 97 / 346 |
| 독립 폐기, 1 / 8 대상 | 3 / 10 → 각각 1 | 77 / 427 → 60 / 298 |
| 2품종 독립 폐기, 8 대상 | 12 → 1 | 472 → 314 |
| 공개 2품종 계획, 8 대상 | 2 → 1 | 46 → 33 |

- 고정 Clock·같은 seed와 HTTP 요청으로 1/8 대상, 전체/부분 이동, 독립 폐기·복수 품종과 공개 계획을 비교한다. `backend/build/work-query-count/discard-record-*.json`에 전체 prepared statement와 Hibernate query별 실행 횟수를 기록하고 상세 보정 집계의 실제 실행을 한 번으로 검사한다. mock 호출 횟수로 query-count를 대체하지 않는다.
- prepared statement에는 SELECT 외 flush DML·sequence 접근이 포함되며 ID allocation 상태에 따라 소수 차이가 날 수 있다. 운영 응답 시간·잠금 대기 개선율이나 완전한 native SQL trace로 해석하지 않는다. 실제 실행별 쓰기·잠금 재검증·전체 대상 종료 확인은 남는다.
- 신규 회귀는 이동 전체/부분 완료의 연결·제목·수량·독립 상세·동일 요청 replay·단독 취소 거절·공동 취소 후 replay, 폐기 없음과 배분 동률, 독립 폐기의 품종 분할과 결과 정규화, 공개 품종별 계획의 현재 제목 변경 후 최초 snapshot replay를 확인한다.
- 연결 폐기 효과 DB CHECK 실패와 이동/복수 품종 폐기의 최종 응답 실패는 모든 Work·효과·Mutation·난 묶음·계보·Receipt·감사 행의 rollback 및 재시도를 검사한다. 후행 독립 폐기 수량 초과와 중복/누락 결과·다른 작업 유형도 선행 쓰기를 rollback한다. 기존 전체 대상 잠금 경쟁과 Receipt 병렬/rollback, 구조 기록·포트 query-count 회귀도 함께 검증한다.
- 초기 시험의 JDBC JSONB `?` 연산자 바인딩과 제목 endpoint의 HTTP method 오류, 단독 취소 거절 status 추측은 테스트에서 바로잡았다. 제품의 오류 응답·취소 정책은 변경하지 않았다.

### 검증

- 수정 전 PostgreSQL 신규 16건 성공. 수정 후 집중 일반/integration/architecture 6개 클래스 47건·PostgreSQL 5개 클래스 109건, 총 156건 성공. 실패·오류·생략은 없고 1분 39초 소요했다. 이후 복수 품종 독립 폐기의 최종 응답 실패 rollback 1건을 추가해 신규 회귀는 17건이다.
- 백엔드 전체 `./gradlew test`: 131개 클래스 662건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 60개 클래스 605건 성공. 신규 17건을 포함해 실패·오류·생략은 없고 전체 백엔드 검증은 9분 18초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. `python3 scripts/generate_openapi.py`도 성공했고 전체 명세·slice와 생성 타입에 diff가 없다. 공개 API·DB schema 변경이 없어 TypeScript 재생성과 Flyway 추가는 필요하지 않다. 응답 시간 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-015는 부분 완료다. Sales의 재잠금과 예약/출고 snapshot·최종 조회, 정산 snapshot과 표시 참조 조회는 후속 분석·회귀가 남는다. 필요 재검증과 과거 snapshot 보존에 쓰이는 조회는 비용만으로 제거하지 않는다. 큰 입력, 품종 분할의 기존 메모리 필터링과 운영 잠금 대기·지연 benchmark는 이번 범위에 포함하지 않는다.

## 26차 변경 — BE-015 판매 쓰기의 배분·스냅샷 N+1과 수정 후 재조회 제거

작업일: 2026-10-04. 상태: 판매 소유 aggregate 일괄 로딩·중복 재조회 제거·query-count/snapshot/replay/rollback 회귀·정책 문서 및 최종 전체 검증 완료.

### 원인과 범위

- 기존 일반 상세 query-count는 1/10/50 품목의 조회를, PostgreSQL 판매 생성 접수 회귀는 이미 성공한 생성의 replay만 보호한다. 검색 benchmark는 목록을, Work benchmark는 Work 읽기를 측정한다. 판매 수정·출고·취소·입금·현재 상태 반환의 상세 조립과 기존 배분 삭제 cascade는 별도 검사가 없었다. 관련 시험을 먼저 확인하고 신규 PostgreSQL 19건을 제품 수정 전 통과시켰다.
- 쓰기는 전표 root를 잠근 뒤 품목마다 배분, 배분마다 역사 snapshot collection을 lazy loading했다. 품목당 2개 배분 fixture에서 collection fetch는 1/8 품목에 4/25회다. 목록/조회가 일괄 로딩해도 쓰기는 같은 최적화를 거치지 않아 조회 회귀가 이 비용을 발견하지 못했다. 수정은 flush 후 이미 managed 상태인 전표와 품목을 다시 조회했다.
- Sales 소유 aggregate 로더가 기존 root 행 잠금을 먼저 획득하고 품목·전체 배분·보존 snapshot을 준비한다. 품목의 배분과 배분의 snapshot은 각각 하나의 collection fetch 쿼리로 초기화한다. 두 collection을 동시에 fetch join하지 않으며 전표 ID 하나로 필터링한다. 기존 snapshot 일괄 조회를 재사용하고 타 모듈 Entity/Repository를 추가로 참조하지 않는다.
- 수정·상태 변경·입금의 동일한 소유 aggregate 로딩을 이 경로로 모은다. 단순 조회 wrapper가 아니라 root 잠금과 두 collection의 로딩 순서를 소유하며 호출 트랜잭션을 필수로 요구한다. 수정은 기존 flush를 유지한 뒤 동일한 managed 전표로 재예약·감사·최종 응답을 처리한다. 삭제 cascade가 사용할 과거 snapshot도 미리 로딩해 allocation별 조회를 막는다.
- 최상위 트랜잭션, 기존/신규 allocation 합집합 선잠금, 편집별 예약 identity, Farm Engine의 잠금 후 재확인과 Mutation·이동 이력·감사·잔액·입금의 atomicity는 유지한다. 각 유스케이스의 기존 root·거래처·Farm 잠금 순서를 바꾸지 않으며 중간에 추가된 소유 collection 조회는 행 잠금을 추가하지 않는다.
- 예약 전 생성 snapshot, 수정의 예약 해제 후 새 snapshot, 출고 직전 예약 수량 snapshot, 최종 응답의 현재 Farm 상태는 서로 다른 시점 계약이다. 이를 이전 조회값이나 과거 Mutation 결과로 대체하지 않는다. 생성은 기존 최초 응답 snapshot을 replay하며 상태 유지·입금 replay는 현재 상세를 반환한다. 공개 API·저장 JSON·수량·금액·취소·입금 정책은 변경하지 않는다.

### 측정과 회귀 방어

| 실제 application 트랜잭션 경로 | 전체 prepared statement 기존 1 / 8 품목 → 수정 1 / 8 품목 |
| --- | --- |
| 전표 수정·배분 교환 | 42 / 91 → 40 / 68 |
| 출고 완료 | 20 / 41 → 19 / 19 |
| 작성중 취소 | 23 / 44 → 22 / 22 |
| 출고 완료 취소 | 30 / 51 → 29 / 29 |
| 입금 확인 | 20 / 39 → 19 / 17 |
| 동일 입금 재요청 | 10 / 31 → 9 / 9 |
| 동일 판매 상태 요청 | 8 / 29 → 7 / 7 |

- 모든 경로의 lazy collection fetch는 4 / 25 → 각각 1이다. 남는 1회는 단일 root의 품목 collection 로딩이다. 1/8 품목에 같은 Hibernate query 실행 상한을 검사하고, 쓰기 없는 상태 유지·입금 replay는 전체 prepared statement도 각각 7/9회로 고정한다. 입력 품목 수에 비례하는 실제 삭제·생성·이력 쓰기는 줄이거나 고정 비용이라고 주장하지 않는다.
- 각 품목은 같은 2개 난 묶음에 배분해 품목/배분 수 증가의 영향을 분리한다. 고정 Clock과 같은 요청·seed로 측정하고 `backend/build/work-query-count/sales-write-*.json`에 prepared statement·collection fetch·query별 실행 횟수를 저장한다. 일반 서비스 mock 호출 횟수로 SQL 회귀를 대체하지 않는다.
- prepared statement는 SELECT 외 DML·sequence 접근을 포함한다. ID allocation 상태가 결과에 영향을 주며 입금 1품목의 수치가 8품목보다 큰 것도 쿼리의 선형 증가를 의미하지 않는다. 고정 SQL 계약은 쓰기 없는 두 경로에만 적용한다. 이 표는 운영 latency·잠금 대기 benchmark나 완전한 native SQL trace가 아니다.
- 신규 14개 경로 조합은 품목·allocation ID, creation/outbound snapshot, 수정의 교환 배분과 두 묶음 수량/예약, 단독 상세와 최종 응답의 일치, 최초 생성 receipt의 기존 snapshot replay와 업무 행 불변성을 확인한다. 입금 첫 처리와 같은 key 재요청은 입금 이벤트를 한 건만 남긴다.
- 신규 5건은 수정·출고·작성중 취소·출고 취소·입금의 최종 응답 조립 실패 후 전표·배분·snapshot·Mutation·재고 이동·잔액·입금·감사·생성 접수의 모든 행이 rollback하고 다시 처리되는지 검사한다. 기존 PostgreSQL의 배분 합집합 잠금 경쟁, 수량 부족·DB 저장 실패·예약 identity·판매 가능 상태, 생성 접수 병렬/rollback도 함께 실행한다.
- 초기 시험의 상태 변경 명령 생성자와 입금 event enum 오해, 공통 Work seeder가 Sales receipt를 초기화하지 않는 fixture 문제는 제품 변경 전에 바로잡았다. 운영 계약을 시험에 맞춰 바꾸지 않았다.

### 검증

- 수정 전 신규 PostgreSQL 19건 성공. 수정 후 집중 일반/integration/architecture 6개 클래스 40건·PostgreSQL 5개 클래스 107건, 총 147건 성공. 실패·오류·생략은 없고 1분 45초 소요했다. 이후 측정된 query 실행 상한과 쓰기 없는 두 경로의 prepared statement 고정 회귀를 추가했다.
- 백엔드 전체 `./gradlew test`: 131개 클래스 662건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 61개 클래스 624건 성공. 신규 19건을 포함해 실패·오류·생략은 없고 전체 백엔드 검증은 9분 24초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. `python3 scripts/generate_openapi.py`도 성공했고 전체 명세·slice와 생성 타입에 diff가 없다. 공개 API·DB schema 변경이 없어 TypeScript 재생성과 Flyway 추가는 필요하지 않다. 응답 시간 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-015는 부분 완료다. 정산 snapshot과 현재 표시 참조 조회는 별도 분석·회귀가 남는다. Sales의 Farm 재잠금과 시점별 snapshot/최종 현재 상태 조회는 필요한 정확성 경계로 유지한다. 큰 품목·서로 다른 난 묶음 수, 운영 lock 대기·지연·메모리 benchmark와 DB index 대사는 이번 범위에 포함하지 않는다. 조회 개수가 줄어도 반환 행과 실제 이력 쓰기는 입력 크기에 비례한다.

## 27차 변경 — BE-015 정산 원본의 과다 Entity 적재와 무제한 ID 조회 제거

작업일: 2026-10-04. 상태: 경매 결과 projection·ID 분할·snapshot/query-count/rollback/replay 회귀·정책 문서 및 최종 전체 검증 완료. BE-015 코드 개선 범위 완료.

### 원인과 범위

- 기존 정산 query-count는 목록·page와 이미 연결된 초기화 fast path, H2 금융 snapshot 보존을 보호한다. 검색·Work benchmark는 정산 재계산·입금·표시 참조의 적재량을 측정하지 않는다. PostgreSQL의 동시 초기화·재계산/입금 시험을 먼저 확인하고 신규 27건을 제품 수정 전 통과시켰다.
- 정산 재계산의 금융 snapshot과 응답의 표시 참조는 다른 계약이라 두 조회를 하나로 합칠 수 없다. 기존 경매 조회는 scalar application 값만 반환하면서도 결과 → 시도 → lot → 출하 Entity 그래프 전체를 로딩한다. ID 기반 조회도 전달받은 전체 ID를 단일 IN으로 읽었다. query-count가 일정해도 원본 Entity 적재가 결과 수에 비례한다.
- 경매 소유 Repository가 동일한 값과 부모 ID·날짜·표시 참조를 scalar projection으로 조회하고 기존 application `Result`로 변환한다. 모듈 외부 응답·DTO와 필드 타입은 유지하며 Repository row나 Entity를 외부 계약으로 노출하지 않는다. 두 쿼리의 공통 projection은 같은 소유 저장소에 두고 기존 Entity graph 조회를 대체한다.
- 결과 ID의 중복을 제거하고 최대 500개씩 읽는다. 빈 입력은 쿼리 없이, 없는 ID는 이전처럼 결과 Map에서 제외한다. 낙찰 수지 조회의 양수 금액 조건·경매장/경매일 필터·ID 순서와 ID 기반 참조 조회의 전체 결과 범위를 유지한다. 금융 snapshot과 최종 표시 참조의 조회 단계는 유지하며 기존 snapshot을 현재 원본 값으로 덮어쓰지 않는다.
- 거래처 → 정산 잠금 순서, 초기화의 잠금 획득 후 연결 ID 재검증, 정산 행 merge와 입금액 보존, 입금 접수·이벤트·잔액·감사의 최상위 atomicity를 유지한다. projection이 JPQL의 기존 flush를 우회하거나 별도 transaction/외부 시스템을 도입하지 않는다.
- 표시용 참조의 분할 조회는 여러 SQL 시점을 갖는다. 기본 isolation을 올리거나 모든 표시 행이 한 시점의 snapshot이라고 약속하지 않는다. 금융 snapshot은 기존 반영 경계에 저장하며 응답의 수량·단가·금액은 정산 저장값, 품종명·출하등급·출하일은 현재 참조를 사용한다. 초기화의 전체 메모리 누적과 transaction 크기는 이번 변경으로 줄이거나 분할 commit하지 않는다.

### 측정과 회귀 방어

| 실제 application 트랜잭션 경로 | prepared statement 기존 → 수정 (1 / 8 / 501 결과) |
| --- | --- |
| 새 정산 재계산 | 9 / 9 / 19 → 9 / 9 / 20 |
| 기존 정산 재계산 | 8 / 8 / 8 → 8 / 8 / 9 |
| 상세 조회 | 3 / 3 / 3 → 3 / 3 / 4 |
| 입금 확인 | 14 / 14 / 14 → 14 / 14 / 15 |
| 같은 입금 재요청 | 7 / 7 / 7 → 7 / 7 / 8 |
| 미연결 결과 초기화 | 9 / 9 / 23 → 9 / 9 / 23 |

- 각 경로의 경매 결과/시도/lot/출하 Entity load는 4 / 25 / 1,504 → 각각 0이다. fixture는 결과별 별도 lot·시도와 공통 출하 하나로 구성한다. 정산·거래처·설정·입금 Entity까지 0이라고 주장하지 않는다.
- 501행 상세·입금·재계산에서는 ID 조회를 2회로 분할하므로 SQL이 1회 증가한다. 이미 500개씩 원본을 대조하는 초기화는 SQL 수가 유지된다. 성능 개선은 불필요한 Entity 그래프 적재 제거와 SQL 인자 상한이며, 전체 query-count 감소나 latency 개선율로 해석하지 않는다. 실제 정산 행 쓰기와 sequence 할당도 prepared statement에 포함된다.
- `backend/build/work-query-count/settlement-read-*.json`에 prepared statement·경매 Entity load·query별 실행 횟수를 저장한다. 고정 Clock·동일 seed와 application 트랜잭션을 비교하며 mock 호출 횟수를 대신 세지 않는다. native SQL 전체 trace·peak heap·운영 latency benchmark는 아니다.
- 18개 경로 조합은 정산 생성/기존 재계산·상세·입금/같은 key replay·초기화를 1/8/501 결과에서 검사한다. 정산 행 ID와 수량/금액/단가·현재 출하 표시·합계·입금액을 확인하며, 전부 연결된 초기화는 업무 행을 바꾸지 않고 기존 2/4회 fast path만 실행한다. 상세와 입금 replay도 각각 `2 + ceil(N/500)`, `6 + ceil(N/500)` SQL로 고정한다.
- 기존 원본 수량/단가/금액과 품종/등급·경매장 이름을 바꾼 3건은 재계산·초기화·입금 replay가 금융 snapshot과 기존 입금액을 보존하면서 현재 표시를 읽는지 검사한다. 정산 재계산과 입금의 최종 응답 실패 2건은 정산/행·입금 이벤트·잔액·감사의 전체 rollback 및 재시도를 확인한다. ID 조회 0/1/500/501건은 중복·없는 ID·빈 입력·정확한 분할 수와 원본 Entity load 0을 보호한다.
- 기존 PostgreSQL 동시 재계산/입금·초기화·같은 입금 key 경쟁과 거래처 잠금, H2 금융 snapshot·고정 Clock·page/summary/filter 및 query-count 회귀도 함께 실행한다.

### 검증

- 수정 전 신규 PostgreSQL 27건 성공. 수정 후 집중 일반/integration/architecture 5개 클래스 47건·PostgreSQL 2개 클래스 38건, 총 85건 성공. 실패·오류·생략은 없고 1분 3초 소요했다. 이후 상세·입금 replay·연결 완료 초기화의 query-count 상한을 추가했다.
- 백엔드 전체 `./gradlew test`: 131개 클래스 662건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 62개 클래스 651건 성공. 신규 27건을 포함해 실패·오류·생략은 없고 전체 백엔드 검증은 9분 32초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. `python3 scripts/generate_openapi.py`도 성공했고 전체 명세·slice와 생성 타입에 diff가 없다. 공개 API·DB schema 변경이 없어 TypeScript 재생성과 Flyway 추가는 필요하지 않다. 응답 시간 benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위와 BE-015 판단

- BE-015의 중간 상세 응답 제거는 23~25차, Sales의 쓰기 aggregate/중복 재조회는 26차, 정산의 불필요한 경매 Entity 적재는 27차다. 최종 전체 검증을 통과해 감사에서 추적한 코드 개선 범위를 완료 처리한다. Farm의 잠금 후 재확인·시점별 snapshot과 정산 금융/현재 표시의 별도 조회는 정확성에 필요한 경계로 유지한다.
- 운영 부하·lock 대기·peak heap, 매우 큰 미연결 초기화의 transaction/메모리와 기존 index 대사는 남는다. 이는 BE-015의 임의 조회 삭제로 해결하지 않으며 별도의 성능·조회량·index finding 범위에서 다룬다. 500개 ID 분할이 전체 메모리 적재 상한이나 정산 단위 commit을 의미하지 않는다.

## 28차 변경 — BE-016 구조 변경 결과의 생성 DTO 경유 제거

작업일: 2026-10-05. 상태: 내부 결과 계획의 직접 Mutation 값 생성·PostgreSQL 회귀 보강·최종 전체 검증 완료. BE-016 완료.

### 원인과 범위

- `BatchStructureTransformationExecutor`는 결과를 `OrchidGroupCreateRequest`에 담은 뒤 같은 속성을 `OrchidGroupMutationDetails`에 다시 복사했다. 중간 생성 요청에는 HTTP 호출이나 Bean Validation 실행이 없고, 속성 추가 시 수정 지점만 늘어났다.
- 내부 `ResultPlan`이 논리 구역 ID·Mutation 상세 값·결과 목적을 직접 보유한다. 기존 Mutation 상세 생성자의 검증·정규화를 계획 시점에 수행하며 새로운 mapper/service나 공개 계약을 추가하지 않는다.
- 원본 행 잠금 후 차감 전에 상속 값을 확보한다. 이동의 원본 화분·연차·상태 상속과 `NORMAL` 목적, 다른 구조 변경의 입력 화분·연차 및 목적별 상태, 결과 순서와 기본 속성 원본 선택을 유지한다. 적용 후 실제 결과 수량·ID를 읽어 Work 효과와 계보를 기록하는 경계도 유지한다.
- 1:1 전량 이동의 기존 ID 보존 분기, 최상위 트랜잭션·잠금 순서, Work/Mutation/계보 원자성, 저장 JSON·지문·요청 replay 계약은 변경하지 않는다. API·도메인 정책·DB schema 변경이 없으며 기존 application 명령 경계 안의 리팩터링이라 별도 구조 정책을 추가하지 않는다.

### 회귀 방어

- 기존 PostgreSQL 네 유형(`REPOT`, `DIVIDE`, `MERGE`, `MOVEMENT`) 회귀에 품종·논리 구역·배치 유형·트레이 수·분할 허용·위치·메모 검증을 보강했다. 명시 값과 null/빈 문자열을 서로 다른 두 결과에 전달해 정규화·기본값과 순서별 보존을 함께 확인한다. 시험 전용 실행기나 호출 횟수 mock을 만들지 않는다.
- 기존 원본 차감 전 상태 상속·목적별 상태·수량 수지·계보/Mutation 연결 및 두 번째 결과 위치 실패의 전체 rollback 검증을 유지한다. 보강한 PostgreSQL 5건은 제품 수정 전 통과했다.
- 집중 검증은 이동 ID 보존·취소/replay·역사 handler·잘못된 handler rollback, 일반 분갈이·분주/합식·배치 이동 및 저장 지문 호환·모듈 경계를 함께 실행한다.

### 검증

- 수정 후 집중 일반/integration/architecture 6개 클래스 41건·PostgreSQL 4개 클래스 19건, 총 60건 성공. 실패·오류·생략은 없고 1분 14초 소요했다.
- 백엔드 전체 `./gradlew test`: 131개 클래스 662건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 62개 클래스 651건 성공. 실패·오류·생략은 없고 전체 백엔드 검증은 9분 25초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. 공개 API·생성 타입·DB schema 변경이 없어 OpenAPI/TypeScript 재생성과 Flyway 추가는 필요하지 않다. benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-016은 이 단일 실행기의 중간 DTO 매핑 제거를 완료 범위로 삼는다. HTTP 입력을 application 명령으로 변환하는 실제 경계와 구형 요청 호환 mapper는 유지한다. Work 취소 서비스의 책임 분리는 별도 BE-017 범위다.

## 29차 변경 — BE-017 Work 취소의 검사·보상·응답 단계 분리

작업일: 2026-10-05. 상태: 서비스 내부 단계 분리·차단 우선순위/잠금 재검사/snapshot 회귀 보강·최종 전체 검증 완료. BE-017 완료.

### 원인과 범위

- `WorkOperationVoidService`의 단건 검사는 상태·종류 차단, 연관 폐기·효과 수집, 기록형/포트/구조 검사, 영향 응답 변환을 하나의 가변 누적 흐름에서 처리했다. 일괄 선택 범위 검증과 단건 보상 실행도 취소 처리 메서드에 섞여 있었다. 기존 원자성 실패를 재현한 변경이 아니라 BE-017의 정책 변경 비용을 줄이는 리팩터링이다.
- 단건 검사를 작업 자체의 차단 → 관련 작업/효과/정렬된 Mutation ID 수집 → 종류별 검사 → 영향 값 변환으로 나눈다. 내부 `CancellationScope`가 이 실행에서 수집한 범위를 전달하고 `PREVIEW`/`LOCKED` 검사 모드를 명시한다. 검사 결과의 영향 작업·묶음·차단 사유를 공개 응답으로 변환하는 기존 경계는 유지한다.
- 종료 상태 → 연관 폐기 단독 취소 → 지원하지 않는 상태/유형, 연관 폐기 미완료 → Farm 차단 사유의 우선순위를 유지한다. 연관 폐기 차단이 있어도 Farm 검사를 생략하지 않는다. 기록형은 최초 대상 snapshot·첫 묶음 등장 순서, 구조형은 원본 복구 → 결과 생성 취소 순서를 유지한다.
- 일괄 취소는 상태·종류·부모/자식 선택 범위와 모든 선택 작업의 Mutation 존재를 별도 단계에서 검증한다. 단건의 기록형/포트 취소 규칙을 일괄 취소에 강제로 적용하지 않으며, 기존 완결된 효과 체인의 Farm 보상 검사를 유지한다.
- 단건 보상 실행과 연관 폐기 상태 전이를 별도 내부 단계로 분리하고, 포트 검사·보상·즉시 배치 입고 보상이 사용하는 효과 변환을 한 곳으로 모았다. 즉시 입고의 전용 상태 제한과 입고를 다시 여는지 여부, 단건/일괄/연관 작업 요청 키, 조회·검사·보상 사이의 기존 재조회는 유지한다.
- 새 Service·registry·전략 계층을 만들지 않는다. 기존 최상위 트랜잭션과 포트/구조 경로별 잠금 순서·잠금 후 재검사, 역순 연관 폐기 처리, 효과·작업·Mutation·감사의 atomicity 및 replay 계약은 유지한다. Farm port·HTTP/저장 계약·DB schema 변경이 없고 query-count/latency 개선을 주장하지 않는다.

### 회귀 방어

- 기존 단건 취소 단위 시험은 완료된 기록형 한 경로만 보호했다. 신규 8건을 제품 변경 전 통과시켜 종료 상태/연관 폐기/미지원 유형의 우선순위, 포트/구조의 조회 허용 후 잠금 검사 차단, 연관 폐기와 Farm 차단의 누적 순서·count·영향 응답 순서를 고정했다.
- 이후 연관 폐기 우선순위의 실제 취소 실패와 차단 시 효과 무변경을 보강하고 기록형의 첫 대상 snapshot·중복 제거·입고 대상 제외 1건을 추가했다. 단위 검증은 내부 helper를 직접 호출하지 않고 공개 조회/취소의 결과·기존 상태·검사 모드 경계를 확인한다.
- 기존 PostgreSQL의 단건/일괄 취소·같은 키의 다른 사유 거절·완료와 취소 경쟁·동시 Mutation 후 재검사, 부분 포트/입고/형제 대상 잠금·취소 재전송, 공동 이동/폐기 취소, 점유·복구 충돌 및 DB 실패 뒤 Work/Mutation/감사 rollback을 함께 실행했다.

### 검증

- 집중 일반/integration/architecture 5개 클래스 42건·PostgreSQL 6개 클래스 52건, 총 94건 성공. 실패·오류·생략은 없고 1분 40초 소요했다. 이후 기록형 snapshot 회귀 1건과 기존 단위 시험의 실패 결과 검증을 보강했다.
- 보강 후 최종 취소 단위 시험 10건 성공. 백엔드 전체 `./gradlew test`: 131개 클래스 671건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 62개 클래스 651건 성공. 실패·오류·생략은 없고 전체 백엔드 검증은 9분 28초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. 공개 API·생성 타입·DB schema 변경이 없어 OpenAPI/TypeScript 재생성과 Flyway 추가는 필요하지 않다. benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-017은 감사에서 지적한 서비스 내부 검사·수집·종류별 처리·응답 책임 정리를 완료 범위로 삼는다. 앞으로 새 blocker는 조회·잠금 검사·일괄 보상에서 같은 의미로 적용되는지 확인해야 한다. 새로운 취소 정책, 모든 writer의 무교착 증명, Farm 보상 알고리즘의 별도 재설계는 이번 범위에 포함하지 않는다.

## 30차 변경 — BE-018 Work 그래프 조립 상태와 노드 종류별 생성 정리

작업일: 2026-10-05. 상태: 내부 조립 상태·노드 factory 정리·상한/순서/JSON 회귀·계약 생성 및 최종 전체 검증 완료. BE-018 완료.

### 원인과 범위

- `WorkOperationGraphQueryService.addMutationFlow`는 조회 입력 Map들과 수정할 출력 nodes/edges를 함께 전달받고 `truncated`만 별도로 반환했다. 네 노드 종류는 21개 위치 인자와 반복 `null`로 생성해, 필드 추가나 상한 변경 시 값 대응과 출력 상태를 여러 곳에서 확인해야 했다. 기존 표시 오류를 재현한 변경이 아니라 BE-018의 변경 비용을 줄이는 리팩터링이다.
- 호출마다 생성하는 내부 `GraphAssembly`가 출력 노드·간선·보이는 ID·상한·truncation을 소유한다. seed 절단 후 보이는 ID 인덱스를 만들고 Mutation 묶음의 수용 판단, 보이는 작업 선정과 최종 불변 목록 응답을 처리한다. 상한 적용 전 전체 seed의 별도 ID 인덱스를 추가하지 않는다. `addMutationFlow`는 조회 입력 context와 조립 상태를 받아 실행하며 별도 boolean 결과를 호출자가 합치지 않는다.
- 조회 입력은 별도 `MutationFlowContext`로 전달하고 이 단계에서 Map을 수정하지 않는다. 기존 작업 발견·효과/보정/대상 조회 순서와 조건, read-only 트랜잭션·Farm graph port는 유지한다. 별도 Service·일반 graph framework·Repository/캐시를 추가하지 않는다.
- DTO 가까이에 출처·작업·Mutation·상태 factory를 두어 각 종류에 필요한 값만 전달한다. DTO가 Entity나 application port를 참조하지 않으며 공개 record 필드·ID 형식·날짜·snapshot·null/빈 목록을 유지한다. 품종명 배열의 과거 null도 삭제하거나 `List.copyOf`로 거절하지 않는다.
- 출처 → seed 작업 → 수용한 Mutation별 발견 작업/Mutation/상태 순서, 공유 상태 노드 재사용, 상한을 넘는 묶음 전체 생략 후 다음 묶음 검토, 보이는 양 끝점의 간선 및 마지막 선후 관계를 유지한다. 같은 접수의 형제 작업 제외와 무효화 전 정상 효과 표시도 유지한다.

### 회귀 방어

- 기존 그래프 단위 회귀 5건은 형제 작업 제외·이동/폐기 선후 관계·보정 Mutation·계보 발견·무효화 효과 표시를 보호했다. 신규 8건을 제품 수정 전 통과시켜 seed 상한 미만/정확한 상한/초과, Farm truncation 전파, 공유 상태 및 큰 묶음 생략/다음 묶음 수용·선택 작업·노드/간선 순서·보이는 끝점을 확인했다.
- 네 노드 종류와 전체 상태 snapshot의 JSON golden fixture는 21개 노드 필드·종류별 null·품종명 null·수량/예약·날짜/시각·위치·이력값을 검사한다. 서비스 공개 조회 응답을 직렬화하며 내부 조립 helper 호출이나 새 생성자 복사를 기대값으로 사용하지 않는다.
- seed가 상한을 초과하면 원래 순서대로 자르므로 출처가 많은 경우 root 작업이 보이지 않을 수 있다. seed가 정확히 상한이면 Mutation 확장을 실행하지 않아 Farm fragment의 truncation을 합치지 않는 기존 동작도 고정했다. 이 변경에서 상한/완전성 정책을 조용히 바꾸지 않는다.

### 검증

- 수정 전 그래프 13건, 수정 후 그래프/Farm adapter/architecture 3개 클래스 24건 성공. 실패·오류·생략은 없고 수정 후 집중 검증은 11초 소요했다.
- 보이는 ID 인덱스를 seed 절단 후 초기화하도록 최종 보정한 뒤 같은 집중 24건이 다시 성공했다. 최종 백엔드 전체 `./gradlew test`: 131개 클래스 679건 성공. 실패·오류·생략은 없고 최종 전체 검증은 1분 38초 소요했다.
- `python3 scripts/generate_openapi.py`는 146 operations·124 paths·263 schemas로 성공했고 전체 명세·slice에 diff가 없다. 프론트엔드 `npm run check`도 성공했다. 공개 필드·enum 변경이 없어 생성 TypeScript 재생성은 필요하지 않다.
- 이번 변경은 조회 응답 조립만 바꾸며 PostgreSQL SQL·Repository 조건·DB schema·트랜잭션·쓰기/잠금 경계를 변경하지 않아 `workE2eTest`를 재실행하지 않았다. benchmark·브라우저 E2E·운영 DB 대사도 실행하지 않았다.
- 백엔드 `spotlessCheck`, `git diff --check`: 성공. 최종 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-018은 국소 출력 조립과 종류별 생성 정리를 완료 범위로 삼는다. 출력 상한을 DB 탐색량·하위 이력 적재량 상한으로 해석하지 않는다. 내부 조회 fan-out·메모리·truncation 의미 개선은 별도 BE-034 범위에서 API 소비자와 함께 검토한다. 과거 snapshot을 현재 Entity 값으로 복원하는 변경도 포함하지 않는다.

## 31차 변경 — BE-019 소비자 기준 공개 값/업무 계약과 내부 helper 경계

작업일: 2026-10-05. 상태: 소비자 기준 공개 계약·내부 helper 경계·호환/architecture 회귀·최종 검증 완료. BE-019 현재 소비자 범위 완료.

### 원인과 범위

- `OrchidGroupReader`는 Sales가 사용하는 현재 상태·판매 선택·잠금 API와 `Optional<OrchidGroup>` 조회를 함께 public으로 노출했다. Entity 반환의 제품 소비자는 Farm 현장 동기화 한 곳이며, 기본 `findById` wrapper는 사용되지 않았다. 현재 Sales의 Entity 접근 위반을 수정한 것이 아니라 승인된 외부 계약과 내부 JPA 접근을 구분한 변경이다.
- Entity 반환 메서드 둘을 제거한다. Farm 현장 동기화는 소유 Repository의 같은 상세 조회를 기존 최상위 쓰기 트랜잭션 안에서 사용한다. 외부 Reader의 상태 값·판매 선택·`MANDATORY` 잠금, ID 정렬·500개 분할·snapshot 조회 시점은 유지한다. Entity를 값으로 바꾸어 Farm 내부 변경 감지를 끊거나 같은 내용을 감싼 내부 Reader/interface를 추가하지 않는다.
- Farm 분갈이·현장 동기화가 Work 내부 제목 helper를 주입하던 의존을 제거한다. Work의 기존 대상 즉시 실행 진입점은 품종명 값을 받는 `executeVarietyHistoryForTarget`으로 좁히고 Work가 자동 제목을 생성한 뒤 기존 접수·효과·응답 경로를 실행한다. 두 실제 소비자만 사용하던 자유 제목 진입점을 별도 호환 wrapper로 남기지 않는다. 이름만 바꾼 public 제목 formatter도 추가하지 않는다.
- 분갈이는 기존 원본 묶음 선잠금에서 읽은 품종명을, 현장 동기화는 기존 상세 조회에서 읽은 품종명을 전달한다. Work가 품종명을 재조회하지 않으며 Work title·요청 원문 title·payload·actor 정규화·영속 지문 입력은 각각 기존 의미를 보존한다. 요청 원문 title이 자동 제목에 표시되지 않아도 변경된 원문은 같은 키로 재실행할 수 없다.
- 이동 시험 fixture도 제거된 Entity 반환 API 대신 같은 소유 Repository 상세 조회를 사용한다. 제품의 조회 조건·managed Entity·트랜잭션 annotation·DI proxy·receipt/membership·효과/Mutation/감사 연결·HTTP/DB 계약은 유지한다. 현장 동기화 HTTP의 `FEATURE_ON_HOLD` 정책을 해제하지 않는다.

### 회귀 방어

- 제품 수정 전에 신규 분갈이 HTTP 회귀 2건을 포함한 8건을 통과시켰다. 품종명의 앞뒤 공백·자동 제목의 150자 상한·worker 정규화, 자동 제목과 원문 title의 분리·동일 요청 replay·원문 title만 다른 재요청 거절 및 수량/작업/계보 무중복을 확인한다.
- 기존 현장 동기화 application 입력의 JSON·고정 지문 golden을 유지한다. 공개 Reader의 기존 잠금 순서/빈 입력/누락 ID·batch query count와 이동 fixture 흐름도 함께 확인한다.
- 신규 architecture 검증은 공개 Reader의 반환/입력 generic container와 중첩 record에서 Entity를 검출한다. 별도 compiled dependency 검증은 Work 외부의 `WorkOperationSupport` 참조를 차단한다. 다른 Work service 전체를 무조건 interface/DTO로 나누거나 모든 application 클래스를 내부로 숨기는 규칙은 만들지 않는다.

### 검증

- 제품 수정 후 집중 일반/integration/architecture 8개 클래스 53건 성공. 실패·오류·생략은 없고 42초 소요했다. Entity API를 사용하던 이동 fixture의 컴파일 실패는 Repository 주입 전환으로 수정했다.
- Architecture gate 자체도 검증했다. 과거 Reader의 public `Optional<Entity>` 메서드와 Farm의 Work helper dependency를 임시 복원하자 두 gate가 각각 의도대로 실패했다. 제품 소스는 자동 복원했고 이후 최종 전체 검증을 실행했다. 이 의도적 거절 결과를 성공 시험 수에 포함하지 않는다.
- 최종 백엔드 전체 `./gradlew test`: 132개 클래스 683건 성공. 실패·오류·생략은 없고 2분 소요했다. 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`도 성공했다.
- PostgreSQL 집중 `workE2eTest`: 5개 클래스 61건 성공. 실패·오류·생략은 없고 1분 2초 소요했다. 구형 분갈이/현장 동기화 접수 지문·payload snapshot·재전송·다른 원문 거절·효과 DB 실패 시 Work/Mutation/감사/접수 rollback과 재시도, 판매 교차 수정 잠금/재고·이동/일괄 취소 회귀를 확인했다. SQL·schema·트랜잭션/잠금 경계를 바꾸지 않아 PostgreSQL 전체 62개 클래스는 재실행하지 않았다.
- 공개 HTTP 필드·enum·Controller·DB schema 변경이 없어 OpenAPI/TypeScript 재생성·Flyway 추가는 필요하지 않다. benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- `git diff --check`: 성공. 최종 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-019의 현재 소비자 기준 Entity/helper 공개 경계 정리를 완료 대상으로 삼는다. Work Receipt의 외부 노출(BE-021), Farm 보정 adapter의 Work 조율(BE-020), 이외 실제 HTTP DTO/application 경계(BE-022)는 별도 finding이며 이번 변경에 끼워 넣지 않는다.
- 새 비 HTTP 채널은 아직 도입하지 않는다. 향후 채널이 생길 때 입력 검증·신뢰된 주체·인가·감사 context·DI proxy·업무별 재시도 계약을 함께 제공한다는 기준만 architecture 문서에 명시한다. 이번 변경을 비 HTTP 권한/context 자동 적용이나 운영 과거 요청 corpus의 호환 증명으로 해석하지 않는다.

## 32차 변경 — BE-020 Work 보정 순서와 Farm 검증/변경 경계 정리

작업일: 2026-10-05. 상태: Work 조율·Farm 준비/적용 경계·회귀·최종 전체 검증 완료. BE-020 완료.

### 원인과 범위

- Work는 접수·원본 잠금·보정 이벤트 저장소를 소유했지만, 저장 callback을 Farm에 넘겨 감사 ID 확보 시점을 위임했다. Farm adapter가 Work 날짜 조회/변경 서비스와 최종 감사 결과까지 조립해 날짜 전용 보정도 농장 변경 경로를 거쳤다. 기존 rollback 실패를 재현한 수정이 아니라 BE-020의 조율 소유권과 변경 비용을 정리한 변경이다.
- Work 보정 포트를 `prepare`와 `apply`로 나눈다. Farm은 기존 대상/수량 검증과 묶음 잠금·실사·후속 사용·현재 수량 검사 아래 감사 전후 값·수량 수지 변경·원본 Mutation 참조를 불변 계획 값으로 반환한다. Entity·Repository나 저장 callback을 포트 계약에 노출하지 않는다. 별도 service·registry·일반 workflow framework를 추가하지 않는다.
- Work는 이미 잠근 원본의 날짜로 미래일·생성 취소 병행·무변경을 판단하고, Farm 준비가 끝난 뒤 보정 이벤트를 저장해 ID를 확보한다. Farm 적용은 이 ID를 Mutation 출처로 사용하고 기존 명령의 수량·원문 상태·사유·업무일과 참조를 유지한다. Work가 같은 managed 원본의 날짜·기간을 바꾸고 기존 `Corrected` JSON·Mutation 연결·접수를 확정한다. Farm이 호출하던 별도 Work 날짜 service는 다른 소비자가 없어 제거했다.
- Farm 두 단계는 `MANDATORY`로 최상위 Work 트랜잭션에 참여한다. 계획은 같은 명령·트랜잭션 안에서만 사용하며 준비의 잠금이 적용/응답 완료까지 유지된다. 접수 → Work 원본 → 난 묶음의 기존 선잠금과 Mutation 내부 순서, 감사 ID를 Mutation 전에 확보하는 생성 지점·원자성, replay의 기존 접수 우선 판정을 유지한다.
- 빈 난 묶음 조정·빈 수량 정정 목록의 날짜 전용 요청만 Farm 보정 포트를 우회한다. 동일 값 난 묶음 행을 보내는 기존 날짜 보정은 Farm 검증을 유지한다. 결과 생성 취소/작업일 병행 제한은 Work 원본을 읽은 직후 판정하므로 복수의 잘못된 조건을 함께 보낸 요청에서 날짜 제한이 먼저 반환될 수 있다. 유효 요청의 정책과 단일 위반의 HTTP 오류 code·저장 계약은 유지한다.
- Date-only에서도 최종 현재 참조 응답·Work 수량 context 조회는 유지한다. 수량 정정/실사의 기본 `FEATURE_ON_HOLD`를 해제하거나 과거 실행 스냅샷·현재 수량을 재구성하지 않는다. 조회 성능·일반 context 중복 적재 개선은 별도 범위다.

### 회귀 방어

- 제품 변경 전에 신규 PostgreSQL 5건을 34초에 통과시켜 무변경 접수 rollback/같은 키 재시도, 결과 생성 취소와 날짜 변경 병행 거절, 최종 감사 JSON DB 실패 시 Mutation·작업일·모든 원장/감사/접수 rollback, 날짜 전용 DB 실패/같은 키 재시도, 동시 날짜+수량 보정의 연속 전후 이력을 고정했다.
- 일반 integration에서 빈 목록 날짜 전용 요청의 Farm 포트 무호출·무변경 무감사·기간 길이·완료 상태·원본 명령/효과 스냅샷 보존을 추가했다. 기존 날짜 fixture는 동일 값 난 묶음 행을 포함했으므로 그 요청에는 준비 검증을 허용하고 Mutation 적용만 금지해 실제 계약을 구분한다.
- 새 architecture gate는 보정 포트의 입력·반환 generic/record 안의 Entity와 `java.util.function` callback을 거절한다. 기존 수량 정정의 저장 지문·기본 비활성화·상태/생성 취소 허용 회귀를 유지한다. PostgreSQL에는 호출자 트랜잭션 없는 Farm 준비/적용 거절도 추가했다.

### 검증

- 최종 집중 일반/integration/architecture 5개 클래스 22건 성공. 실패·오류·생략은 없고 29초 소요했다.
- 최종 백엔드 전체 `./gradlew test`: 132개 클래스 686건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 62개 클래스 657건 성공. 실패·오류·생략은 없고 최종 백엔드 전체 검증은 9분 34초 소요했다.
- PostgreSQL 전체에는 신규 6건 외에 기존 보정의 병렬 동일/다른 접수·최초 전후 값·취소 후 replay·지문/저장 snapshot·수량 수지·현재 실사/후속 사용 차단·생성 취소/재활성화·제약 실패 rollback·migration/원장 대사 회귀를 포함한다. 기본 비활성화된 수량 기능과 허용된 날짜/상태/생성 취소 경로도 확인했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. HTTP 필드·enum·Controller·DB schema 변경이 없어 OpenAPI/TypeScript 재생성·Flyway 추가는 필요하지 않다. benchmark·브라우저 E2E·운영 DB 대사는 실행하지 않았다.
- 최종 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-020은 Work 보정의 callback·날짜·최종 감사 결과 조율 소유권을 완료 범위로 삼는다. Farm이 사용하는 Work 소유 실행/Mutation 참조와 수량 snapshot 조회 계약은 유지하며 이 read 경계를 없애기 위한 DTO/interface를 일괄 추가하지 않는다.
- 보정 수량 context의 반복 적재/Java 집계(BE-037), Work Receipt 외부 노출(BE-021), 다른 HTTP DTO/application 경계(BE-022), 운영 과거 보정 corpus 검증과 과거 감사 복구는 별도다.

## 33차 변경 — BE-021 입고 포트 취소의 Work Receipt 내부화

작업일: 2026-10-05. 상태: 업무 API·Farm adapter·저장 호환/rollback/architecture 회귀·최종 전체 검증 완료. BE-021 완료.

### 원인과 범위

- Farm의 입고 포트 취소 한 경로가 Work Receipt helper에 namespace·Object 지문 입력·저장 callback·Work ID 목록을 넘기고 `executeExisting`을 선택했다. 현재 멱등 처리 실패를 재현한 버그 수정이 아니라 접수 표현과 생성 membership 의미의 소유권을 정리하는 BE-021 변경이다.
- 별도 Receipt wrapper나 범용 workflow를 추가하지 않고 기존 Work 포트 업무 service에 입고별 취소 API를 둔다. 호출자는 입고 ID·요청 키·사유만 전달한다. Work가 기존 키 정규화·사유 trim/빈 값 처리·`INBOUND_POTTING_VOID:<입고 ID>:<키>` namespace·`inboundRecordId/reason` 지문·기존 작업의 비생성 membership을 선택한다. 지문에는 키나 실행 metadata를 추가하지 않는다.
- Farm은 Work가 정의한 포트 취소 업무 port를 구현한다. 기존 연결 입고/Work 선잠금 → 입고 쓰기 조회 → 입고 자격 검증 → 감사 전값 → Work 취소/Mutation 보상 → 입고 감사 후값의 실행 순서와 소유 모듈 내 Entity 접근을 유지한다. port에는 Entity·Repository·Receipt scope·지문 계산용 Object·`Supplier`를 전달하지 않는다. 반환 Work ID를 Receipt 목록으로 조립하는 것은 Work 내부 책임이다.
- 업무 API와 Farm adapter는 `MANDATORY`로 최상위 입고 service 트랜잭션에 참여한다. 기존 Receipt claim/행 잠금이 Farm 검증보다 먼저이며 완료 Receipt는 업무 port를 다시 호출하지 않는다. 입고/보상/효과/감사/접수를 한 트랜잭션에 확정한다. 기존 입고 취소 경로와 Work 직접 취소·포트 실행의 접수 의미는 변경하지 않는다.
- 입고 포트 취소 응답은 기존처럼 현재 입고 참조로 조립한다. 새 포트 작업 후 옛 키로 재시도하면 새 작업을 취소하지 않고 현재 입고 응답을 반환한다. Receipt의 ID 기반 replay를 일반 Work 생성 응답 snapshot으로 바꾸거나 과거 접수를 재작성하지 않는다.

### 회귀 방어

- 제품 변경 전 신규 PostgreSQL 2건을 통과시켜 기존 저장 계약을 고정했다. 기존 namespace와 literal JSON의 독립 SHA-256 지문·공백 정규화·생성 membership 보존을 확인하고, 완료 Receipt를 구형 필드/작업 ID로 직접 재설치한 뒤 대체 포트 작업이 있는 상태에서 replay/다른 사유 충돌이 DB 상태를 바꾸지 않는지 검증한다.
- 입고 최종 감사의 실제 DB CHECK 실패를 주입해 입고·Work/대상/실행/효과/참조·묶음·Mutation/entry/relation·계보·감사·Receipt/membership을 전후 비교한다. 전체 rollback과 같은 키 재시도·추가 replay의 무변경을 확인한다. 테스트 비교는 PostgreSQL 배열을 포함한 DB 행 JSON을 사용한다.
- 새 PostgreSQL 회귀는 호출자 트랜잭션 없는 업무 API와 Farm port가 모두 실패하고 DB 상태를 바꾸지 않는지 확인한다. 기존 병렬 동일 키/다른 사유, 부분 실행 취소, 새 포트 작업 후 지연 replay, 형제 입고 잠금, 일반 변경/취소 경쟁을 유지한다.
- 컴파일된 dependency architecture gate는 다른 모듈의 Work Receipt helper·지문 계산기 참조를 차단한다. 기존 Farm 구현을 일시적으로 복원한 음성 대조에서 Receipt 의존으로 예상한 1건 실패를 확인한 뒤 현재 소스를 복원했다. 포트 계약의 Entity·중첩 container·저장 callback 금지도 추가했다.

### 검증

- 집중 일반/integration/architecture 5개 클래스 30건 성공(32초). 집중 PostgreSQL 3개 클래스 31건 성공(48초). 실패·오류·생략 없음.
- 최종 백엔드 전체 `./gradlew test`: 132개 클래스 688건 성공. PostgreSQL 전체 `./gradlew workE2eTest`: 62개 클래스 660건 성공. 실패·오류·생략은 없고 최종 백엔드 전체 검증은 9분 31초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. PostgreSQL 전체에는 신규 3건 외에 기존 접수/replay·동시성·rollback·migration·수량 원장 대사 회귀를 포함한다.
- HTTP 필드·enum·Controller·DB schema 변경이 없어 OpenAPI/TypeScript 재생성·Flyway 추가는 필요하지 않다. benchmark·브라우저 E2E·운영 과거 Receipt corpus 검증은 실행하지 않았다.
- 최종 전체 검증 이후에는 진행 문서의 완료 상태와 검증 결과만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-021은 실제 Farm 소비자의 Receipt 메커니즘 의존 제거와 업무별 접수 소유권을 완료 범위로 삼는다. Work 내부의 Receipt 공용 helper는 여러 Work application 경로가 사용하므로 유지한다. 입고 전체 취소의 별도 child key 의미와 키 없는 신규 채널의 재시도 정책을 이 취소 API에 일괄 통합하지 않는다.
- 다음 변경은 BE-022의 Sales 감사와 Settlement 내부 helper 결합을 검토한다. 과거 접수의 운영 corpus 검증과 실제 저장 version 전환은 BE-013의 별도 범위다.

## 34차 변경 — BE-022 전표 입금 감사의 Sales 소유권 정리

작업일: 2026-10-05. 상태: 소유권/가시성 정리·감사/rollback/architecture 회귀·최종 검증 완료. BE-022 완료.

### 원인과 범위

- Sales 입금이 Settlement의 감사 helper에 전표 상태 값·target 종류·식별자를 전달했다. helper에는 Settlement Entity 감사도 함께 있어 입금 원장 API와 별개인 내부 구현 의존이 생겼다. 현재 금융 오류를 재현한 버그 수정이 아니라 감사 조립 소유권과 변경 결합을 정리하는 BE-022 변경이다.
- 기존 Sales 감사 helper가 전표의 입금 전후 스냅샷과 감사 조립을 소유한다. Sales 입금은 Settlement의 기존 수동 입금 원장·거래처 잔액 API를 계속 사용하며 감사는 공통 Audit 값 계약으로 기록한다. 전표 감사에 전체 품목·배분을 추가로 읽거나 새 공용 Payment Audit API/복제 DTO를 만들지 않는다.
- Sales와 Settlement의 감사 helper 클래스·메서드를 package-private으로 제한한다. Settlement의 범용 target 감사 입력은 소유 경매 정산의 전용 감사 메서드로 좁히고, 이제 외부 소비자가 없는 scalar payment snapshot 메서드는 제거한다. 설정/입금 이벤트 감사는 같은 Settlement helper에 유지한다.
- 전표 입금 감사의 기존 `UPDATED`·`SETTLEMENT_MANAGEMENT`·`SALES_SLIP`과 세 상태 필드·변경 필드 순서·partner/target context를 보존한다. 경매 정산의 기존 source/target/context/상태 표현도 유지한다. 감사 출처는 저장·조회 계약이며 helper의 코드 소유 모듈명과 일치해야 하는 것은 아니다.
- 전표 상태 감사와 `PAYMENT_EVENT` 생성 감사는 서로 다른 사실이므로 둘 다 남긴다. 최상위 입금 트랜잭션·root/거래처 잠금 순서·원장 접수 키·같은 요청의 replay 우선 판정·잔액 재계산·최종 응답 조립 순서는 변경하지 않는다. 일반 판매 수정/생성/취소 감사의 `SALES_MANAGEMENT` 출처도 유지한다.

### 회귀 방어

- 제품 변경 전 기존 일반 입금 시험에 부분입금→완납의 정확한 감사 전후 JSON·source/action·변경 필드 순서·entity ID·context를 추가해 통과시켰다. 재시도·다른 금액/날짜의 키 재사용·과입금 거절이 감사 건수를 늘리지 않고, 입금 이벤트 감사에서 입금자명/메모를 제외하는 기존 검증을 유지한다. 경매 정산 감사의 독립 상태 표현도 고정했다.
- 신규 PostgreSQL 2건은 전표 상태 감사와 입금 이벤트 감사 각각의 실제 DB CHECK 실패를 주입한다. 전표·수동 원장/매칭 이벤트·잔액·감사 행의 전체 rollback, 같은 키 재시도 성공, 두 감사 사실 보존, 추가 replay의 무변경을 제품 변경 전후 확인한다. sequence 번호 공백은 실패 복구 정책의 검증 대상이 아니다.
- 컴파일된 dependency architecture gate는 다른 모듈의 Sales/Settlement 감사 helper 참조를 거절한다. 변경 전 Sales 입금/Settlement helper를 일시 복원한 음성 대조에서 기존 직접 의존으로 예상한 1건 실패를 확인한 뒤 현재 소스를 복원했다. package-private 가시성도 Java 컴파일 단계에서 외부 참조를 제한한다.

### 검증

- 제품 변경 전 집중 일반 2건·신규 PostgreSQL 2건 성공(34초). 변경 후 집중 입금/감사/원장/architecture 5개 클래스 42건 성공(34초).
- 최종 백엔드 전체 `./gradlew test`: 132개 클래스 689건 성공. 관련 PostgreSQL `workE2eTest`: 거래처/판매·경매 입금 13건·판매 쓰기 query-count 19건·정산 조회 query-count 27건, 총 3개 클래스 59건 성공. 실패·오류·생략은 없고 최종 백엔드 검증은 2분 42초 소요했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. PostgreSQL 검증에는 신규 두 감사 실패 회귀 외에 기존 동시 입금/replay·거래처 잠금·최종 응답 실패 rollback·금융 snapshot/query-count 회귀를 포함한다.
- HTTP 입력/응답·enum·Controller·DB schema·금액 정책·트랜잭션/잠금 구현의 변경은 없다. PostgreSQL 전체·benchmark·브라우저 E2E·운영 과거 감사 대사는 실행하지 않는다. OpenAPI/TypeScript 재생성·Flyway 추가는 필요하지 않다.
- 최종 검증 이후에는 진행 문서의 완료 상태/결과와 architecture 문서의 주어를 명확히 하는 문구만 갱신했다. 제품 코드·시험·HTTP/저장 계약은 변경하지 않았다.

### 남은 범위

- BE-022는 실제 Sales 소비자의 Settlement 감사 내부 의존과 불필요한 공개 target helper 제거를 완료 범위로 삼는다. 입금 원장·잔액 API와 기존 감사 사실의 출처를 통합하거나 과거 감사를 재작성하지 않는다.
- 다음 변경은 BE-023의 직접 판매 생성·수정 공통 입력 정책 중복을 검토한다. 신규 입금 취소/보정·예치금·자동 매칭 등의 MVP 후속 기능은 별도 업무 범위다.

## 35차 변경 — BE-023 판매 생성·수정의 공통 입력 정책 정리

작업일: 2026-10-05. 상태: 공통 Domain Policy·기본값 참조·HTTP/rollback 회귀·최종 검증 완료. BE-023 완료.

### 원인과 범위

- 일반 판매 생성과 수정이 거래처/품목 필수·경매장 금지의 조건과 메시지를 따로 관리했다. 생성은 판매 유형의 입금 기본값을 사용했지만 수정은 같은 값을 문자열로 지정했다. 현재 서로 다른 업무 결과를 재현한 버그 수정이 아니라 같은 정책을 바꿀 때 두 유스케이스가 어긋나는 변경 비용을 줄이는 BE-023 변경이다.
- 조회·저장·주입이 없는 작은 Sales Domain Policy가 거래처 필수·품목 개수·판매 유형에 맞는 거래처 여부를 소유한다. 입력은 Sales 유형·식별자·개수·경매장 여부의 값이며 다른 모듈 Entity/application DTO에 의존하지 않는다. 경매 생성의 기존 필수 메시지와 거래처 제한도 같은 정책에서 구별한다. 별도 workflow·registry·생성/수정 공통 service나 입력 DTO를 추가하지 않는다.
- 생성은 기존 null 유형의 DIRECT 해석 뒤 거래처 → 품목 → 활성 거래처 조회 → 유형 검사 순으로 검증한다. 수정은 기존 요청/저장 전표의 경매 수정 거절·작성중/입금 이력 검사를 먼저 수행하고 같은 직접 판매 입력 정책을 적용한다. 입력이 여러 조건을 동시에 위반할 때 거래처 필수와 경매 수정 제한이 먼저 반환되는 기존 순서를 유지한다.
- 수정의 입금 기본값은 기존 `SalesType.DIRECT.defaultPaymentStatus()`를 참조한다. null/공백의 기본값과 명시적 legacy 상태 문자열의 trim을 유지한다. 생성·수정의 서로 다른 paymentMethod 처리, 수정에서 지원하지 않는 품목 개수 변경, 일반/경매 상태 전이와 입금 정책은 이 공통 정책에 합치지 않는다.
- 생성 접수·영속 지문·원문/null/default 구별·replay 우선 판정은 기존 위치에 유지한다. 원문을 공통 정책 적용 전에 정규화해 지문으로 쓰지 않는다. 트랜잭션·파트너/난 묶음 잠금·기존 예약 해제/재예약·Mutation·이력·잔액·감사·응답 조립 순서와 생성/수정 HTTP 계약은 변경하지 않는다.

### 회귀 방어

- 제품 변경 전 기존 Farm fixture의 HTTP 시험 12건과 PostgreSQL 1건을 통과시켜 정책을 고정했다. 일반 판매 생성/수정의 거래처 없음·품목 없음·복합 위반·경매장 선택에서 동일한 `400 / VALIDATION_ERROR`, 공통 message와 도메인 details를 검사하고 기존 전표 응답이 보존되는지 확인한다. 도메인 문구는 `error.details`에 있으며 공통 `error.message`를 정책 메시지로 가정하지 않는다.
- null/명시적 DIRECT 유형과 null/공백/명시적 legacy 입금 문자열의 생성·수정 결과를 함께 검증한다. 경매 생성의 기본 입금 상태/방법·독립 필수 메시지·일반 거래처 금지, 일반 전표에 경매 유형을 보낸 수정 요청의 우선 거절도 고정했다. 기존 fixture를 확장하며 별도 데이터 생성 계층을 복제하지 않는다.
- 신규 PostgreSQL 회귀는 품목 개수 변경이 기존 예약 해제 뒤 거절되어도 전표·판매일·기존 예약·재고 이동·Mutation·감사를 보존하는지 확인한다. 일반 HTTP fixture의 테스트 트랜잭션만으로 rollback을 주장하지 않고 실제 최상위 service 트랜잭션을 실패시켜 원장 대사까지 검증한다.

### 검증

- 제품 변경 전 집중 일반 12건·신규 PostgreSQL 1건 성공(42초). 변경 후 집중 HTTP/상태/금액/영속 지문/architecture 5개 클래스 54건 성공(20초).
- 최종 백엔드 전체 `./gradlew test`: 132개 클래스 701건 성공. 관련 PostgreSQL `workE2eTest`: 판매 재고 21건·생성 접수 30건·배분 잠금 12건·판매 쓰기 query-count 19건, 총 4개 클래스 82건 성공. 시험의 실패·오류·생략은 없다. 일반/PG 시험과 첫 포맷 검사 시도는 2분 55초 소요했다.
- 시험 성공 후 명령의 마지막 `spotlessCheck`가 “0 lint error(s)”로 실패했고 단독 재시도에서도 같았다. 검사 산출물이 원본과 같은 것을 확인한 뒤 `spotlessApply spotlessCheck --rerun-tasks`로 포맷 task를 다시 실행해 7초에 성공했다. 규칙을 변경하거나 검사를 생략하지 않았다. 후속 `compileJava compileTestJava spotlessCheck`도 성공하며 모두 UP-TO-DATE로, 전체 시험의 소스 입력이 유지되는지 확인했다.
- 프론트엔드 `npm run check`, 최종 백엔드 `spotlessCheck`, `git diff --check`: 성공. 관련 PostgreSQL 검증에는 신규 품목 개수 rollback 외에 기존 생성 지문/replay·동시 생성·다른 요청 키 충돌·최초 snapshot·예약/출고/취소·교차 배분 잠금·최종 실패 rollback·query-count 회귀를 포함한다.
- HTTP 입력/응답·enum·validation annotation·DB schema·금액/수량/상태 정책·트랜잭션/잠금 구현의 변경은 없다. PostgreSQL 전체·benchmark·브라우저 E2E·운영 대사는 실행하지 않는다. OpenAPI/TypeScript 재생성·Flyway 추가는 필요하지 않다. 도메인/화면/운영 정책과 모듈 경계가 그대로이므로 기존 기능/architecture 문서에 클래스 목록을 추가하지 않는다.
- 전체 시험 이후에는 포맷 task의 산출물/검사 상태와 진행 문서의 완료 결과를 갱신했다. Java와 시험의 소스 입력 변경이 없어 전체 시험을 다시 실행하지 않았다. HTTP/저장 계약도 변경하지 않았다.

### 남은 범위

- BE-023은 실제 생성/수정의 공통 필수·거래처 제한과 입금 기본값 참조를 완료 범위로 삼는다. 전체 paymentStatus/salesStatus 문자열 모델(BE-026), 일반 metadata 수정 정책과 보정/실사 제한(BE-009), 입금 취소/보정 등의 후속 기능은 별도다.
- 다음 변경은 BE-024의 호출되지 않는 전략 옵션·주입 의존을 검토한다. 사용되지 않는 옵션을 새 기능으로 연결하는 것은 이번 정책 중복 정리의 범위가 아니다.

## 36차 변경 — BE-024 미사용 주입 의존과 전략 옵션 제거

### 완료 범위

- 전체 소스·테스트·현재 문서의 참조를 다시 확인했다. `ImmediateWorkExecutionService`의 `appliedEffectRepository`는 선언 외 사용이 없고, `requiresEverySourceResult`는 인터페이스 default와 이동 전략 override만 존재하며 호출되지 않는다. 직접 생성자 호출이나 해당 옵션을 사용하는 시험도 없다.
- 즉시 실행 service에서 미사용 Repository 필드·import를 제거해 Lombok 생성자의 필수 의존을 줄였다. 실제 효과 저장·조회 경로의 Repository는 유지한다.
- 구조 변경 전략에서 실행에 영향을 주지 않는 옵션과 이동 override를 제거했다. 원본별 결과 의무를 새 validation으로 연결하지 않는다. 실제 실행에서 사용하는 수량 검증·배분·혼합 품종 제한·속성 상속·계보 관계와 registry/handler는 유지한다. 제품 변경은 세 Java 파일의 미사용 선언 12줄 삭제다.
- 공개 업무 API·접수 지문·actor 정규화·트랜잭션·잠금·Mutation/효과/감사·저장/HTTP 계약의 변경은 없다. 즉시 명령의 반복 인자 전달 정리는 BE-025로 분리한다.

### 검증

- 집중 검증 `spotlessApply test --tests ...`: 전략 registry 4건·이동 수량 배분 3건·분갈이 8건·분주/합식 12건·일괄 이동 6건, 총 5개 클래스 33건 성공(30초). 이동의 ID 보존·폐기·N:M 수량 배분, 구조 결과·계보·부분 실행, 즉시 실행의 제목/actor·멱등 계약을 기존 회귀로 확인했다. 삭제된 내부 선언의 부재만 검사하는 시험은 추가하지 않는다.
- 최종 백엔드 `./gradlew test spotlessCheck`: 132개 클래스 701건 성공(2분 1초). 실패·오류·생략 0건. 컴파일과 application context 로딩도 성공했다.
- 프론트엔드 `npm run check`, `git diff --check`: 성공. 제품 소스에서 제거 대상의 참조가 남지 않은 것을 재검색했다.
- PostgreSQL `workE2eTest`·benchmark·브라우저 E2E·운영 대사는 미실행. 실제 DB 경계·수량/금액 정책·SQL·Flyway 변경이 없는 정리이므로 PostgreSQL E2E를 반복하지 않는다. OpenAPI/TypeScript 생성과 기능/architecture 문서 갱신도 필요하지 않다.
- 전체 검증 이후에는 진행 문서만 수정했다. 동작 변경 없이 동일한 전체 시험을 다시 실행하지 않는다.

### 남은 범위

- BE-024의 감사에서 지적한 미사용 주입과 전략 옵션 제거를 완료했다. 다음 변경은 BE-025의 즉시 실행 명령 전달·actor 정규화 정리이며 기존 지문과 실제 실행 값의 일치를 검증한다.

## 37차 변경 — BE-025 즉시 실행 명령 전달·actor 정규화 정리

### 완료 범위

- 즉시 실행의 두 공개 진입점에서 이미 만든 `ImmediateCommand`를 접수 지문과 내부 실행에 함께 사용한다. callback에서 명령을 풀어 8/9개 인자를 반복 전달하던 경로를 `requestKey`와 기존 명령의 두 인자로 줄였다. 공개 서명·새 범용 실행 계층·외부 모듈 계약은 추가하거나 변경하지 않는다.
- actor는 기존처럼 접수 전에 정규화하고 명령에 저장한다. 내부 실행에서 다시 정규화하지 않고 작업·효과·대상 완료에 동일한 값을 전달한다. 일반 모드의 null/공백 → null·trim과 데모 계정 치환은 유지한다. 실제 provider의 정규화가 재적용해도 같은 값임을 확인하고 제품 변경 전후 회귀로 고정했다.
- 기존 명령의 필드 이름·순서·값, null 대상 ID, 자동 이력 제목과 직접 전달 제목, 원문 payload의 actor/제목/키/메모/사유·날짜 표현을 보존한다. 요청 원문을 정규화한 실행 값으로 대체하지 않는다. 접수 namespace·키 처리·legacy 거절·replay 우선 판정·membership·최종 현재 응답 조회도 기존 위치에 유지한다.
- 작업 생성/대상 확정·상태 전이·효과·Mutation·감사의 실행 순서와 트랜잭션·잠금·DB schema·HTTP 계약은 변경하지 않는다. 대상 없는 공개 실행의 실제 외부 소비자를 새로 추가하지 않는다.

### 회귀 방어

- 새 parameterized 시험 10건은 대상 유무 × 일반 actor null/공백/trim 및 데모 actor null/위조 입력을 확인한다. 변경 전 코드에서 먼저 10건이 성공했다(8초).
- private record를 직접 호출하거나 메서드 인자 개수·actor 호출 횟수를 검사하지 않는다. 두 공개 업무 API의 접수 입력을 기존 literal JSON envelope의 지문과 비교하고, 실제 생성된 Work와 효과 명령·대상 완료의 actor/날짜/메모/상태·원문 payload를 확인한다. DB claim/replay/rollback의 증거는 이 mock 시험이 아닌 기존 PostgreSQL 회귀에서 확보한다.

### 검증

- 변경 후 집중 실행/지문/현장 동기화 payload/분갈이/보상·실사/actor 6개 클래스 30건 성공(30초).
- 최종 백엔드 `./gradlew test workE2eTest --tests '*WorkIdempotencyPostgresE2ETest' spotlessCheck`: 일반 133개 클래스 711건·관련 PostgreSQL 1개 클래스 11건 성공(전체 2분 25초). 실패·오류·생략은 없다.
- PostgreSQL은 즉시 요청의 동시 실행·효과/접수/membership 단일 생성, 실패 후 전체 rollback과 같은 키 재시도, 구형 분갈이·현장 동기화 접수 지문/replay와 snapshot 보존, 변경 원문 충돌, 원문 없는 legacy 거절·DB 효과 identity 제약을 기존 회귀로 확인한다. 실제 트랜잭션 경계를 변경하지 않지만 영속 지문에 쓰이는 명령을 실행에도 사용하는 호환성 위험을 이 관련 범위로 검증했다.
- 프론트엔드 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. PostgreSQL 전체·benchmark·브라우저 E2E·운영 대사는 미실행. 공개 API/enum/validation·저장 schema·모듈 경계나 도메인 정책 변경이 없어 OpenAPI/TypeScript 생성·Flyway·기능/architecture 문서 갱신은 필요하지 않다.
- 전체 검증 후 진행 문서만 수정했다. 제품·시험 소스가 유지되어 전체 검증을 반복하지 않는다.

### 남은 범위

- BE-025의 내부 반복 전달과 actor 재정규화 제거를 완료했다. 다음 변경은 BE-026의 Work/Settlement 멱등 키 충돌 오류 계약이며 기존 payment 소비자의 HTTP 호환성을 먼저 검토한다.

## 38차 변경 — BE-026 수동 입금 멱등 키 충돌의 오류 계약

### 완료 범위

- Work 접수·보정의 기존 `409 / IDEMPOTENCY_KEY_REUSED`와 수동 입금의 `400 / VALIDATION_ERROR` 차이를 재확인했다. 입금 원장의 금액/입금일 비교는 그대로 두고, 비교로 확인한 키 재사용만 공통 `ConflictException`과 `IDEMPOTENCY_KEY_REUSED`로 반환한다. 일반 판매 전표·경매 정산 모두 같은 원장 정책을 사용한다.
- 입력 형식/필수/범위·초과입금 등 기존 업무 검증은 400을 유지한다. 현재 상태의 선행 검증, 잠금 순서·트랜잭션·입금/연결 이벤트·잔액·감사·DB UNIQUE와 replay 처리 순서는 변경하지 않는다. 모든 `IllegalArgumentException`을 409로 바꾸거나 새 오류 framework/enum을 만들지 않는다.
- 같은 대상·키의 비교 범위는 기존처럼 금액과 입금일이다. 키의 trim과 대상별 범위를 유지하며, 방법·입금자·worker·메모만 바뀐 재전송도 기존 반영 결과를 반환하고 저장된 이벤트를 수정하지 않는다. 실제 추가 입금에는 새 키를 사용한다.
- 저장 DB schema·금융 계산·성공 응답과 요청 DTO는 유지하지만 충돌 HTTP status/code는 의도적으로 변경한다. API 가이드에 400 입력 오류·409 키/상태/DB 충돌 선택 기준과 기존 도메인별 code 보존 원칙을 추가하고, 입금 정책·Domain Rules에 새 응답과 처리 방법을 반영했다.

### 소비자·명세와 회귀 방어

- 저장소의 두 입금 API adapter는 공통 `requestApi`를 사용하고 입금 패널은 오류 메시지를 표시한다. 기존 400/code에 의존한 분기를 찾지 못했으며 별도 프론트 동작 변경은 필요하지 않다. 외부 소비자의 존재/배포는 확인하지 않았다. 기존 400에 의존한 외부 연동에는 status/code 갱신이 필요함을 API 가이드에 명시한다.
- 두 Controller에 200·400·409 설명과 공통 ErrorResponse schema를 선언하고 `python3 scripts/generate_openapi.py`와 `npm run api:types`로 전체/slice/TypeScript를 생성했다. 생성 결과의 두 operation에서 성공 응답 schema가 보존되고 400/409가 ErrorResponse를 참조하는지 확인했다. 생성 파일은 직접 수정하지 않는다.
- HTTP 회귀에서 일반 판매·경매의 동일 요청 성공/replay와 금액/입금일 변경의 409/code·공통 오류 envelope를 검사한다. 초과입금과 입력 validation의 기존 400을 별도로 유지한다. 원장 application 시험도 메시지 문자열 대신 ConflictException의 code를 확인한다.
- PostgreSQL 신규 6건은 두 대상의 완납 뒤 금액/입금일 충돌과 같은 키의 다른 금액 동시 요청을 검증한다. 충돌 전후 대상·입금/연결 이벤트·잔액·감사의 전체 행 snapshot을 비교하고 이후 원래 요청과 metadata만 바뀐 요청의 replay가 저장 상태를 보존하는지 확인한다. 동시 요청은 성공 1건·정확한 키 충돌 1건과 이벤트 2개만 기록되며 성공 요청의 재전송도 변경하지 않는다. 기존 경매 fixture와 snapshot helper를 함께 사용한다.

### 검증

- 집중 입금 HTTP/원장 2개 클래스 26건 성공(32초), 기존 프론트 ApiError 처리 3건 성공. 신규 PostgreSQL 6건 성공(29초).
- 최초 신규 PostgreSQL 시도는 병렬 Callable의 `var` 타입 추론이 Object가 되어 시험 컴파일에서 실패했다. 결과 변수를 `List<ApiResult>`로 명시해 수정했으며 PostgreSQL 실행 실패나 제품 오류는 아니었다. 이후 집중/최종 검증에서 컴파일과 시험이 모두 성공했다.
- 최종 `./gradlew spotlessApply test workE2eTest --tests '*PartnerSettlementPostgresE2ETest' spotlessCheck`: 일반 133개 클래스 711건·관련 PostgreSQL 1개 클래스 19건 성공(2분 20초). 실패·오류·생략 0건. 신규 충돌 외 기존 동시 입금·재전송·잔액/정산 재구축·감사/호출자 rollback·외래키·잠금 회귀도 포함한다.
- 프론트엔드 `npm run check`, OpenAPI/TypeScript 생성, 백엔드 포맷 검사·`git diff --check`: 성공. PostgreSQL 전체·benchmark·브라우저 E2E·운영 대사·외부 소비자 배포는 미실행. Flyway와 운영 데이터 변경은 없다.
- 최종 전체 검증 이후 진행 문서만 수정했다. 제품·시험·명세·생성 타입의 입력이 유지되어 전체 검증을 반복하지 않는다.

### 남은 범위

- BE-026의 수동 입금 키 충돌 구별·오류 선택 기준·확인된 저장소 소비자 호환과 관련 회귀를 완료했다. 기존 업무의 다른 상태 검증/도메인별 code를 일괄 변경하지 않는다. 다음 변경은 BE-027의 경매 변경 응답 mapper N+1을 추적한다.

## 39차 변경 — BE-027 경매 쓰기 응답의 결과 행 N+1 제거

### 완료 범위

- lot root 잠금과 결과/반환의 완료 접수 replay 뒤 기존 시도의 결과 행을 소유 Repository로 일괄 로딩한다. 변경·cascade flush·최초 응답 조립 전에 같은 managed Entity의 collection을 초기화해 시도별 lazy SQL을 제거한다. root 조회에 shipment fetch/부모 잠금을 추가하지 않는다.
- 쓰기 응답은 기존 lot collection으로 조립한다. 조회 assembler의 경매일 정렬로 교체하지 않아 과거 날짜로 추가한 신규 시도도 기존 append 위치를 유지한다. 결과 행의 일괄 조회에는 생성 ID 순서를 명시하며 최초 응답·접수 snapshot의 자식 ID/행 순서를 유지한다. snapshot 시점·지문·replay·수량 검증·트랜잭션/rollback은 변경하지 않는다.
- GET도 같은 결과 행 bulk Repository를 사용하며 collection fetch join을 root pagination에 추가하지 않는다. 전체 이력의 Entity 적재량은 응답 이력 수에 비례한다. 하위 이력 상한/별도 pagination은 BE-034에 남긴다. 새 조회 규칙만 architecture 문서에 반영했고 API/DB schema는 바뀌지 않았다.

### 측정·회귀와 검증

- 새 PostgreSQL 회귀는 독립 시도 1/10/50개와 시도별 결과 2행·상태 이력을 갖는 detached fixture에서 상태 변경·결과 등록·반환·무변경 수량 보정의 최상위 트랜잭션을 호출한다. commit을 포함한 prepareStatement 수와 Entity/collection 적재 증적을 `build/work-query-count/auction-write-*.json`에 남긴다. 전체 응답의 기존 시도/행/이력 ID 순서·검토 상태·수량 capability와 신규 ID·과거 날짜 append·receipt replay를 검증한다.
- 제품 변경 전 SQL은 상태 변경 9/17/57, 결과 등록 11/20/59, 반환 10/19/59, 무변경 보정 6/15/55였다. 응답 계약 assertion은 통과하고 조회 상한에서 12건 중 8건이 실패해 N+1을 재현했다. 최초 시험의 잘못된 검토 enum 이름은 실제 MANUAL_REVIEW로 수정한 뒤 측정했다.
- 변경 후 같은 순서로 상태 변경 9/8/8, 결과 등록 11/11/10, 반환 10/10/10, 무변경 보정 6/6/6으로 건수 증가와 무관해졌다. 시퀀스 할당의 단일 SQL 차이를 허용하면서 commit 포함 상한 14를 고정했다. 신규 12건과 관련 일반 경매/수량 정책 집중 검증 성공(49초).
- 최종 일반 `./gradlew test`: 133개 클래스 711건 성공. 관련 PostgreSQL 쓰기 조회 12건·요청 멱등성 40건·수량 이력 16건, 3개 클래스 68건 성공. 기존 root 잠금 경쟁·늦은 실패 rollback·최초 snapshot/replay·수량 변경 제한 회귀도 포함한다. 실패·오류·생략 0건; 백엔드 검사 2분 43초.
- 프론트 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check`: 성공. PostgreSQL 전체·benchmark 전체·브라우저 E2E·운영 지연/lock duration 실측은 미실행. OpenAPI/TypeScript 재생성과 Flyway는 필요하지 않다. 최종 검증 후 진행 문서만 변경했다.

### 다음 범위

- BE-027 완료. 요청한 다음 단계는 BE-028 직접 계보 조회와 BE-029 배치 profile의 적재 범위 정리다. 각각 별도 커밋으로 진행한다.

## 40차 변경 — BE-028 직접 계보의 현재 참조 N+1 제거

### 완료 범위

- Work application 조회의 실행 ID로 이미 포함된 직접 연결을 먼저 제외한다. 남은 sources/results 연결의 양 끝 그룹 ID와 Work 계보의 그룹 ID를 합쳐 기존 Farm 소유 상세 Repository로 한 번에 로딩한다. 같은 persistence context의 직접 연결 Entity에도 위치 tree·품종·입고가 초기화되므로 기존 mapper를 유지한다.
- 직접 연결의 생성 시각/ID 순서, Work 실행 계보의 관계·순서, 현재 품종/위치와 농장 업무일 기준 연령 의미를 유지한다. 최상위 readOnly 트랜잭션·404·원장/효과 snapshot·쓰기/잠금은 바꾸지 않는다. 조회 시점의 현재 참조를 과거 snapshot으로 대체하지 않았으며 API/DB schema 변경은 없다.
- 전체 계보 응답의 Entity 적재량은 연결 수에 비례한다. 대형 IN 분할·하위 계보 상한은 BE-032/034의 별도 범위이며 이번 수정으로 heap/무제한 이력 위험까지 해결한 것으로 표시하지 않는다.

### 측정·회귀와 검증

- 새 PostgreSQL 회귀는 들어오는 연결과 나가는 연결 각각 1/10/50개를 생성한다. 중심과 각 연결의 위치·품종·입고는 독립적이며 fixture commit 후 서비스의 새 트랜잭션에서 전체 DTO를 조립한다. 연결 ID/관계/작업/수량/순서, 현재 위치·품종·입고일 기반 7년생을 함께 검증한다.
- 제품 변경 전 SQL 20/110/510회, 변경 후 5/5/5회. 최초 3건은 응답 assertion 통과 후 조회 상한에서만 실패했다. 양방향·입고를 포함하는 fixture여서 감사 당시 단방향 실험의 수치와 다르다. Entity load 20/146/706은 전후 동일하고 collection fetch 0회다. `build/work-query-count/legacy-lineage-*.json`에 측정 증적을 기록한다.
- 관련 일반 계보 integration/codec 조회 및 신규 PostgreSQL 3건 집중 검증 성공(42초). 최종 일반 `./gradlew test`: 133개 클래스 711건 성공. 관련 PostgreSQL 직접 계보 3건·구조 handler/계보/취소/replay 및 잘못된 결과 rollback 7건, 2개 클래스 10건 성공. 실패·오류·생략 0건; 백엔드 검사 2분 27초.
- 프론트 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check` 성공. PostgreSQL 전체·benchmark 전체·브라우저 E2E·운영 heap/지연 실측은 미실행. 최종 검증 후 진행 문서만 수정했다.

### 다음 범위

- BE-028의 조회 수 문제를 해결했다. 다음은 BE-029 배치 profile의 불필요한 난 묶음 적재 제거다.

## 41차 변경 — BE-029 검증 중 발견한 배치 profile 규칙 교체 오류

### 완료 범위

- BE-029 조회/수정 PostgreSQL fixture에서 기존 키의 값만 바꾸는 전체 규칙 교체가 `uk_bed_zone_capacities_rule`에 실패했다. Hibernate의 새 Entity INSERT가 orphan DELETE보다 먼저 실행돼 같은 `(구역, 배치 유형, 화분, 모드)` 키가 잠시 중복된다. H2 기반 교체 테스트는 이 PostgreSQL 제약을 검사하지 않아 통과해 왔다. 감사의 적재 최적화와 별도의 버그 수정으로 저장한다.
- 새 규칙 전체를 기존 Domain Policy로 먼저 검증한 뒤 빈 목록으로 교체해 삭제를 flush하고 새 규칙을 적용한다. 같은 최상위 트랜잭션 안에서 원래 삭제·신규 규칙·감사가 함께 확정/rollback하며 commit/잠금/인가/API/DB schema를 추가하지 않는다. 행을 in-place 수정하는 방식으로 바꾸지 않아 기존 전체 교체 의미와 동일 값의 감사 생략을 유지한다.
- 새 PostgreSQL 회귀는 기존 키의 3개 모드 수정·신규 행 ID·동일 값 재요청의 감사 생략, 삭제 flush 후 감사 DB CHECK 실패·원래 규칙의 ID/모든 DB 필드 복구·감사/재고 보존·재시도 성공을 검증한다. 테스트 실패 주입 CHECK는 기존 감사는 보존하고 신규 감사 INSERT만 검사하도록 `NOT VALID`로 추가/정리한다. 제품 migration이나 제약은 바꾸지 않는다.

### 검증

- 집중 검증: 기존 배치 profile/감사/정책 일반 테스트 성공, 새 PostgreSQL 동일 키/감사 rollback/retry 회귀 1건 성공(24초). 별도 적재 실험 9건은 응답·감사 검증 통과 후 난 묶음 적재 0 조건에서 실패해 BE-029의 남은 문제를 확인했다. 최초 fixture의 화분 표시 기대를 실제 정규화 값 `3"`에 맞췄다.
- 최종 일반 `./gradlew test`: 133개 클래스 711건 성공. 관련 PostgreSQL 교체/rollback 1건·Farm 조회 4건, 2개 클래스 5건 성공. 실패·오류·생략 0건; 백엔드 검사 2분 27초. 프론트 `npm run check`, `spotlessCheck`, `git diff --check` 성공.
- PostgreSQL 전체·benchmark 전체·브라우저 E2E·동시 profile 수정 검증은 미실행. 이번 변경은 동시 writer 제어를 추가하지 않는다. 최종 검증 후 커밋 대상에는 진행 문서만 수정했고 적재 회귀는 다음 변경에서 별도로 저장한다.

### 다음 범위

- 규칙 교체 오류 수정을 별도 커밋한 뒤 BE-029의 profile 전용 graph를 적용한다. 조회·수정 SQL이 고정돼도 현재 graph의 불필요한 난 묶음 적재는 남아 있다.

## 42차 변경 — BE-029 배치 profile의 불필요한 난 묶음 적재 제거

### 완료 범위

- profile 조회·수정의 구역 조회만 위치 tree와 capacities를 fetch하는 Farm 소유 전용 graph로 교체했다. 응답과 감사가 읽지 않는 orchidGroups를 제거해 두 collection 간 join 증폭 경로도 없앴다. 재고가 필요한 입고/구역 상세의 기존 graph는 유지한다.
- 용량 정책·응답 필드/정규화·모드 순서·감사 before/after·동일 값 감사 생략은 유지한다. 41차의 같은 키 교체를 위한 삭제 flush와 전체 rollback도 그대로다. 현재 규칙·위치 조회를 projection/역사 snapshot으로 바꾸거나 root 잠금·트랜잭션을 변경하지 않았다. API/DB schema 변경과 신규 migration은 없다.

### 측정·회귀와 검증

- 새 PostgreSQL 회귀는 서로 독립인 난 묶음 1/10/50개와 용량 규칙 1/3/5개를 조합한 9개 fixture를 사용한다. fixture commit 후 각각 새 서비스 트랜잭션에서 조회·수정하고 수정 commit까지 측정한다. 전체 응답/모드 순서·정규화 값·감사 before/after·변경 필드·위치·주체와 재고 모든 DB 필드 보존을 검증한다.
- graph 변경 전 SQL은 조회 1회, 수정 4~6회로 작았지만 난 묶음 적재는 조회·수정 모두 1/10/50개였다. 41차 교체 오류를 해결한 뒤 9건 모두 응답/감사 검증 통과 후 적재 상한에서만 실패했다. 최종 전용 graph에서는 난 묶음과 품종 적재 모두 0, 전체 Entity는 규칙 수 + 위치 3개(4/6/8개), 조회 SQL 1회·수정 4~6회로 그룹 수에 무관하다. 시퀀스 할당에 의한 단일 SQL 차이를 허용한다.
- `build/work-query-count/placement-profile-*.json`에 조회/수정 prepareStatement·전체 Entity·그룹/품종/규칙 load 수를 별도로 남긴다. 규칙/Entity 적재 검사는 상한을 사용해 향후 projection 전환도 허용하며 SQL count만으로 과다 적재를 완료 판정하지 않는다. JDBC 실제 row 수·round trip·heap·운영 지연은 측정하지 않았다. 규칙의 조회/교체 자체 비용은 규칙 수에 비례한다.
- 집중 검증: 기존 배치 profile/감사/정책 일반 테스트와 신규 PostgreSQL 적재 9건·동일 키 교체/감사 rollback/retry 1건 성공(44초). 최종 일반 `./gradlew test`: 133개 클래스 711건 성공. 관련 PostgreSQL 적재 9건·교체/감사 rollback/retry 1건·Farm 조회 4건, 3개 클래스 14건 성공. 실패·오류·생략 0건; 백엔드 검사 2분 32초.
- 프론트 `npm run check`, 백엔드 `spotlessCheck`, `git diff --check` 성공. PostgreSQL 전체·benchmark 전체·브라우저 E2E·운영 rows/heap/지연·동시 profile 수정 실험은 미실행. OpenAPI/TypeScript 재생성은 필요하지 않다. 최종 검증 뒤에는 진행 문서만 수정했다.

### 다음 범위

- 요청한 BE-027~029를 완료한다. 이후 범위는 BE-030의 Work 요약을 위한 전체 target Entity 적재 검토이며 이번 요청에 포함하지 않는다.

## 43차 변경 — BE-030 Work 요약의 대상/child Entity 적재 제거

### 완료 범위

- Work 소유 Repository에서 `(작업 ID, 입고 ID)`를 group/min(target ID)로 조회해 처음 등장한 순서와 distinct 의미를 유지한다. 기존처럼 excluded target도 출처 판단에 포함한다. snapshot JSON·대상/실행 Entity는 목록 요약에서 로딩하지 않는다.
- parent별 전체 child 수는 scalar DB 집계로 읽는다. 상태별 child 제외는 추가하지 않는다. 페이지의 nested family가 겹치는 경우 기존 조립 순서의 마지막 family 우선순위를 유지해 root·child·parent가 없는 페이지와 과거 깊은 연결의 기존 응답 의미를 보존한다. receipt membership·생성 batch는 그대로이며 동일 receipt는 구조적 연결로 계산하지 않는다.
- 상세/실행/잠금/효과·receipt snapshot·readOnly 경계·API/DB schema는 변경하지 않는다. 기존 Repository의 Entity 조회는 다른 소비자를 위해 유지한다. 페이지 root·receipt 크기와 모든 입고 ID의 응답 크기는 별도 한계이며 이번 변경이 모든 요약 메모리를 상수로 만든 것은 아니다.

### 측정·회귀와 검증

- PostgreSQL 회귀는 root 10개 고정, 각 root의 target 20/200/2,000개와 child 1/10/50개를 조합한다. child의 큰 details JSON, 중복/순서가 다른 입고 참조·제외 대상·페이지 밖 receipt batch를 포함한다. HTTP 응답의 pagination/total·진행 대상 수·입고 순서/출처·연관 수·생성 batch와 snapshot 필드 미노출을 검증한다.
- 제품 변경 전 SQL 8/8/8회, target load 200/2,000/20,000개, WorkOperation load 20/110/510개. fixture의 JSON 정수 node 종류 비교를 ID 값 비교로 고친 후 3건 모두 응답 검사는 통과하고 적재 조건에서 실패했다. 변경 후 SQL 8/8/8회, target load 0, WorkOperation load 10, 전체 Entity 14개로 고정됐다. `build/work-query-count/work-summary-*.json`에 증적을 남긴다.
- 기존 Work benchmark(100×20)는 SQL 상한뿐 아니라 요약 target load 0·작업 load ≤100을 검사하고 각 API의 Entity 적재량도 측정 결과에 남긴다. 관계 unit 회귀에 두 페이지 순서의 nested family 사례를 추가했다. JDBC rows·allocation·heap·운영 지연/plan은 이 적재 회귀의 직접 측정 대상이 아니다.
- 초기 집중 검증: 관계 unit 및 PostgreSQL 3건 성공(34초). 최종 일반 `./gradlew test`: 133개 클래스 713건 성공. 관련 PostgreSQL 요약 적재 3건·Work 보정 감사 20건, 2개 클래스 23건 성공. 기존 Work benchmark 1건도 `-PworkBenchmarkEnforce=true`로 성공했으며 목록 SQL 7회·target load 0·operation load 100을 확인했다. 실패·오류·생략 0건; 백엔드 검사 3분 2초.
- 프론트 `npm run check`, `spotlessCheck`, `git diff --check` 성공. PostgreSQL 전체·검색 benchmark·브라우저 E2E·실제 JDBC rows/heap/운영 부하 실측은 미실행. 최종 검증 뒤에는 진행 문서만 수정했다.

### 다음 범위

- BE-030 조회 적재 개선을 완료한 뒤 BE-031 품종/자동 그룹의 요약 집계를 진행한다.

## 44차 변경 — BE-031 품종 DB 집계·자동 그룹 scalar 순차 조립

### 완료 범위

- 품종 페이지/단건의 양수 묶음 개수·전체 수량·판매 가능량은 Farm 소유 QueryDSL 집계로 조회한다. 기존처럼 양수이면 종료/경고 상태도 개수/전체량에 포함하고 판매 가능량만 Domain Policy의 상태 목록과 예약 잔량/0 하한을 적용한다. 품종 응답에 불필요한 그룹/위치 Entity를 적재하지 않는다.
- 현재 그룹 ID/품종의 scalar cursor와 500개 Work 날짜 입력 batch로 최신 날짜를 모은다. 전 그룹 Entity·ID·Work 날짜 map을 한꺼번에 보관하지 않고 품종별 최대 날짜만 유지한다. Work가 현재 그룹 ID에 대해 완료/제외 여부를 판단하는 기존 계약을 유지한다. 현재 품종 연결 대신 과거 target의 품종 snapshot으로 집계하지 않는다.
- 검증 중 기존 페이지 조립이 모든 품종에 페이지 전체 최신 작업일을 넣는 결함을 확인했다. 자기 품종의 최신일을 반환하도록 바로잡았으며 작업이 없는 품종은 null이다. 단건/연결 상세와 의미를 맞췄고 DOMAIN_RULES에 기준을 명시했다. API shape·enum·validation은 변경하지 않아 명세/타입 재생성은 필요하지 않다.
- 자동 그룹은 공통 QueryDSL 조건/정렬로 scalar summary/member cursor를 읽는다. summary는 key별 개수/수량/고유 위치만 누적하고 전체 member DTO 목록을 만들지 않는다. member는 현재 연령 필터 통과 후에만 DTO를 만든다. 기존 연령 함수를 공통 사용해 입고일 우선, UTC 생성일의 농장 날짜, 윤년·미래일 0 하한·미지정 나이·group key를 유지한다. 저장 age_year 조건으로 바꾸지 않았다.
- scalar stream은 caller read/write 트랜잭션 안에서 fetch size 500으로 처리하고 닫는다. 다중 모듈 조회의 전체 결과가 하나의 DB snapshot이라는 보장은 추가하지 않는다. summary key/고유 위치 수와 반환 member DTO 크기, DB 후보 scan/sort·Java 연령 계산은 여전히 데이터 수에 영향받는다. 호환 member/연결 상세의 pagination/상한 변경은 BE-034에 남긴다.

### 측정·회귀와 검증

- 새 PostgreSQL 회귀는 품종 3개 고정, 첫 품종의 묶음 1/500/5,001개 + 다른 품종 1개·비어 있는 품종을 구성한다. 정상/주의/종료·예약·서로 다른 두 위치·현재/저장 품종명이 다른 자료·품종별 완료 날짜를 포함한다. 합계/판매량/최신 입고·작업일, 자동 그룹 수량/고유 위치/현재 연령 및 전체 member DTO의 기존 Entity mapper 호환을 검증한다.
- 품종 변경 전 묶음 load 2/501/5,002개, 전체 Entity 8/508/5,009개·SQL 5회. 3건 모두 다른 품종의 최근 작업일 assertion에서 기존 결함을 재현했다. 변경 후 묶음 load 0·Entity 3개, SQL 6/7/16회(5 + ceil(양수 그룹/500))다. 조회 수 증가를 허용하는 대신 scalar/ID batch 크기와 Entity 상한을 방어한다.
- 자동 그룹의 새 scalar 경로에서는 summary/member 각각 SQL 1회·Entity load 0이다. 해당 수치는 품종 변경 전후 측정이며 자동 그룹 구형 경로의 별도 실행 baseline은 하지 않았다. `build/work-query-count/orchid-summary-*.json`에 단계별 SQL/Entity 증적을 남긴다. 실제 JDBC row count·allocation·heap은 미측정이며 fetch-size 설정만으로 peak heap을 입증했다고 취급하지 않는다.
- 기존 품종/감사/자동 그룹 일반 회귀 및 첫 PostgreSQL 3건 집중 검증 성공(50초). 윤년 두 경계·미래 입고·UTC 생성일의 농장 날짜 두 경계·null 연령 6건을 추가했다. 추가 6개 날짜 경계까지 PostgreSQL 신규 9건 집중 검증 성공(29초). 최종 일반 `./gradlew test`: 133개 클래스 713건 성공. PostgreSQL 요약/연령 9건·기존 Farm 조회 4건, 2개 클래스 13건 성공. 실패·오류·생략 0건; 백엔드 검사 2분 38초.
- 프론트 `npm run check`, `spotlessCheck`, `git diff --check` 성공. PostgreSQL 전체·전체 benchmark·브라우저 E2E·peak heap/실제 cursor fetch round trip·운영 plan/부하는 미실행. 최종 검증 후에는 진행 문서만 수정했다.

### 다음 범위

- BE-031 요약 적재 개선 후 BE-032 반복 검색/큰 ID 입력을 진행한다.

## 45차 변경 — BE-032 검색 조건 일괄 조회·대량 ID 바인딩/참조 입력 분할

### 변경 범위

- 기존 SearchScalabilityBenchmark는 일치 거래처 501/5,001개와 문구 공백 하나의 total/마지막 페이지·SQL 수·호출 스레드 할당량을 검사한다. 모듈 소유 경계를 지키는 대신 경매의 각 공백 경계와 전체 이름/경매장 조건이 Partner keyset scan을 반복하고, Sales/Auction root 및 Work history는 전체 ID를 개별 SQL 인자로 확장했다. 기존 Work/계보 query-count 상한만으로 대량 IN 위험을 검출하지 못한다.
- Partner의 다중 검색 조건을 중복 제거하고 32개씩 묶어 하나의 OR keyset scan에서 조건별 Boolean 일치 값을 읽는다. 각 scan은 500행으로 제한하고 조건별 전체 ID를 보존한다. 단일 조건은 기존 경로를 유지한다. 비활성 거래처, null 대표/전화, DB case/LIKE literal escape와 ID 오름차순은 기존 단일 검색과 일치한다. 경매의 공백 경계·전체 이름·경매장 정확 일치 조건을 한 번에 전달한다.
- Sales/Auction의 500개 초과 ID membership과 Work 이력의 전체 범위 membership은 Long 배열 하나의 `= ANY` 바인딩으로 바꾼다. Hibernate SQL fragment/명시 boolean cast는 Repository 내부에만 있으며 식별자 값은 인자로 전달한다. 다른 모듈 테이블을 읽지 않고 array containment 연산 대신 scalar equality ANY를 사용한다. Work의 root page/count·계획일/ID 정렬·제외 대상·효과만 연결된 작업·EntityGraph를 유지한다. 여러 범위의 독립 페이지를 합성하지 않는다.
- Partner 전체 표시 정보, Farm 읽기용 계보/collection 상세 참조와 Work 최신 날짜는 중복 제거한 ID를 500개씩 조회한다. Farm 쓰기 상세/잠금 조회와 작업 효과의 snapshot/transaction 경계는 바꾸지 않는다. HTTP DTO·enum·validation·DB schema 변경이 없어 OpenAPI/타입 생성은 하지 않는다.
- 전체 matching ID/배열 전송량은 여전히 일치 수에 비례한다. 조건 chunk 수, root 검색 조건 수, 후보 scan/sort와 호환 전체 계보/이력 응답 크기 역시 증가한다. 한 번의 검색 전체가 같은 DB snapshot이라는 보장은 추가하지 않는다. 완전한 상수 메모리·임의 길이 검색의 SQL 상한·운영 plan을 입증했다고 취급하지 않는다. 전체 목록 상한·indexed 검색 정책은 BE-034/035 범위다.

### 검증

- 기존 H2 Core/collection/Work 이력 집중 검증 성공. 신규 H2 5,001개 membership은 실제 전표 1개와 없는 ID를 섞어 total/content를 검증한다. PostgreSQL 신규 9건은 검색 조건 33개/중복/빈 입력과 기존 단일 검색 비교(500/501/5,001명), 표시 참조 0/500/1,001개·중복·누락 오류, Work 범위 1,500/1,503/15,003개에서 최신일/제외 대상·효과만 연결된 작업·페이지 전체 건수/정렬·SQL 인자 수를 검증한다. 계보 기존 3건에 501 source+501 result를 추가해 양방향 응답·순서·현재 연령과 SQL 인자 500개 상한을 확인했다. 집중 일반 22건·PostgreSQL 13건 성공.
- 기존 검색 benchmark를 501/5,001/70,001명과 공백 1/20개로 확대했다. 전역 total·마지막 한 행을 유지하고 Sales SQL 5/14/144회, Auction 7/16/146회로 공백 수에 따른 scan 증가를 막는다. 이전 공백 한 개 Auction 8/17회에서 7/16회로 줄었다. 최대 SQL 인자는 Sales 5개, Auction 공백 1/20개에서 7/45개이며 일치 수와 무관하다. 실제 PostgreSQL 70,001개의 배열 membership을 통과했지만 구형 경로의 같은 규모 인자 오류를 실행해 재현한 것은 아니다.
- 로컬 70,001명 Sales 약 568ms/18MB, Auction 공백 1개 약 1,132ms/37MB, 20개 약 4,191ms/112MB의 호출 스레드 할당량을 기록했다. warm-up 후 한 번의 샘플이며 process peak heap·운영 SLA·안정적인 p95가 아니다. keyset/조건 판정·전체 ID 전송 비용은 남는다. `build/work-benchmark/partner-search.json`에 SQL/인자/시간/할당량, `partner-search-plan-*.json`에 소유 Sales scalar 배열 조건의 EXPLAIN ANALYZE/BUFFERS를 저장했다. 해당 plan은 API 전체 조인이 아니며 broad match에서 Seq Scan+Sort, 70,001행에서 external merge 1,520KB/약 101ms가 관측됐다. 작은 fixture plan만으로 운영 index를 추가하지 않는다.
- 확장 검색 benchmark 1건 성공(43초). 최종 `./gradlew test` 133개 클래스 714건, 관련 PostgreSQL 4개 클래스 25건(신규 참조 9·계보 4·Work 요약 3·품종/자동 그룹 9), 기존 Work benchmark 1건과 `spotlessCheck` 성공(3분 8초). 실패·오류·생략 0건. 검색/Work benchmark는 각각 한 번의 완료 검증으로 실행했다.
- 프론트 `npm run check`, `git diff --check` 성공. 최종 검증 후 변경은 진행/아키텍처 문서뿐이다. PostgreSQL 전체·전체 benchmark·브라우저 E2E·peak heap·운영 DB/부하/전체 API plan은 미실행.

### 다음 범위

- 요청한 BE-030~032를 마무리한다. 이후 BE-033 경매 대시보드 집계는 이번 요청에 포함하지 않는다.

## 46차 변경 — BE-033 Mutation 배치 검사의 구역별 scalar 상태·구간 index

### 변경 범위

- 감사의 실제 BE-033은 Mutation 배치 placement 검사이며, 이전 결과의 다음 범위를 경매 대시보드라고 안내한 것은 잘못된 항목 설명이다. 기존 Engine은 입고/구조 변경 결과마다 구역 Entity 목록을 읽어 자동 정렬·겹침을 검사하고 결과 저장 사이의 조회가 AUTO flush를 유발한다. 일괄 이동은 구역 조회 후 결과 쌍을 전부 비교하고 복원도 기존/새 배치를 중첩 순회한다.
- zone 잠금 뒤 활성 그룹의 ID/구역/구간/표시 순서 scalar 값을 500개 구역 ID씩 읽는다. 구역별 점유 구간의 union과 1칸 이상 빈 자리 index를 만들고 새 결과도 바로 예약한다. 첫 빈 1칸·2자리 정규화·양 끝 접촉 허용·명시 구간·null 구간 건너뛰기·입력 결과 순서를 유지한다. 기존의 최대 sort 집계는 수량 0을 포함한 구역 전체 의미로 유지한다.
- 구조 변경은 원본 수량/구간 변경 후 최초 placement 조회를 수행해 해제 위치 재사용과 현재 원본 잔여를 반영한다. 제외 ID는 기존 명령 계약을 따른다. 이동은 전체 기존 배치를 먼저 검사한 뒤 새 결과를 index로 비교해 기존 배치/결과 충돌의 단계별 우선순위를 유지한다. 복원은 원래 구간과 구역별 표시 순서 집합을 검사하며 같은 요청의 이전 복원도 반영한다. 여러 충돌 종류가 동시에 있는 복원은 구간 충돌을 우선 보고하지만 안정적인 HTTP 오류 분류는 동일하다.
- request-local 상태이며 공유 cache는 추가하지 않았다. replay의 두 단계 확인, inbound/group/zone 잠금 순서, Mutation fence·Entry·효과·감사 원자성은 유지한다. 동시 요청은 zone lock 이후 별도의 scalar 조회로 앞선 commit을 확인한다. HTTP 요청/응답·명세·DB schema 변경은 없다.

### 검증

- 순수 구간 index 회귀는 고정 seed의 100개 fragment/중첩/음수 시작 fixture에서 기존 전체 scan의 overlap/첫 빈 자리 결과와 20회 연속 예약을 비교한다. 기존 H2 Mutation/배치/Work 집중 회귀와 최종 일반 `./gradlew test` 134개 클래스 715건 성공.
- 새 PostgreSQL 회귀는 결과 R=1/10/50 × 기존 그룹 G=1/100/1,000의 9조합에서 구역 조회·기존 Entity 적재·flush와 전체 transaction 경과 시간을 기록한다. 자동 위치/표시 순서/입고 연결·응답 순서와 replay도 확인한다. 실제 구형 Engine/Policy를 같은 신규 fixture에 실행해 기존 구역 조회/flush가 R=1/10/50회, 그룹 load가 G개인 baseline을 재현했다. 개선 후 모든 조합이 구역 조회 1회·기존 그룹 load 0·flush 1회다. 첫 SQL의 sequence block 확보 등으로 전체 prepared count는 11~14회여서 전체 SQL이 엄밀하게 고정됐다고 표현하지 않는다.
- 로컬 R50/G1000 단일 샘플은 구형 약 654ms, 개선 약 106ms다. JVM/컨테이너 warm-up 차이가 있으므로 지연 개선율이나 운영 처리량으로 단정하지 않는다. 실제 lock 대기/보유 시간을 별도로 계측한 것은 아니며 transaction elapsed만 기록한다. `build/work-query-count/mutation-placement-*.json`에 회귀 증적을 저장한다.
- 후행 결과 겹침 실패는 앞선 그룹/Mutation/Entry를 전부 rollback하며 같은 source로 올바른 배치를 재시도한다. 관련 PostgreSQL 5개 클래스 55건(신규 10건 포함), 기존 Mutation 경쟁·ACTIVE fence·보상·일괄 취소·구조 기록·포트 기록 회귀 성공. 추가한 서로 다른 입고의 같은 구역 50개씩 병렬 자동 배치 1건도 성공(19초). 100개 결과가 서로 다른 연속 위치에 놓이고 Entry 100개를 보존했다. 관련 PostgreSQL 총 56건을 검증했다.
- 프론트 `npm run check` 성공. 최종 전체 백엔드+PostgreSQL+spotless 검증 3분 24초. 전체 benchmark·브라우저 E2E·운영 lock/heap/부하 실측은 미실행. 전체 검증 이후 변경은 추가 경쟁 테스트와 문서다.

### 다음 범위

- BE-034 누적 목록·하위 이력/graph의 조회 경계를 이어서 진행한다.

## 47차 변경 — BE-034 캘린더·호환 작업 이력·그래프 내부 조회 상한

### 변경 범위

- BE-034 전체를 완료로 닫지 않는다. 이번 변경은 Work 캘린더, deprecated 난 묶음 작업 이력, Work/Mutation 그래프의 참조·관계 적재를 대상으로 한다. root page나 출력 node 상한을 내부 Entity/JSON 상한으로 오해하던 경계를 먼저 보강했다.
- 캘린더는 양 끝 날짜를 포함해 366일 이내를 요구하고 기간/상태/보기/보정 조건을 적용한 DB 조회를 1,001행으로 제한한다. 최대 1,000개이면 전체 요약을 조립하고 초과하면 조립 전에 422 `QUERY_LIMIT_EXCEEDED`를 반환한다. 기간 초과는 기존 validation 오류인 400이다. 일부 일정을 정상 응답으로 표시하지 않으며 더 좁은 기간·필터 또는 기존 작업 페이지 조회를 사용한다.
- deprecated 난 묶음 작업 이력은 기존 통합 이력 페이지 코어에서 작업일·ID 내림차순 최신 500개 작업을 조립한다. 과거 전체 target/effect 행을 먼저 읽던 경로를 제거했다. 전체 과거 이력은 최대 100개씩 통합 페이지에서 확인한다. frontend는 이미 통합 페이지 경로를 사용한다. 전파/효과 우선순위·현재 위치 값과 전체 page total을 유지하며 호환 목록의 상한은 Controller/OpenAPI와 도메인 정책에 명시한다.
- Work 그래프는 child query를 `maxNodes + 1`행, target/effect/correction query를 각각 1,001행으로 제한한다. sentinel을 제외한 최대 1,000개 참조를 조립하고 초과를 `truncated`에 반영한다. 그래프 출처는 제한된 입고 scalar 참조만 읽으며 receipt JSON·membership·child count를 읽지 않는다. 정정 참조는 ID 순서로 제한한다. 표시할 자식·관계가 생략된 경우와 정확히 seed node 상한을 채운 경우에도 Farm의 truncation을 전파한다.
- Farm의 두 그래프 경로는 Entry 탐색의 기존 깊이별 Slice 한도를 유지하고 표시할 Mutation/Entry를 먼저 선택한 뒤 위치·Work 정보를 조립한다. Mutation relation은 양 끝이 표시되는 ID 집합에 속한 행만 DB에서 읽고 최대 `maxNodes × 4`개를 반환한다. Slice의 추가 행이 있으면 전체 graph/fragment를 truncated로 표시한다. 화면 밖 Mutation과의 관계는 결과에 원래 없던 것이므로 이를 생략한 사실만으로 graph를 truncated로 표시하지 않는다.
- Mutation 결과 edge의 계보 라벨은 표시되는 RESULT Entry의 Mutation/결과 쌍마다 최소 lineage ID의 타입 하나를 scalar로 읽는다. 숨겨진 결과나 같은 결과에 연결된 모든 source/현재 그룹 Entity를 적재하지 않는다. 같은 결과에 여러 타입이 있으면 기존 최초 ID 우선순위를 유지한다. root 출력 순서·종류·snapshot·edge endpoint 계약은 작은 완전한 그래프에서 보존한다.
- 기존 프론트 graph의 부분 결과 안내를 계속 사용한다. API JSON 필드/enum을 바꾸지 않고 새 422 응답과 조회 상한 설명을 Springdoc에서 생성한다. 작업/Mutation writer의 전체 이력, replay·잠금·수량 변경·원장·감사 경계와 DB schema는 바꾸지 않는다.

### 검증

- 일반 백엔드 134개 클래스 715건 성공. 최초 전체 실행은 기본 test JVM heap에서 `Java heap space`로 중단됐으며 `/tmp/green-house-be034-test-heap.gradle`의 검증용 `maxHeapSize=1g`로 전체를 다시 실행했다(1분 28초). 실패를 제품 성능 검증 성공으로 취급하지 않고 repository 빌드 설정에는 heap 우회 변경을 추가하지 않았다.
- PostgreSQL 3개 클래스 35건 성공(46초). 신규 23건은 target=999/1,000/1,001/5,001, child=1/50/5,001, 캘린더=1,000/1,001/5,001, 기간 양 끝 366일/367일 경계, legacy 이력 501개와 마지막 page total, 외부 Mutation 관계=1/100/5,001, visible dense relation 132개, lineage source=1/100/5,001, effect/correction 각각=1,001/5,001을 검증한다. SQL 횟수뿐 아니라 대상·작업·관계·계보 Entity 적재 상한, 참조/관계 초과 시 truncated, 모든 edge 양 끝의 가시성, 초과 캘린더의 안정적인 오류와 요약 미조립을 확인한다. 기존 Mutation PostgreSQL 9건과 Work summary 적재 3건도 통과했다.
- 기존 Work benchmark(100작업 × 20대상, 3회 warm-up/20회 sample)를 query gate를 켜고 실행했다. 목록 SQL 7회·대상 load 0, 상세 SQL 4회, 호환 이력 SQL 4회로 기존 7/4/5 상한을 통과했다. 이력의 root page 조회가 추가됐으며 비용 감소로 설명하지 않는다. benchmark 자체의 작은 이력 fixture가 상한을 입증하지 않으므로 큰 fan-out과 501개 이력은 새 PostgreSQL 회귀로 분리했다.
- `python3 scripts/generate_openapi.py`와 `npm run api:types` 성공. 생성 명세의 캘린더 200/400/422 응답 및 정상 요약/ErrorResponse schema를 확인했다. 프론트 `npm run check`, `spotlessCheck`, `git diff --check` 성공. 전체 PostgreSQL·전체 benchmark·브라우저 E2E·운영 DB plan/부하·peak heap은 미실행. 전체 일반 테스트 뒤 제품 변경은 API 설명/응답 schema annotation·포맷뿐이며 PG의 적재 assertion은 향후 projection 최적화도 허용하는 상한으로 조정했다.

### 남은 범위와 한계

- 전체 그룹·sellable·derived member·collection/직접 계보의 누적 목록 계약, Work/Inbound/lot/Mutation page의 하위 이력 분리·pagination, 사용된 출하 후보가 많은 Sales 선택지 반복 조회는 남는다. 화면이 전체 구성원을 확정하거나 과거 이력을 표시하는 계약에 임의 cut을 넣지 않는다. 다음 BE-034 변경에서 endpoint·소비자별로 이어서 처리한다.
- 전체 farm map은 의도된 전체 배치 계약으로 유지했다. graph/캘린더의 반환 행·Entity 상한은 DB scan/sort, snapshot JSON의 byte 크기, 실제 peak heap/latency, multi-query 단일 snapshot을 보장하지 않는다. 계보 최소 ID projection의 운영 plan·index 검증은 BE-035와 함께 남긴다.

## 48차 변경 — BE-035 실제 Repository 계획·참조/날짜 index·계보 MIN 반복

상태: 확인된 조회의 코드/index 개선과 회귀 검증 완료. 운영 DB의 계획·분포·쓰기 부하와 나머지 조건 검증은 남는다.

### 구현과 근거

- 실제 Flyway가 적용된 PostgreSQL 18에서 root 1,000/50,000개·하위 2N개·구역/상태/날짜 동률·계보 source 최대 5,000개를 적재하고 ANALYZE했다. Repository의 실제 첫 SELECT와 별도 표시한 검색/달력 구성 조건 SQL에 대해 index 전후 `EXPLAIN (ANALYZE, BUFFERS, WAL)`을 비교한다. 작은 데이터의 sequential scan은 허용하며 planner를 강제하지 않는다.
- V41은 확인된 구역/입고/판매/정산 참조와 날짜·ID 정렬, Mutation/결과 쌍의 계보 최초 ID 조회를 지원하는 17개 index를 추가한다. 기존 UNIQUE·index·업무 행·원장·접수·constraint는 보존한다. 5만 root에서 판매 상세 buffer는 1,870→13, 정산 상세는 1,337→8, 전체 판매 최신 page는 1,554→22였다. 전체 FK와 모든 필터를 검증한 결과는 아니다.
- BE-034의 계보 scalar query에 남은 source별 MIN 호출을 표시 Entry에서 시작하는 쿼리로 바꿨다. 같은 index에서도 라벨 1개의 buffer가 15,091→8, MIN 호출이 source 5,000회→1회다. 101개 Entry는 planner의 hash join 때문에 202회 평가하므로 회귀 기준은 표시 Entry 수에 비례하는 상한으로 둔다. 최초 타입/순서·라벨 없음·graph 계약과 모듈 소유권·transaction/잠금·감사·snapshot을 유지한다.
- [11 실행 계획 검증](11-index-plan-validation.md)에 fixture·측정 범위·전후 표·추가하지 않은 index·회귀 기준·쓰기를 포함한 비용을 남긴다. 운영 read-only catalog/통계/FK 선두 index 점검 script와 V41 쓰기 중지·timeout/rollback·재시도 절차를 배포 문서에 추가했다. API schema 변경은 없다.

### 검증

- 일반 백엔드 134개 클래스 715건 성공(1분 25초). BE-034에서 확인한 기본 heap 부족을 피하기 위해 동일한 `/tmp/green-house-be034-test-heap.gradle`의 검증용 `maxHeapSize=1g`를 사용했으며 repository 빌드 설정은 변경하지 않았다. H2의 기존 조회/graph·JSON 계약과 architecture 검증도 통과했다.
- PostgreSQL 관련 회귀 4개 클래스 80건 성공: 배치 query/충돌/replay/rollback 11, 판매 쓰기 query/상태/snapshot/replay/rollback 19, 정산 조회/표시/snapshot/rollback 27, 캘린더/graph/계보 상한·타입/순서 23. 같은 체크포인트에서 새 계획 검증의 초기 assertion은 101 Entry의 hash join MIN 202회를 101회 상한으로 제한해 실패했다. planner의 선형 중복 평가를 확인하고 Entry 수의 두 배 이내 상한으로 조정한 뒤 신규 2건을 별도로 실행하여 성공했다(1분 19초). 이후 제품 변경은 없다.
- 신규 2건은 실제 Repository 및 명시한 구성 조건/legacy/write 표본 26개를 각각 index 전후로 비교한다. 큰 fixture의 참조/최신 page buffer 상한·이전 계획 대비 개선, 선택적 구역, 계보 라벨 1/101개와 MIN 호출 상한, 이전 쿼리와의 차이, 같은 반환 행 수·rollback 후 catalog index 복원·read-only 점검 script 실행을 검증했다. 반환 값 회귀는 기존 기능 검증과 함께 판단하며 plan의 같은 행 수만으로 의미가 같다고 가정하지 않는다.
- 프론트 `npm run check`, backend `spotlessCheck`, `git diff --check` 성공. 전체 PostgreSQL/benchmark·운영 DB 계획/부하·실제 writer 처리량·cold cache·peak heap·migration 중 lock 경쟁/timeout fault injection은 미실행. query count 검증과 계획/행/loop 비용을 구분한다. 전체 일반 검증 뒤 변경은 문서뿐이다.

### 남은 범위와 비용

- 합성 5만 root의 신규 index 합계는 약 41.47MiB다. native 수량 200건 변경 표본의 WAL은 129,072→177,429 bytes로 늘었으며 실제 Mutation writer 처리량 실험은 아니다. 활성 partial predicate의 quantity 변경은 HOT update에 영향을 준다. 조회 개선을 쓰기 비용 감소로 설명하지 않는다.
- leading-wildcard/OR/concat 검색·희귀 상태·count/집계·다중 조건·deep offset·generic prepared plan·대형 IN과 미측정 FK·정산 재구성·원장 대사는 남는다. 이번 구성 조건의 Partner contains는 전체 scan을 유지했다. 운영 PostgreSQL 버전·분포·통계·부하로 재검증해야 하며 B-tree만으로 문자열 검색을 해결했다고 취급하지 않는다.
- V41은 한 Flyway transaction에서 일반 index를 생성한다. 쓰기 중지 시간이 필요하며 `lock_timeout=5s`는 잠금 대기, `statement_timeout=5min`은 각 SQL의 실행 제한이다. 전체 migration 시간/취득 잠금 유지 시간을 보장하지 않는다. 운영 적용·rehearsal·peak build 공간·실제 lock/WAL/HOT 관찰은 미실행이다.

## 49차 변경 — BE-036 정산별 초기화 commit·유한 후보 scan·정확한 key 조회

상태: 정산 초기화의 처리 단위·재시작 의미와 회귀 검증 완료. 운영 최초 대량 처리·heap/GC·lock duration은 미측정이다.

### 구현

- 전체 미연결 Result 누적·정렬과 전체 거래처 선잠금/넓은 min-max 날짜 정산 적재를 제거했다. 시작 시 양수 결과의 최대 ID를 고정하고 read-only 후보 transaction에서 500개 ID/link를 대조하여 정산 key만 writer에 전달한다. 처리 중 생성된 ID와 지나간 cursor 뒤 늦게 commit한 과거 ID는 다음 실행에서 확인한다. 실행 전체의 단일 snapshot이나 전역 날짜 순서를 새로 보장하지 않는다.
- `AuctionSettlementRebuildService`는 바깥 transaction을 `NEVER`로 거절하고 DI proxy를 통해 기존 application writer를 호출한다. writer는 거래처 하나를 잠근 뒤 해당 경매장·경매일의 상한 이내 결과와 연결을 다시 확인하고 정산 한 건을 commit한다. 같은 key의 결과가 후보 여러 페이지에 걸쳐도 전체 key를 한 transaction에서 처리한다. 전체 EntityManager를 clear해 caller 변경을 버리는 방식은 쓰지 않는다.
- 기존 정산은 정확한 house/date Entity graph로 해당 line만 읽는다. 미반영 결과만 snapshot으로 추가하고 기존 line의 수량·단가·금액, 입금액·상태와 수동 재계산/입금 경계를 보존한다. 같은 실행의 결과 접수 UTC 시각을 주입 Clock에서 한 번 구해 전달하며 빈 DB fast path에서는 시각을 읽지 않는다.
- 실패한 정산은 rollback하며 앞서 commit한 정산은 남는다. startup failure를 전체 초기화 rollback으로 취급하지 않는다. 재시작은 연결된 결과를 건너뛰어 미반영 정산만 처리한다. 같은 거래처를 동시에 처리하는 initializer는 잠금 후 연결을 재확인한다. 자동 재시도·진행 상태 테이블·비동기 queue는 추가하지 않았다.
- 정산별 commit 때문에 반복되는 날짜 조회의 실제 Hibernate SQL을 PostgreSQL 18의 50,000개 다른 날짜 결과로 비교했다. 이전 계획은 결과 49,999개를 filter로 제거했고 shared buffer 578이었다. V42의 `(auction_date,id) WHERE amount>0` 이후 같은 1행에 buffer 12를 읽었다. 기존 attempt FK index는 날짜 선두 조건을 지원하지 않았다. planner를 강제하지 않고 buffer 예산과 이전 대비 차이를 회귀로 둔다. 실행 시간 단일 관측이나 이 조건을 모든 검색의 개선으로 취급하지 않는다.
- V42는 업무 데이터·constraint를 바꾸지 않는 transaction 방식 index 생성이다. V41과 같은 쓰기 중지·5초 lock wait/각 SQL 5분 제한·rollback/재시도 정책을 문서화했다. 초기화의 부분 commit과 운영 재시작 정책은 판매/정산 기능·도메인 규칙·배포·architecture 문서에 함께 반영했다. HTTP schema·OpenAPI·생성 TypeScript 변경은 없다.

### 검증

- PostgreSQL 정산 관련 3개 클래스 55건 성공: 신규 처리 단위/계획 9, 기존 조회·금융 snapshot·입금·rollback 27, 거래처/정산 경합 19. 신규 검증은 key=1/50/501의 실제 transaction ID 분리·날짜 계산 단계의 managed Entity 수 5 이하, 한 key의 결과 1,501개, 날짜 양 끝의 추가 결과가 있는 501정산 중 root/기존 line 각각 2개 적재, 실제 CHECK에 도달한 후속 정산 실패와 최초 commit 보존·남은 결과 재실행, 최대 ID 이후 추가 결과 보류, 외부 transaction 거절, 실제 Repository SQL의 50,000행 index 전후 계획을 포함한다.
- 정상 H2 회귀는 초기화 호출 전에 fixture transaction을 실제 commit하도록 바꿨다. writer transaction을 test transaction이 대체하지 않는다. 초기화 fast path SQL은 기존 후보/link 대조에 상한 조회 1회가 추가된 비용으로 기록하고 일반 목록 query gate는 유지한다.
- 정산/원장 개선이 함께 있는 작업 트리에서 일반 backend 134개 클래스 715건 성공(1분 50초), PostgreSQL 관련 7개 클래스 87건 성공(1분 39초). 일반 test JVM에는 BE-034와 같은 검증용 `maxHeapSize=1g` 임시 init script를 사용했으며 repository 설정은 변경하지 않았다. frontend `npm run check`, backend `spotlessCheck`, `git diff --check` 성공. 이후 제품 변경은 없으며 목적별 커밋을 나눈다. 전체 PostgreSQL/benchmark·운영 부하/배포·heap/GC·index build lock/timeout fault injection은 미실행이다.

### 남은 비용

- 한 정산의 source와 기존 line 전체, 처리한 key의 중복 제거 집합은 여전히 증가한다. 거래처 한 행의 잠금 시간도 해당 정산 크기에 비례한다. commit 분할은 조회/settings round-trip 수를 늘릴 수 있으므로 모든 경로의 SQL 횟수·latency 감소로 설명하지 않는다. 운영 최초 처리·반복 latency·peak heap/GC·WAL·lock 대기/시간과 실제 데이터 분포의 V42 계획은 미측정이다.
- 시작 시 초기화 활성 정책을 임의로 끄지 않았다. 다른 인스턴스가 있는 운영에서 전체 초기화가 완료된 화면이 필요하면 writer/인스턴스를 중지하고 적용·확인해야 한다. 전체 원장 대사의 Entry 누적 개선은 다음 별도 목적 변경으로 기록한다.

## 50차 변경 — BE-036 원장 대사의 scalar 그룹·Entry cursor·snapshot 일치

상태: 현재 그룹/원장 Entity 전체 적재와 revision 이력 누적 개선·회귀 검증 완료. 대사 전체를 상수 메모리로 전환하거나 운영 peak heap/GC를 측정한 결과는 아니다.

### 구현

- 현재 그룹을 ID 순 500행의 소유 Repository scalar projection으로 읽는다. 이전 ID 조회→그룹/구역/배드/농장/품종 Entity graph 적재를 제거했다. 도메인 snapshot 필드·좌표 canonical 변환과 현재 그룹의 순서는 유지하고 Repository projection을 외부 모듈 계약으로 노출하지 않는다.
- 현재 그룹/삭제 그룹의 Entry chain을 각각 그룹·revision 순서의 scalar `Stream`으로 순회한다. source type/domain/reference와 revision·전후 snapshot의 필요한 값만 선택하고 fetch size 500을 사용한다. Entry/Mutation Entity 또는 전체 group별 history map을 누적하지 않으며 try-with-resources로 cursor를 닫는다. parser가 유지하는 chain 상태는 최초/직전/다음 Entry이고 JDBC fetch window는 500행이다. caller EntityManager를 clear하지 않는다.
- 현재 revision/마지막 snapshot, chain origin·연속 revision/전후 snapshot, 삭제 tombstone과 PRE_BASELINE/PREPARING/ACTIVE 판정을 보존한다. 현재 그룹의 cutover BASELINE만 기존 ID 순 fingerprint 입력으로 모으며 삭제 그룹을 이 입력에 새로 넣지 않는다. baseline/current fingerprint 구조와 persisted hash, 모든 보고서 필드/오류 코드를 유지한다.
- 독립 `reconcile()` 호출은 read-only `REPEATABLE_READ`로 현재 그룹·Entry·count·업무 참조를 같은 snapshot에서 검사한다. cutover 등의 기존 쓰기 transaction에 참여하는 경우는 caller 경계/isolation을 유지하여 미확정 import/coverage를 볼 수 있게 한다. 보고서를 위해 caller의 상태를 버리거나 별도 commit하지 않는다. writer 중지·cutover 확인은 계속 필요하다.

### 검증

- 신규 PostgreSQL 8건 성공: 한 그룹 revision=1/500/501/5,001의 현재/Entry/Mutation Entity load 0·SQL 24회 이하·기존 fingerprint 입력과 정확한 hash 일치, cursor 경계의 snapshot 불연속, 현재 그룹=500/501의 전체 count/순서/두 fingerprint, 삭제 chain=1,001의 tombstone 누락/복원 및 중간 불연속, 독립 대사 중 병렬 current+Entry 수정에 대한 repeatable snapshot을 검사한다. 대사 뒤 새 호출은 commit된 새 상태를 관측한다. native fixture는 chain cardinality/판정 실험이며 application writer 처리량 실험은 아니다.
- 기존 PostgreSQL 전환/활성화 2건, Mutation 9건, 일괄 취소 13건도 성공했다. PREPARING/ACTIVE·import/activation fingerprint·현재 상태/원장·보상/rollback·기존 쓰기 transaction 참여를 검증한다. 정산 55건을 포함해 같은 최종 체크포인트의 7개 클래스 87건이 모두 성공했다(1분 39초).
- 정산/원장 개선이 함께 있는 작업 트리에서 일반 backend 134개 클래스 715건 성공(1분 50초). 검증용 test heap 1g 임시 init script만 사용했고 repository 설정은 변경하지 않았다. frontend `npm run check`, `spotlessCheck`, `git diff --check` 성공. 전체 검증 이후 변경은 문서뿐이며 목적별 커밋을 나눈다. 전체 PG/benchmark·운영 corpus/부하·peak heap/GC·장기 snapshot의 vacuum 영향은 미실행이다.

### 남은 범위

- 현재 그룹/배치·업무 참조의 전역 검사와 baseline/current fingerprint 입력은 그룹 수에, 전체 오류 보고서는 문제 수에 비례한다. fingerprint 계산은 기존 JSON 직렬화 형식을 그대로 사용하므로 큰 현재 상태의 문자열/byte allocation도 남는다. Work 참조/보정 전체와 보정 Mutation 조회의 누적·큰 입력은 후속 범위다. 이 계약들을 임의로 자르거나 hash 형식을 바꿔 ready를 잘못 확정하지 않는다.
- 한 snapshot JSON의 크기·DB sort/scan·긴 read transaction과 vacuum 영향·실제 heap/GC는 별도 실측이 필요하다. 500행 fetch cursor는 500행별 commit 또는 독립 snapshot이 아니며 전체 대사의 일관된 root transaction을 유지한다.

## 51차 변경 — BE-037 독립 쓰기 경계·늦은 실패·저장 상태 rollback 회귀

- Work 일괄 취소는 외부 테스트 transaction 없이 application proxy를 호출하고, 실제 `test_batch_failure` CHECK와 PostgreSQL SQLSTATE `23514`를 확인한다. Mutation 적용 뒤 Work 상태 저장에서 실패했음을 확인한 다음 그룹·revision·Mutation/Entry/Relation·작업/대상/실행/효과/연결·접수/membership·감사 전체 행을 변경 전 snapshot과 비교한다.
- 같은 실패 요청을 실제 HTTP로도 호출해 `409 / DATA_INTEGRITY_CONFLICT`와 저장 상태를 검증한다. CHECK 제거 후 동일 키 재시도 성공·대사 정상·재전송 응답/저장 상태 불변을 확인한다. 더 이른 validation 실패가 rollback 성공으로 통과하던 `>=400` 검증을 제거했다.
- 경매 출하 완료의 최종 Sales 상태 감사에 CHECK를 넣고 독립 상태 서비스 호출로 검증한다. 전표·항목·배분·생성/출고 snapshot·재고 이력·출하/lot·수량/예약·Mutation 원장·감사의 전체 snapshot rollback과 재시도·동일 상태 재호출 불변을 확인한다.
- Sales/Settlement 두 입금 감사 실패 위치도 각각 CHECK 이름/SQLSTATE를 확인하고 전표·입금 이벤트·잔액·감사 전체 행을 별도 transaction으로 조회해 비교한다. 동일 키 재시도/replay 금융 검증은 유지한다.
- 공통 테스트 helper는 standalone 호출 전 외부 transaction 부재를 확인하고, snapshot은 `REQUIRES_NEW / readOnly / REPEATABLE_READ`로 조회한다. SQL용 테이블 이름은 테스트 상수와 형식 검사에 한정한다. 기존 호출자 후속 실패 시험은 참여 시험임을 이름에 명시해 별도로 유지한다.
- 검증: 관련 PostgreSQL 3개 클래스 54건 통과(Work 취소 13, Sales 재고 22, 거래처 입금/정산 19). 전체 기본 134개 클래스 715건 통과(기존 임시 init script로 test heap 1GiB); frontend `npm run check`, `spotlessCheck`, `git diff --check` 통과. 전체 PostgreSQL suite·benchmark는 재실행하지 않았다. 이후 문서 변경만 반영했다.
- 범위: 운영 로직·DB/API 계약은 바꾸지 않았다. 기존 H2 MockMvc 전체를 독립 transaction 시험으로 전환하지 않았으며 기존 독립 응답/접수/감사 실패 회귀와 보완 관계다. 모든 writer의 transaction 삭제를 mutation test로 검증한 것은 아니다.

## 52차 변경 — BE-038 worker PID별 실제 잠금 충돌·양쪽 순서 회귀

- 공통 PG 잠금 관측 helper는 서비스 spy에서 실제 쓰기 transaction 활성 상태와 worker `pg_backend_pid()`를 기록한다. spy는 원래 메서드를 그대로 실행하며 외부 worker transaction·지연 barrier를 추가하지 않는다. 지정한 보유자 PID까지 이어지는 `pg_blocking_pids` 차단 관계를 10초 이내 관측하고 worker 진입(5초)·미완료 상태도 확인한다.
- 대기열에서 앞선 waiter에 막히는 경우도 고려해 차단 관계를 재귀 추적한다. `UNION`으로 PID 중복/순환을 제한한다. DB 전체 대기자 수와 단순 200ms 미완료 검증을 제거하고, 현재 잠금 manager의 관계만 조회한다. 함수 의미는 [PostgreSQL 공식 문서](https://www.postgresql.org/docs/current/functions-info.html#FUNCTIONS-INFO-SESSION) 기준이다.
- Farm 생성 취소는 실제 group row를 가진 owner와 독립 취소 호출의 PID를 구분하고 기다림을 확인한 뒤 최종 생성 취소/대사를 검증한다. observer transaction이 통계 view를 미리 조회한 조건에서도 확인한다. 별도 owner 둘에 막힌 unrelated worker 둘이 서로의 관측을 통과시키지 못하는 실제 PG 부정 회귀를 추가했다.
- Work 취소/새 계획 등록의 양쪽 순서는 cancel·plan 서비스에서 각각 PID를 기록한다. 지정한 결과 root 잠금과 두 요청의 충돌을 확인한 뒤 성공/업무 거절 코드와 잘못된 PLANNED 대상 부재·원장 대사를 검증한다.
- Work 대상 완료/취소는 양쪽 순서로 확대했다. 완료 우선이면 완료·취소 성공, 취소 우선이면 완료 `400 / VALIDATION_ERROR`와 최종 CANCELED·활성 효과 부재를 확인한다. 취소/Farm 이동 재검사도 취소 worker와 이동 owner의 실제 차단 관계를 확인한다.
- 형제 입고 포트도 양쪽 실행 순서에서 각각의 실제 worker PID가 동일 inbound owner의 잠금으로 이어지는지 확인하고 공통 작업 완료/대사를 검증한다. Work 일괄 취소 재전송은 두 HTTP 요청을 원본 Work row 잠금 아래에서 실제로 겹치게 만든 뒤 단일 보상/감사 결과를 검증한다.
- BE-006에서 이미 보강한 Sales 교차 배분 수정·취소·구조 변경의 양쪽 순서와 첫 수정 rollback 회귀도 최종 관련 PG 실행에 포함한다. 기존 테스트를 새 helper로 임의 이식하지 않았다.
- 최종 검증: 관련 PostgreSQL 6개 클래스 67건 통과(Farm routing 6, Work 보정 20, 안전 취소 6, 입고 취소/포트 10, 일괄 취소 13, Sales 잠금 순서 12). 전체 기본 134개 클래스 715건 통과(기존 임시 init script로 test heap 1GiB); frontend `npm run check`, `spotlessCheck`, `git diff --check` 통과. 추가 회귀 수정과 겹쳤던 포맷 검사 실패는 최종 재검증에서 해소했다. 전체 PostgreSQL suite·benchmark는 재실행하지 않았다. 이후 문서 변경만 반영했다. 운영 코드·DB migration·HTTP/API 계약은 변경하지 않았다.
- 범위: 기존 DB 전체 대기자 helper 3곳과 200ms 생성 취소 시험, 일괄 취소의 시작 latch만 사용한 경쟁을 보강했다. 전체 concurrent suite의 모든 시작 latch를 제거한 것은 아니며, 다른 경로의 모든 충돌/무교착·고부하 처리량을 증명하지 않는다. HTTP client의 전역 timeout·fixture 공통화는 BE-039/040 후속 범위다.

## 53차 변경 — BE-039 JSON path·고정 업무일·과거/최신 migration 검증

- Work/입고 포트 및 OrchidGroup 통합 시험의 생성 ID 정규식을 JSON pointer 조회로 교체했다. 응답 필드 순서·공백·중첩 ID에 의존하지 않고 숫자/long 범위/양수 여부를 검사한다. helper의 순서/중첩·누락/null/문자열/소수/overflow 부정 회귀도 추가했다.
- 난 묶음 년생 시험은 UTC `2026-10-04T15:00:00Z`의 주입 Clock과 고정 입고일을 사용한다. 농장 날짜 `2026-10-05`의 실제 HTTP 조회에서 3년생을 검증해 시스템 날짜/UTC 날짜 차이에 의존하지 않는다. 해당 클래스의 농장 fixture는 필요한 3동만 생성하도록 줄였으며 공통 fixture를 사용하는 다른 클래스의 15동 구성은 유지한다.
- V27→V34의 일곱 migration 목록/건수는 의도한 과거 업그레이드 계약으로 보존한다. 별도 parameter 사례에서 V34→현재 classpath의 마지막 migration까지 적용하고 pending 없음·적용 버전과 resolved 버전 일치·validate·재실행 0건 및 원본 입고/작업/receipt 보존을 확인한다. 최신 version 숫자를 새로 하드코딩하지 않았다.
- Mutation schema 시험도 V21~34 역사 목록과 전체 최신 적용/validate/pending gate를 구분한다. DB 격리·임시 DB cleanup·기존 pooled sequence 정책을 유지한다.
- 검증: 집중 기본 4개 클래스 44건 통과; 관련 PostgreSQL 2개 클래스 4건 통과(역사/최신 upgrade 2, Mutation schema/과거 Entry 2); `git diff --check` 통과. 세 finding의 최종 전체 기본/frontend 검증은 55차 체크포인트에서 수행한다.
- 범위: 생산 코드·migration 파일·API 계약은 바꾸지 않았다. 다른 fixture의 MIN/OFFSET·payload/seed 중복과 긴 lifecycle 전체 분해는 후속 정리다. 과거 upgrade의 고정 버전/정확 hash·API golden은 일반적인 brittle assertion으로 제거하지 않는다.

## 54차 변경 — BE-040 공통 HTTP·병렬 future·JUnit 실행 상한

- PG 공통 HTTP transport에 연결 10초/request 60초를 적용했다. IO 실패/timeout에는 method·path·설정 상한과 원인 예외를 남기고 body·query string은 기록하지 않는다. interrupt는 복원해 전파한다. 기존 status/body/사용자 header는 유지한다.
- 공통 GET/POST뿐 아니라 직접 별도 client로 보내던 Farm PATCH 두 경로도 공통 transport로 이식했다. MockMvc의 static `patch`와 이름 충돌을 피하도록 HTTP helper는 `patchJson`으로 구분한다.
- 실제 local HTTP 서버로 stalled response의 timeout/진단·본문 비노출과 PATCH/header/error response 보존을 검증한다. 서버/worker는 finally에서 해제한다.
- 판매 전표 번호·Farm 기준정보 번호의 `invokeAll`을 60초로 제한하고 취소 여부·bounded future 결과를 확인한다. 전표 번호 executor는 shutdown 뒤 10초 이내 종료도 확인한다. 기존 번호 uniqueness/FK/rollback 검증을 유지한다.
- 공통 PG test method는 SAME_THREAD 5분 JUnit 상한, 두 benchmark 클래스는 15분을 명시한다. `workE2eTest`의 timeout thread dump를 켰다. 과거 migration/benchmark를 60초의 일반 HTTP 상한으로 일괄 제한하지 않는다.
- 검증: HTTP transport 기본 2건 통과. 관련 PG 4개 클래스 20건 통과(전표 번호 1, Farm 코드 5, 입고 취소/포트 10, Work 상태 보호 4); `git diff --check` 통과. 전체 기본/frontend·format은 55차 완료 체크포인트에서 실행한다.
- 범위: test harness 변경이며 운영 timeout/SLA는 바꾸지 않았다. JUnit method 상한은 Spring context/container 기동·DB 서버 statement·JVM 종료의 강제 종료 보장이 아니다. 무응답 서버의 JDBC 취소나 모든 executor의 종료 정책까지 확대하지 않았다.

## 55차 변경 — BE-041 application member 승인·새 mutator 자동 탐지

- OrchidGroup 상태 메서드 이름 목록을 제거했다. compiled field SET을 가진 method와 그 method로 위임하는 method/reference를 추적해 상태 writer를 발견한다. 외부 직접 field SET도 확인한다. engine/복구 migration의 실제 caller inventory, 생성자·Repository writer 경계는 유지하고 method/constructor reference 탐지를 추가했다.
- 새 임의 이름 mutator·private 위임·method reference·직접 field write를 가진 bytecode 부정 fixture로 탐지 여부를 검증한다. 순수 getter/constructor는 기존 Entity mutation으로 잘못 분류하지 않는다. constructors는 기존 별도 생성 gate에서 확인한다. 이전 이름 목록에서 빠져 있던 실사/대사도 같은 규칙으로 포함된다.
- 모듈 밖에서 사용하는 application TYPE/METHOD/CONSTRUCTOR를 별도 검토 목록으로 고정했다(490개 계약 항목). 기존 현재 소비 경계를 읽고 소유 API·값·adapter/port·복구 계약으로 검토했다. 내부 서비스의 모든 public method를 승인하지 않으며 기존 타입의 새 helper 호출/reference도 실패한다. type/member 목록은 caller class/라인/횟수를 포함하지 않고 기존 모듈 의존 방향 gate와 결합한다. 기준 파일은 자동 갱신하지 않으며 사라진 계약도 제거해야 한다.
- 실제로 쓰는 public method/constructor와 구현한 외부 interface의 generic 입력/반환·nested record 값에서 Entity·Repository projection·storage callback 유출을 검사한다. record 값의 재귀 generic bound도 확인한다. Entity/callback 부정 fixture와 승인된 타입에 미승인 helper를 호출/reference하는 부정 fixture를 추가했다.
- `@Query`/count root의 schema·FQCN·quoted 식별자 정규화를 추가해 같은 다른 모듈 table을 이름 표기만 바꿔 읽는 우회를 차단한다. 직접 read/write/count root와 association alias를 구별하는 회귀를 추가했다. 완전한 SQL parser는 도입하지 않았다.
- 검증: 집중 architecture/탐지 helper 6개 클래스 26건 및 최종 전체 기본 139개 클래스 737건 통과(기존 임시 init script로 test heap 1GiB). 관련 PostgreSQL 12개 클래스 90건 통과: 역사/최신 migration 4, 코드 발급 6, 입고/상태 보호 14, Mutation/fence/routing 15, Work 취소/보정 39, Sales 잠금 순서 12. 기존 raw DB fence 부정/Mutation context rollback도 포함했다. frontend `npm run check`, `spotlessCheck`, `git diff --check` 통과. 전체 PostgreSQL suite·benchmark는 재실행하지 않았다. 이후 문서 변경만 반영했다.
- 범위: 생산 domain/service·DB/API 계약은 바꾸지 않았다. 사용하지 않는 public method의 존재 자체를 금지하지 않으며 새 모듈 외부 사용을 검토한다. reflection·Opaque Object JSON·동적/alias/comma SQL·외부 XML query·새 Repository 원자 writer와 연관 객체 내부 변화는 구조 gate 밖의 검토/PG fence 범위다. architecture 통과를 transaction/수량 불변식 보장으로 확대하지 않는다.

## 56차 변경 — BE-042 JDBC 실행·반환 행과 하위 fan-out 측정

- 테스트 전용 DataSource wrapper로 Hibernate/JdbcTemplate의 execute·batch 호출, 소비 ResultSet 행, 실패, 명시 commit/rollback 및 실행/commit 시간을 계측한다. SQL·parameter 값은 보관하지 않는다. H2에서 실제 batch/행·commit/rollback·driver 예외 전파와 측정 창 중복 거절을 검증했다.
- Work benchmark는 root 100건 고정·작업당 대상 1/20/100건과 동일 묶음 이력 1/100건으로 확장했다. Hibernate SQL/Entity/flush·JDBC counters·응답 JSON UTF-8 byte를 기록하며 목록 target Entity 0·반환 행 상한 및 enforcement 시 JDBC 실행 상한을 검사한다. 검색 benchmark의 501/5,001/70,001 거래처·공백 1/20개에도 JDBC counters를 추가했다.
- 영구 PostgreSQL 회귀는 root 4건·대상 1/40/100건에서 SQL·반환 행 증가가 없음을 확인하고, Hibernate 통계가 0인 JdbcTemplate 조회 17행을 관측한다. 독립 Work 기록은 응답까지 commit 1회·JDBC 실행 37회(batch 호출 21회), 동일 키 replay는 commit 1회·실행 2회로 측정했다. 기존 저장 응답 replay와 flush를 함께 확인하고 `build/test-measurements/work-write.json`을 CI artifact에 포함했다.
- 검증: 계측 단위 2건, 새 PG 회귀 3건, Work/Search benchmark 2건이 query enforcement와 함께 통과했다(benchmark/PG checkpoint 임시 heap 1GiB). `git diff --check` 통과. 전체 기본/frontend 검증은 BE-044 완료 체크포인트에서 실행한다.
- 최종 전체 검증에서 DEBUG 환경의 H2 SHUTDOWN 뒤 JdbcTemplate 경고 조회가 정리 오류를 일으키는 문제를 발견했다. 정리만 raw JDBC로 수행하도록 별도 수정했다(`c301dfb4`). 수정 후 전체 기본 743건과 format이 통과했고 PG/benchmark의 계측·실행 의미는 바꾸지 않았다. Work benchmark 목록은 대상 1/20/100건 모두 JDBC 실행 7회·반환 행 201건·target Entity 0건을 유지했다.
- 범위: 기존 BE-027~036 경로별 query/Entity/plan·rollback 회귀를 유지했다. JDBC 호출은 driver 내부 round trip이 아니고 소비 행은 DB scan이 아니다. JSON bytes는 재직렬화된 body 기준이며 autocommit·raw unwrap·다른 DataSource·lock 보유 시간·peak heap/GC·전체 할당량은 별도다. 시간 절대값을 CI 실패 기준으로 삼지 않으며 운영 부하 실험은 수행하지 않았다.

## 57차 변경 — BE-043 현행 writer·대상 terminal 정책 문서 일치

- Farm 예약/직접 변경 Legacy 제거 설명은 앞선 수정에서 반영돼 있었다. 남은 구조 변환의 Legacy/Engine 공동 경로 설명을 실제 application 실행기의 원본 잠금/변경 전 계획·단일 Mutation Engine·recorder 역할에 맞췄다.
- Work 전체 완료 검사와 진행 중 자동 완료에 `CANCELED` 대상 terminal을 명시했다. 비어 있거나 미완료/부분 완료/실패 대상이 있으면 완료할 수 없고, 전체 취소·STOPPED를 자동 완료로 대체하지 않는 의미를 구별했다.
- 기능 문서의 진행률 100%면 취소할 수 없다는 설명도 실제 완료 작업의 CANCEL/CORRECT action과 맞췄다. 프론트는 진행률 대신 서버 action/취소 가능 조회를 사용한다.
- 검증: Engine·WorkTargetExecution·WorkOperationProgressService·WorkOperationActionResolver 및 관련 API slice와 현행 문서를 대조했다. `git diff --check` 통과. 문서만 변경했으며 테스트·OpenAPI 재생성은 반복하지 않았다. 일반 metadata 수정 권한(BE-009)의 업무 정책은 변경하지 않았다.

## 58차 변경 — BE-044 정산 선호값과 실행 capability 구분

- 정산 설정 응답에 현재 거래처 유형의 실행 capability를 추가했다. 비경매 거래처는 전표별, 경매장은 경매일별 정산을 제공하며 월간·자동 매칭/정산·예치금·자동 차감·규칙 실행은 미지원이다. immutable domain 값이 이 지원 정책을 소유하고 저장된 unit/boolean/ruleJson으로 기능을 활성화하지 않는다.
- 기존 거래처 잠금에서 받은 유형을 응답에 사용해 별도 재조회 없이 capability를 조립한다. 최초 설정 생성·변경·감사의 기존 transaction/잠금 순서를 유지하고 저장된 MONTHLY_BATCH·자동 처리 true·JSON 규칙을 거절하거나 초기화하지 않는다. 지연일과 달력일/영업일의 실제 예상 입금일 계산도 유지한다.
- 화면의 정산 단위 선택·자동 처리 availability는 생성된 capability 타입을 사용한다. 저장된 선호값과 현재 실행 지원 범위를 따로 안내하고 미지원 값을 실행 중인 기능으로 표시하지 않는다. capability가 없으면 조작을 허용하지 않으며 응답 전용 capability를 수정 payload에서 제외한다. 기존 수정 form 상태 구조를 전면 변경하지 않았다.
- Controller/DTO와 회귀를 수정한 뒤 `python3 scripts/generate_openapi.py`, `npm run api:types`로 전체 명세·Partner slice·TypeScript 계약을 재생성했다. Request의 기존 필드·validation·저장 의미는 유지하고 보관용 설정의 설명을 추가했다. 도메인/판매 기능 문서에 실제 적용되는 지연일과 미지원 기능·별도 신규 정산의 설계 조건을 반영했다.
- 검증: 집중 capability 4건·설정 HTTP/architecture 회귀 통과. 실제 PG 22건(신규 저장/조회 계약 3, 기존 정산/거래처 잠금·동시성·rollback 19) 통과. Wholesale/Retail/Auction 설정의 월간/자동 선호값 보존·capability 불변·설정 감사 한 건과 금융 행 미생성을 확인했다. frontend capability 부정/누락 회귀 3건 및 최종 `npm run check` 통과. 최종 backend 전체 141개 클래스 743건·`spotlessCheck`·`git diff --check` 통과(임시 init script로 test heap 1GiB). BE-042 PG 3건과 합쳐 이번 관련 PG 25건·benchmark 2건이 통과했다. 최종 성공 뒤 문서만 갱신했다.
- 범위: capability는 현행 지원 범위이며 권한·특정 대상의 입금 가능 여부를 대체하지 않는다. 월간 aggregate·자동화·입금 분배·예치금 기능과 DB migration은 추가하지 않았다. 전체 PostgreSQL suite·브라우저 E2E·운영 부하 실험은 재실행하지 않았다.

## 59차 검증 — 전체 PostgreSQL 회귀 체크포인트

- 기준: `222e1d04`의 작업 트리가 깨끗한 상태에서 전체 `workE2eTest`를 실행했다. 일반 수정의 `RECONCILIATION` 분류·Work 생성과 관련 정책 변경은 사용자 요청으로 보류했다. 운영 코드·테스트·migration·API·기능 활성화 설정은 변경하지 않았다.
- 실행: backend에서 `./gradlew --init-script /tmp/green-house-be034-test-heap.gradle workE2eTest --rerun-tasks`. 기존 검증용 임시 init script로 모든 Test task의 `maxHeapSize`를 `1g`로 설정했으며 repository 빌드 설정은 유지했다. PostgreSQL 18 Alpine Testcontainers의 격리 DB를 사용했다.
- 결과: **76개 클래스·780건 성공, 실패 0·오류 0·건너뜀 0**, Gradle 전체 실행 약 11분. 선언 위치의 `@Tag` 검색에는 추상 base가 포함되고 상속된 태그를 사용하는 구체 클래스가 빠지므로 최종 수치는 XML 보고서의 실제 실행 결과로 집계했다. `--rerun-tasks`로 5개 task가 모두 실행됐으며 기존 결과의 up-to-date 판정으로 대체하지 않았다.
- 범위: 판매 예약/출고/취소·생성 접수, 경매 결과/반환·이력 보존, 입고/포트, 구조 변경·Work 생성/취소/보정, 정산/입금, Mutation 원장·write fence·전환/대사, 실제 CHECK/validation 도구, migration upgrade, query-count·조회 상한·인덱스 계획·잠금 충돌 회귀를 함께 실행했다. 이 체크포인트에서 수정할 테스트 실패는 발견되지 않았다.
- 증적: XML `backend/build/test-results/workE2eTest/TEST-*.xml`, HTML `backend/build/reports/tests/workE2eTest/index.html`, 실행 로그 `/tmp/green-house-postgres-checkpoint.log`. 이 경로의 산출물은 로컬 검증 자료이며 버전 관리하지 않는다.
- 일반 backend 141개 클래스·743건, frontend `npm run check`, `spotlessCheck`는 58차의 마지막 성공 결과를 유지한다. 이후 제품 코드 변경이 없고 이번 변경은 검증 기록뿐이므로 재실행하지 않았다. `git diff --check`는 이번 문서 변경에 실행했다. 별도 `workBenchmark`, 브라우저 E2E, 운영 DB 대사/validation·부하·배포 검증은 이번 실행에 포함하지 않았다.

전체 로컬 회귀 성공을 기존 운영 데이터의 정합성·과거 삭제 기록 복원이나 모든 writer의 무교착 증명으로 취급하지 않는다. 현재 후속 작업과 필요한 입력은 마지막 [남은 작업](#남은-작업)에서 관리한다.

## 60차 문서 정리 — 현재 후속 작업과 완료 이력 분리

- 59차 검증과 1~58차 구현 완료 근거를 반영해 마지막 목록을 다시 정리했다. 오래된 BE-015/018/019/038의 후속 문구와 완료 내역 반복을 제외하고 기술 후속·운영 검증·정책 보류/범위 판단·조건부 확장을 구분했다.
- 기존 1~59차 기록은 당시 이력으로 보존하고 현재 후속 목록의 위치를 문서 처음과 59차에 연결했다. BE-009 사용자 보류는 유지하며 미측정 영역을 확인된 결함이나 신규 기능을 필수 잔여 구현으로 취급하지 않는다.
- 문서만 변경했다. `git diff --check` 통과. 마지막 전체 PostgreSQL 780건 및 58차 일반 backend 743건/frontend 검증 이후 제품 변경이 없어 테스트·benchmark·OpenAPI 생성은 반복하지 않았다.

## 61차 변경 — 정산·원장 대량 측정 도구와 결과 전달 경로

- 사용자 환경에서 오래 걸리는 측정을 실행하고 결과만 전달할 수 있도록 `domainBenchmark` task와 `scripts/performance/run-domain-benchmark.py`를 추가했다. PostgreSQL 18 Testcontainers의 새 격리 DB와 실행별 고유 디렉터리를 사용하며, 파괴적 fixture 초기화 전에 연결 URL을 확인한다. 제품 코드·migration·API·일반 수정/실사/보정 gate는 변경하지 않았다.
- 정산 초기화/재실행과 원장 정상/긴 단일 chain/Work 참조/오류 누적을 smoke·standard·large profile로 나눴다. sample마다 fixture를 새로 준비하고 실제 application proxy를 호출한다. 정산 수·line·금액·무변경 replay와 원장 Entry 수·ready·오류 종류/수·fingerprint를 검증한 결과만 성공으로 기록한다. 원장은 수량 0의 `BASELINE_PREPARING` 합성 자료이며 ACTIVE 전환이나 Work 보정 writer 실행을 측정하지 않는다.
- JSON에 처리 시간, 10ms 간격의 sampled heap·측정 스레드 할당량·GC, JDBC 실행/반환 행·Hibernate 적재, 관측 transaction/row-lock 구간과 실행 환경을 함께 저장한다. transaction은 첫 SQL부터 JDBC 종료까지이며 row-lock 구간은 명시적 locking SQL 반환부터 종료까지다. 서버의 정확한 lock hold/wait·process RSS·운영 p95를 측정한 것으로 해석하지 않는다.
- runner는 종료 코드뿐 아니라 report 설정·scenario/sample 수·개별 성공 상태를 확인한다. 실패·중단의 부분 결과를 보존하고 이전 성공 파일 재사용을 막는다. 일반 `test`와 기존 PostgreSQL 회귀/benchmark, 기본 CI에서는 대량 측정을 실행하지 않는다. 실행 명령·fixture 크기·결과 전달·관측 한계는 [측정 가이드](12-domain-performance-measurement.md)와 배포 문서에 반영했다.
- 집중 검증: JDBC transaction/실제 H2 locking SQL·rollback·미종료와 heap sampler의 새 Java 3건, runner 완료/누락/실패/중복/설정·이전 성공 재사용 방어 Python 3건 통과. PostgreSQL smoke는 warmup 1회·sample 2회·heap 1GiB로 **5개 scenario·14개 측정 sample 성공**, 전체 실행 약 40초였다. 미종료/관측 없이 닫힌 transaction은 0건이다. 결과는 `backend/build/domain-benchmark/20261005T121717Z-2bccac3d/result.json`에 보존한다.
- 기능 단위 검증: `./gradlew --init-script /tmp/green-house-be034-test-heap.gradle test spotlessCheck` **143개 클래스·746건 성공**, 실패·오류·건너뜀 0건. 기존 임시 검증 설정으로 일반 Test heap을 1GiB로 사용했다. frontend `npm run check`, Python runner 3건 및 `git diff --check` 통과. 제품 DB 경계 변경이 없어 전체 `workE2eTest` 780건을 다시 실행하지 않았으며 새 측정 경로는 실제 PostgreSQL smoke로 확인했다.
- **standard·large 대량 측정은 사용자 실행과 결과 분석 대기다.** 도구 준비/smoke 성공으로 BE-015/033/036/042의 대량 성능·병목 개선이나 운영 부하 검증을 완료 처리하지 않는다. BE-009 보류는 유지한다.

## 62차 분석 — 사용자 제공 standard 정산·원장 측정 결과

- `5c664962`의 깨끗한 작업 트리에서 실행한 `20261005T122534Z-e84e1210/result.json`을 받았다. standard/all·warmup 1회·sample 3회·heap 2GiB, 전체 실행 약 282초다. 완료 guard와 원시값 재계산 summary가 일치하며 **10개 scenario·45개 sample 모두 성공**, JDBC 실패·rollback·미종료/관측 없이 닫힌 transaction은 0이다.
- 정산 1건×10,000결과 초기화 중앙값 1.68초, row-lock 관측 구간 최대 1.585초. 501정산×20결과는 중앙값 24.63초(16.99~30.14초)였지만 단일 transaction 최대는 96ms였다. JDBC execute 누적 15.18~28.65초에 비해 commit 호출 누적은 약 0.15~0.16초라 다수 정산 비용을 commit 자체만의 문제로 취급하지 않는다. 정확한 지연 SQL/계획 원인은 미확인이다. 정산별 commit·잠금 순서는 유지한다.
- 원장 5,000그룹×10revision은 중앙값 1.68초·Entity 1개로 chain cursor 경로를 확인했다. 반면 Work 보정 참조 5,000건은 Entity **10,001개**를 적재했다. Work correction의 Entity batch 읽기와 Farm의 전체 Mutation ID `findAllById`를 코드에서 확인했다. scalar/projection·입력 분할을 우선 개선 대상으로 정했고, large 입력의 실제 실패나 OOM이 재현됐다고 기록하지 않는다.
- 상세 표·메모리/GC 해석·원인과 가설의 구분·후속 순서는 [결과 분석](13-domain-performance-results.md)에, 원시 sample 45행은 `measurements/20261005T122534Z-e84e1210-standard.csv`에 보존했다. 로컬 원본 JSON의 SHA-256과 환경도 함께 기록했다. sampled heap과 누적 할당량을 혼동하지 않으며 결과를 운영 p95/부하 검증으로 확대하지 않는다.
- 문서·측정 증적만 변경했다. CSV의 45행·설정·측정값·결과를 원본 JSON과 대조하고 `git diff --check`를 실행했다. 마지막 일반 backend 746건/frontend·spotless, 59차 전체 PostgreSQL 780건을 재실행하지 않았다. standard 수집/분석은 완료, 확인된 비용의 개선/추가 측정·운영 검증은 남음. BE-009 보류 유지.

## 63차 변경 — Work 보정 대사의 scalar 참조·Mutation 입력 분할

- 62차의 Work 참조 5,000건/Entity 10,001개 적재를 후속 개선했다. Work 보정은 ID 순서로 500행씩 `id`·Mutation/correlation·저장 결과만 읽고 Work 내부에서 application 참조 값으로 변환한다. Farm은 보정 참조 500건씩 중복/null Mutation ID를 정리하고 소유 projection에서 검증에 필요한 출처/correlation만 조회한다. 전체 correction/Mutation Entity와 전체 Mutation map을 persistence context/메모리에 누적하지 않는다.
- 기존 보정 결과의 날짜·adjustments·quantityBalances 해석을 공통 reader로 옮겨 상세 응답과 scalar 대사가 함께 사용한다. 구형 absent/default·숫자 문자열 ID, 명시적 null/잘못된 날짜·수량 수지의 실패 의미를 유지한다. 외부 HTTP/application 참조 계약·저장 JSON·오류 순서·fingerprint·같은 read snapshot은 변경하지 않았다.
- Work/Farm Repository projection은 소유 모듈 안에서만 사용한다. transaction 경계·잠금·migration·기능 gate는 유지하며 persistence context `clear`나 새 transaction을 추가하지 않았다. 전체 Work 참조 목록/그룹 ID 값은 기존 계약상 남으므로 상수 메모리로 판정하지 않는다.
- 실제 PostgreSQL에서 1·499·500·501·5,000건의 Entity 적재 0과 명시적 batch 수 기반 조회/반환 행 상한·SQL bind 상한을 확인했다. 배치 경계의 누락/잘못된 Mutation 링크·dangling 그룹 오류 순서와 fingerprint 유지, 날짜만 바뀐 구형 보정, 동시 수정 뒤에도 같은 root repeatable snapshot을 검증한다. 기존 Work 보정/수량 수지/상세 응답·원장 cursor/전환 회귀도 실행한다.
- 측정 회귀: 보정 5,000건에서 전체 Entity 적재 **1개(coverage)**, 보정/Mutation 각각 0개, JDBC 실행 38회·소비 행 15,009개·SQL bind 최대 500개를 확인했다. 1/499/500건의 실행 수는 모두 20회, 501건은 22회다. 건별 N+1 대신 명시적 batch 경계에서만 증가한다. 이 fixture는 한 그룹의 revision을 늘린 것이며 62차의 500그룹 Work scenario와 시간/heap을 직접 비교하지 않는다.
- 집중/기능 단위 검증: 공통 reader·H2 대사 집중 검사 통과. PostgreSQL 관련 **6개 클래스·49개 고유 시험 성공**(첫 실행 48건 후 날짜 전용 회귀를 추가해 새 클래스 8건 재실행), 실패·오류·건너뜀 0건. 일반 backend **144개 클래스·748건 성공**, frontend `npm run check`, `spotlessCheck`, `git diff --check` 통과. 일반/PG 검증에는 기존 임시 heap 1GiB 설정을 사용했다. 전체 `workE2eTest` 780건 및 standard/large benchmark 전체 재측정은 반복하지 않았다. 마지막 전체 PG 체크포인트는 59차다.
- 실제 실행 로그는 `/tmp/green-house-be063-focused.log`, `/tmp/green-house-be063-postgres.log`, `/tmp/green-house-be063-final.log`, `/tmp/green-house-be063-frontend.log`에 보존한다. 최종 검증 뒤에는 문서만 정리했다. 다수 정산의 24.6초 지연 SQL/계획 조사가 다음 기술 작업이며, 운영 부하 검증과 BE-009 보류는 유지한다.

## 64차 분석 — Work scalar 개선 뒤 standard 전후 비교

- 사용자 재측정 `20261006T004212Z-e0775dd9`를 62차 결과와 비교했다. revision `c1b4bd95`, 깨끗한 작업 트리, standard/all·warmup 1회·sample 3회·heap 2GiB. 보고된 환경/설정·fixture는 같고 두 revision 사이 benchmark/계측 코드도 동일하다. 완료 guard·summary 재계산 검증에 성공했고 **10개 scenario·45개 sample 모두 성공**, JDBC 실패/rollback/미종료 0, 업무 outcome 동일이다.
- 같은 Work 참조 scenario에서 Entity **10,001→1개**, 측정 스레드 누적 할당량 중앙값 **104.30→81.30MiB(약 22% 감소)**를 확인했다. sampled heap 최대는 292.49→293.18MiB라 live heap peak 개선으로 해석하지 않는다. application 중앙값은 **257.76→303.61ms(+17.8%)**, JDBC 실행 30→39회다. 전체 Mutation 조회 1회를 500건씩 10회로 바꾼 비용과 Entity 누적 제거를 구분하며 속도 개선으로 완료 처리하지 않는다.
- 정산 501건 초기화는 24.63→25.02초로 남아 있고 실행 수/변동 범위가 비슷하다. 오류 5,000건 대사는 241.07→377.05ms(+56.4%)로 증가했다. 후자는 조회/행/Entity·할당량이 거의 같고 Work 보정도 없는 fixture라 원인을 분할 조회나 GC만으로 단정하지 않는다. 전체 시간 회귀 부재를 선언하지 않으며 필요한 지연 재현/원인 조사를 후속으로 남긴다.
- [13번 결과 분석](13-domain-performance-results.md)에 전후 15개 phase 중앙값·해석·관측 한계를 기록하고, 새 원시 sample 45행과 원본 SHA-256을 보존했다. standard 전후 결과 수집·비교는 완료했다. 다수 정산 statement별 비용, 오류 대사 지연 재현, 필요한 Work scalar 조회 비용 보강과 large/운영 검증은 남는다.
- 문서·측정 CSV만 변경했다. CSV의 sample identity·설정·outcome·핵심 지표와 모든 phase 중앙값을 원본에 대조하고 `git diff --check`를 실행했다. 제품 변경이 없어 63차 backend 748건/관련 PG 49건/frontend·format과 59차 전체 PG 검증을 반복하지 않았다. BE-009 보류 유지.

## 65차 조사 — 정산 계획 변동과 원장 지연 재현

- [14번 원인 조사](14-performance-diagnosis.md)에 실제 application·PostgreSQL 서버 SQL 통계·custom/generic/cached 계획·JFR와 이전/현재 코드의 격리 비교를 기록했다. 별도 `domainDiagnosis` task/tag와 scope별 실행 가이드를 추가했다. production·index·transaction·보류 정책은 변경하지 않았다.
- 정산 원본 조회가 통계 미갱신 조건에서 반환 20행을 얻으려고 출하 10,020행과 후속 join을 각각 경유하는 계획을 재현했다. 같은 SQL의 custom 실행은 57.664ms, 통계 갱신 후 기존 V42 날짜 index에서 시작한 계획은 0.439ms였다. 실제 501정산 첫 실행에서도 약 16.3초 중 원본 SQL이 약 10.2초였다. 필요한 index가 없다는 진단과 전역 custom/generic 강제를 피한다.
- 연결 ID 조회에서도 빈 테이블에서 선택한 generic 전체 스캔 계획이 line 증가 후 남는 경우를 확인했다. 마지막 재현의 연결 조회 합계는 약 2.153초였고 같은 connection/bind의 custom 비교는 20개 4.816→0.034ms·500개 104.712→0.152ms로 기존 UNIQUE index를 사용했다. 이전 사용자 standard의 실제 계획은 미수집이라 당시 25초의 구성 비율을 소급 확정하지 않는다.
- 같은 5,000그룹×1revision에서 정상/오류 교대 측정으로 이전 `5c664962`와 현재 `82444a86`를 순차 비교했다. 오류 중앙값 273.05→280.29ms·조회 37회/15,006행·할당량 약 118.8MiB로 큰 증가가 재현되지 않았다. chain cursor/snapshot 비교와 fingerprint 비용을 확인했지만 당시 241→377ms의 정확한 환경 원인을 확정하거나 회귀 부재를 선언하지 않는다.
- 같은 Work 참조 5,000건에서는 8회씩 비교해 할당량 102.74→82.02MiB·조회 30→39회/행 16,506개를 확인했다. 전체 중앙값 250.57→222.87ms, JDBC execute 중앙값 63.99→62.35ms로 이번에는 시간 증가가 없었다. 표준 측정의 시간 증가와 할당량 개선을 구분하며 큰 IN/Entity 적재로 되돌리지 않는다.
- 성공한 진단 8개 실행의 원본 경로/SHA-256과 핵심 48개 sample·SQL 통계·선택된 실제 계획을 `measurements/20261006-performance-diagnosis.json`에 보존했다. 이전 실패한 탐색 실행은 성공 증적으로 포함하지 않았다. 프로파일링·사전 EXPLAIN·서버 nested 통계의 해석 한계도 기록했다.
- 기능 단위 검증: 실제 PostgreSQL의 plans·stats·이전/현재 ledger·Work 진단 모두 성공했다. 일반 backend `./gradlew --init-script /tmp/green-house-be034-test-heap.gradle test spotlessCheck` **144개 클래스·748건 성공**, 실패/오류/건너뜀 0. frontend `npm run check`와 증적 원본 대조·`git diff --check` 통과. 제품 DB 경계 변경이 없어 전체 `workE2eTest`와 standard/large를 반복하지 않았다. 마지막 전체 PG 체크포인트는 59차다. 최종 일반 검증 뒤 변경은 TODO 주석·문서·측정 증적뿐이다.
- 후속은 benchmark 관계 통계·prepared plan 시작 조건 명시와 scan/loop/buffer 계획 회귀 보강, 필요 범위의 Repository query 개선이다. 원인 조사 완료를 production 성능 수정 완료나 large/운영 검증 완료로 집계하지 않는다. BE-009 보류 유지.
- 사용자 후속 결정: 별도 일괄 정산 구현 후 시작 자동 정산을 제거한다. 시작 실행기·일괄 서비스·원본/연결 조회에 TODO를 표시하고 시작 경로 성능 수정은 보류했다. 정산 계획·대량 처리 검증은 새 기능에서 해당 조회를 재사용할 때 반영한다. 현재 실행 동작·설정은 유지하며 원장 후속과 BE-009 보류는 별개다.

## 커밋 진행

- `7ff08ffa` — 감사 03·06·07·08·09 문서.
- `d0a661d6` — BE-001 판매 수정 예약 identity와 회귀·정책 문서.
- `1509e55f` — BE-002 경매 이력 보존과 rollback·경쟁 회귀·정책 문서.
- `3a5114cc` — BE-003 금액 보호·V35·회귀·정책 문서.
- `7cd07446` — BE-004 판매 가능 상태·조회·회귀·관련 문서.
- `8a1101e2` — BE-005 경매 요청·receipt·V36·화면 키·계약 생성물·회귀·정책 문서.
- `0e803627` — BE-006 판매 수정 합집합 잠금·경쟁 회귀·정책 문서.
- `6b12b411` — BE-006 Farm 단건·일괄 수정 묶음·구역 선잠금·경쟁 회귀·정책 문서.
- `270750ea` — BE-006 Work 구조 기록 전체 원본·구역 선잠금·계획 전 검증·경쟁 및 rollback 회귀·정책 문서.
- `dd69ddc9` — BE-006 일반 계획 전체 대상 잠금·500개 분할·계획/폐기 경쟁 및 rollback 회귀·정책 문서.
- `a5acf3f3` — BE-007 수량 snapshot·V37·보정 정책·capability·계약 생성물·회귀·화면·관련 문서.
- `32ebe83b` — BE-008 Sales 접수·V38·HTTP 계약·화면 키·경쟁/rollback/migration 회귀·관련 문서.
- `275f8f00` — BE-008 Farm 입고 접수·V39·HTTP 계약·공통 화면 키·경쟁/rollback/migration 회귀·관련 문서.
- `b137360e` — BE-008 일반 Work 응답 snapshot·V40·HTTP 계약·화면 키·경쟁/rollback/migration 회귀·관련 문서.
- `26691ad8` — BE-010 전표 생성·묶음 metadata 감사 주체·최종 상태·원문 제외·rollback/중복 방어 회귀·정책 문서.
- `69fd5348` — BE-011 실제 CHECK 기반 대사·제약별 validation·PostgreSQL 회귀·운영 정책.
- `3131f82d` — BE-012 포트 입력 타입 유지·저장 JSON/지문 호환·rollback 회귀.
- `9843c9ea` — BE-012 고정 결과 타입 유지·JSON golden·효과/대상/보정 저장 회귀.
- `8288c839` — BE-012 공통 효과 reader·형식별 정책·호환 fixture·query-count 회귀.
- `4ea56293` — BE-012 공통 명령 집합·구형 입력/receipt 호환·rollback 회귀.
- `2c35562a` — BE-013 Mutation 중첩 v1 지문·schema guard·snapshot/replay 회귀·전환 정책.
- `c0aeb120` — BE-013 Sales·일반 Work 생성 지문·schema guard·구형 접수 replay 회귀.
- `4df04833` — BE-014 저장 handler·정의·strategy 계보 연결과 rollback/replay 회귀.
- `5d2786f8` — BE-015 구조 기록 중간 상세 조회 제거·실제 경로 query-count·rollback/replay 회귀.
- `372efc20` — BE-015 포트 중간 상세/중복 완료 제거·경로별 query-count·snapshot/rollback/replay 회귀.
- `95f594fa` — BE-015 품종별 계획/폐기 중간 상세 제거·query-count·연계 취소/replay·rollback 회귀.
- `8ac145d7` — BE-015 판매 소유 배분/snapshot 일괄 로딩·중복 재조회 제거·query-count·snapshot/replay/rollback 회귀.
- `6d3baa09` — BE-015 정산 원본 Entity graph 대체·500 ID 분할·snapshot/query-count/rollback/replay 회귀.
- `1d62c3c3` — BE-016 중간 생성 DTO 매핑 제거와 네 구조 유형의 배치 속성 회귀.
- `389cbd9b` — BE-017 검사·선택 범위 검증·보상 단계와 취소 정책 회귀.
- `dee4ca93` — BE-018 그래프 출력 상태·노드 factory·상한/순서/JSON 회귀.
- `2ac96878` — BE-019 소비자 기준 값/업무 API·내부 helper 경계·호환/architecture 회귀.
- `c9505f0d` — BE-020 보정 준비/적용·Work 날짜/감사 조율·rollback/동시성 회귀.
- `885d7863` — BE-021 포트 취소 업무 API·Farm adapter·저장 호환/rollback/architecture 회귀.
- `646e0db6` — BE-022 전표 입금 감사 소유권·내부 helper 가시성·감사/rollback/architecture 회귀.
- `75aac8cd` — BE-023 공통 필수/거래처 정책·기본값 참조·HTTP/rollback 회귀.
- `f7f5cf90` — BE-024 미사용 주입/전략 옵션 제거·기존 실행 회귀.
- `6c95e7a0` — BE-025 즉시 명령 전달·actor 정규화·지문/실행 호환 회귀.
- `6bb0bac9` — BE-026 입금 키 충돌 오류·소비자/명세·금융 상태/경쟁 회귀.
- `7aa63f7f` — BE-027 `perf: batch load auction write result histories`. 쓰기 결과 행 N+1·순서/접수 호환·query-count 회귀를 별도 커밋으로 저장한다.
- `c5ee8014` — BE-028 `perf: batch load direct lineage references`. 직접 계보 참조·순서/연령·query-count 회귀를 별도 커밋으로 저장한다.
- `aa675b08` — BE-029 추가 결함 `fix: flush old placement rules before replacement`. 동일 키 교체·삭제 flush/감사 rollback/retry 회귀를 별도 커밋으로 저장한다.
- `d8aaa928` — BE-029 `perf: load placement profiles without inventory groups`. profile 전용 graph·응답/감사/재고 보존과 Entity 적재 회귀를 별도 커밋으로 저장한다.
- `a59b862c` — BE-030 `perf: project work summary origins and relation counts`. Work summary 적재·응답/관계/순서·기존 benchmark 회귀를 별도 커밋으로 저장한다.
- `dd230cd3` — BE-031 `perf: assemble orchid summaries from scalar queries`. 품종 집계/최신일과 자동 그룹 stream·mapper/연령·Entity 적재 회귀를 별도 커밋으로 저장한다.
- `bed7e32a` — BE-032 `refactor: batch partner searches and bound identifier queries`. 다중 검색·배열 membership·참조 입력 분할과 PostgreSQL/검색/Work benchmark 회귀를 별도 커밋으로 저장한다.

- `f9cca5ac` — BE-033 `refactor: index mutation placements within locked batches`. 구역별 scalar 검사·구간 index와 SQL/flush/충돌/rollback/경쟁 회귀.
- `383ec751` — BE-034 `fix: bound calendar history and graph reference retrieval`. 조회 경계와 partial/error 계약·대량 PostgreSQL 회귀·생성 API 계약.
- `6effe1a2` — BE-035 `refactor: index operational reference and date queries`. 실제 PG 계획·index·계보 MIN 개선과 회귀·배포 절차.
- `935f042c` — BE-036 정산 `fix: commit settlement initialization per auction house and day`. 정산별 commit·실패/재시작·정확 key/날짜 계획·V42와 회귀.
- `c085d21f` — BE-036 원장 `refactor: stream reconciliation chains without entity accumulation`. 현재 scalar/chain cursor·기존 fingerprint/판정·snapshot 회귀를 별도 목적으로 저장한다.

- `7b08702e` — BE-037 `test: verify standalone writes and late database rollback`. 독립 Work 취소/출하/입금 경계·정확 CHECK·새 transaction snapshot·재시도 회귀.

- `d1abdf7e` — BE-038 `test: observe postgres conflicts by worker and blocker pid`. 실제 충돌 관측·양쪽 순서·무관 대기자 부정 회귀.

- `5ed1d575` — BE-039 `test: decouple response ids dates and migration head assertions`. JSON path·고정 Clock·작은 농장 fixture·최신 upgrade 회귀.

- `30aca7c2` — BE-040 `test: bound postgres http requests and concurrent waits`. 공통 transport·future·JUnit 상한과 실제 stalled response 회귀.

- `e31b99fa` — BE-041 `test: enforce application members and discover entity writers`. 명시 계약·값 유출·자동 writer 탐지와 부정 회귀.
- `d5ad1753` — BE-042 `test: measure jdbc execution rows and work fan out`. JDBC counters·쓰기 commit/replay·fan-out benchmark와 영구 PG 회귀.
- `c301dfb4` — BE-042 후속 `test: close measurement database without warning inspection`. 전체 DEBUG 환경의 H2 정리 회귀 수정.
- `16f357d4` — BE-043 `docs: align mutation writers and work completion policies`. 단일 writer·취소 대상 terminal·완료 action 문서 일치.
- `222e1d04` — BE-044 `fix: distinguish settlement preferences from execution capabilities`. 저장값 보존·실행 capability·화면/생성 계약·PG 회귀.
- `275bd445` — `docs: record full postgres regression checkpoint`. 전체 PostgreSQL 780건·후속 작업/보류 상태 기록.
- `7329edc6` — `docs: separate remaining audit work from completed changes`. 현재 후속 목록과 완료 이력 분리.
- `5c664962` — `test: add self-service settlement and ledger measurements`. 격리 측정·JSON 전달·실행 가이드·smoke 검증.
- `5519974f` — `docs: analyze standard settlement and ledger measurements`. 사용자 standard 분석·원시 sample 증적 보존.
- `c1b4bd95` — `refactor: bound ledger correction references without entity loading`. Work/Farm scalar 참조·500건 분할·공통 저장 결과 reader·PG 회귀.
- `82444a86` — `docs: compare ledger benchmark after scalar reference reads`. 두 standard 비교·Work 할당량/조회 비용·미확정 지연과 증적.

## 남은 작업

기준: 65차 계획/지연 조사, 64차 standard 전후 비교와 63차 Work 참조 적재 개선, 59차 전체 PostgreSQL 검증을 반영한 현재 후속 목록이다. 앞선 변경 기록의 “남은 범위”는 각 변경 당시의 상태이며 이후 차수에서 해결한 내용을 포함한다. 완료된 구현은 위 변경 기록에 보존하고 여기에서는 중복 집계하지 않는다.

아래는 **바로 조사·보강할 기술 작업 5묶음**, **운영 자료·환경이 필요한 검증 4묶음**, **명시적 보류/범위 판단 3건**으로 구분한다. 묶음 수는 미해결 버그 수·필요 커밋 수·일정 추정이 아니다. 미래 확장은 별도로 두며 현재 필수 개선량에 포함하지 않는다.

### 1. 바로 조사·보강할 기술 작업

| 작업 | 관련 finding | 남은 범위와 완료 기준 |
| --- | --- | --- |
| 정산 초기화·원장 대사의 대량 처리 비용 개선·측정 | BE-015/033/036/042 | 61차 도구·62/64차 standard 수집/전후 비교, 63차 Work Entity 누적·큰 ID 조회 개선, 65차 SQL/계획·코드 비교 조사는 완료했다. 별도 일괄 정산 구현 후 시작 자동 정산을 제거하기로 결정해 관련 TODO를 남겼다. 시작 경로 성능 수정은 보류하고, 재현한 통계 미갱신 join·cached 전체 스캔과 benchmark/plan 회귀는 별도 기능에서 조회 재사용 시 검증한다. 원장은 과거 오류/Work 시간 증가가 비교에서 재현되지 않았으며 필요한 후속 측정은 별개다. 정산별 commit·Entry cursor를 유지하며 필요한 large/운영 증적을 남긴다. 상세는 13·14번 참조. |
| 남은 목록·하위 이력·선택지의 조회 경계 | BE-032/034 | 전체 그룹·sellable·derived member·collection/직접 계보, Work/Inbound/lot/Mutation 상세의 하위 이력, Sales 출하 선택지를 endpoint·소비자별로 조사한다. 전체 matching ID 메모리·응답량·반복 조회를 확인하고 pagination/검색/호환 상한 계약과 필요한 회귀를 정한다. 전체 farm map의 의도된 배치 계약에 임의 cut을 넣지 않는다. |
| 미측정 쿼리와 index 비용 검증 | BE-032/035 | contains/OR/concat·희귀 상태·count/집계·복합 조건·deep offset·generic prepared plan·미측정 FK 등의 실제 Repository 계획을 확인한다. 48·49차에서 검증한 참조/날짜/계보 index를 다시 미완료로 세지 않는다. 결과·행/loop/buffer·쓰기 비용을 비교해 필요한 개선만 채택한다. |
| 기존 접수·응답의 저장 계약 보호 범위 확대 | BE-013 | Mutation 및 Sales/일반 Work 생성의 v1 고정은 완료했다. 그 밖의 Work 실행·구조 기록·포트·취소·보정, Farm 입고·Auction 접수와 저장 응답에서 추가 보호가 필요한 shape/hash를 식별한다. 기존 golden·필드 변경 guard·구형 replay로 보호하며 과거 hash나 응답을 재작성하지 않는다. 신규 version 전환 자체는 아래 조건부 작업이다. |
| 핵심 경로의 선택적 회귀·해석 경계 보강 | BE-012/037/038/039 | 독립 transaction의 후행 실패·실제 잠금 충돌 관측이 빠진 중요한 writer와 보정 이벤트 수량 수지 해석을 선별한다. 필요한 PG/호환 회귀와 해당 경로의 fixture 정리를 추가한다. 모든 H2 시험의 wrapper 전환·모든 lifecycle 분해·자유 JSON의 일괄 타입화를 완료 목표로 삼지 않는다. |

다음 기술 작업은 **다수 정산 쿼리 비용 조사**이며, 오류 대사 지연 증가의 재현도 후속으로 남긴다. Work 참조 적재 개선과 standard 전후 비교는 완료했지만 속도 개선이나 전체 시간 회귀 부재로 판정하지 않는다. large 한계와 실제 운영 부하는 별도 검증이다. 목록·쿼리 개선은 비용과 소비자 계약을 확인한 뒤 진행할 수 있고, 저장 계약 보호는 해당 필드 변경 전에 보강한다. 마지막 회귀·fixture 항목은 위험이 큰 경로부터 점진적으로 수행한다. 미측정 영역 전체에 성능 결함이 확인됐다는 의미는 아니다.

### 2. 운영 자료·환경이 필요한 검증

| 작업 | 관련 finding | 필요한 입력과 완료 기준 |
| --- | --- | --- |
| 과거 데이터 대사·조건부 복구 | BE-001~005/007/008/010/014 | 최신 백업을 복원한 격리 DB에서 예약/allocation·금액/잔액/입금·경매 결과/반환·원장·누락된 이력/감사·unknown 저장 code·중복 후보를 대사한다. 영향 건수와 업무 사실을 확인하고 필요한 경우에만 복구 정책·이력 보존·재대사 증적을 남긴다. 과거 삭제 기록은 복원 가능한 백업/자료가 있어야 복구할 수 있다. |
| 기존 CHECK의 실제 validation | BE-003/011 | 15차 도구와 배포 절차는 완료했다. 대상 DB의 설치 정의·위반 행·convalidated를 확인하고, 필요한 복구와 재대사 후 제약별 validation 및 최종 상태를 기록한다. Flyway 성공을 과거 행 검증 완료로 취급하지 않는다. |
| 운영 과거 요청·저장 JSON의 호환 대사 | BE-013 | 실제 과거 요청 자료와 receipt/Mutation/응답 자료를 확보해 현재 reader·fingerprint·replay와 비교한다. 합성 golden은 실제 운영 corpus를 대체하지 않는다. 원문을 추정하거나 현재 Entity로 과거 snapshot을 만들지 않는다. |
| 운영 부하·index 배포 비용 확인 | BE-015/033/035/036/042 | 운영 규모·분포·PostgreSQL 통계·동시 요청 조건에서 지연/처리량·heap/GC·잠금 대기/보유·긴 snapshot의 vacuum 영향·index 쓰기/WAL/HOT/build 공간·배포 시간을 확인한다. 로컬 측정과 실제 운영 적용 결과를 구분해 증적을 남긴다. |

기존 자료에 실제 위반·중복·누락이 있다고 확정한 상태는 아니다. read-only 대사 결과로 복구 필요 여부를 판단하며, 운영 DB 수정·validation·배포는 대상 자료와 적용 절차를 확인한 뒤 별도 변경 단위로 수행한다.

### 3. 정책 보류와 적용 범위 판단

| 상태 | 관련 finding | 남은 판단 |
| --- | --- | --- |
| 사용자 요청으로 보류 | BE-009 | 일반 수정의 허용 범위와 `RECONCILIATION` 분류·Work 생성 여부. 기존 일반 수정의 Audit/Mutation과 실사/보정 gate를 유지한다. 관련 코드·API·기능 활성화는 변경하지 않는다. |
| 추가 적용 범위 판단 | BE-008 | 일반 생성에 포함하지 않은 입고 포트 계획·폐기 기록 등과 키 없는 연동에 재시도 방어가 필요한지, 같은 입력의 새 업무와 replay를 어떻게 구별할지 정한다. 현재 키 기반 Sales·입고·일반 Work 생성은 구현 완료다. |
| 추가 감사 범위 판단 | BE-010 | 입고·inline 신규 품종 등에 일반 AuditEvent가 추가로 필요한지 판단한다. 기존 WorkEffect·Mutation·Auction 자체 이력을 먼저 확인하며 업무 원장이 있는 경로를 일반 감사 부재만으로 무기록으로 취급하지 않는다. 전표 생성·묶음 metadata 감사는 구현 완료다. |

뒤의 두 항목은 범위 판단이 필요한 사항이며 사용자가 보류한 BE-009와 구분한다. 모든 생성 경로에 멱등 키나 일반 감사 이벤트를 일괄 추가하지 않는다.

### 4. 해당 변경이 생길 때 수행할 조건부 작업

- **새 영속 필드/형식 도입 — BE-013:** 실제 version dispatcher·upgrade, absent/null/default 의미, reader-first 배포, 구버전 writer 병행·rollback 시험. 현재 형식을 바꾸지 않는 동안 신규 형식 migration을 만들 필요는 없다.
- **새 Work 유형·비 HTTP 채널 도입 — BE-014/019:** 새 저장 이름·strategy·계보·취소/replay의 전체 흐름 회귀와 채널별 검증·주체·인가·감사 context. 미래 유형·채널을 미리 구현하지 않는다.
- **새 보상·정산 기능 도입 — BE-007/044:** 경매 결과/반환 이후 정정·보상 이벤트, 월간 정산·자동화·입금 분배·예치금. 별도 기능 범위와 업무 계약을 정한 후 진행한다.
- **테스트/architecture gate 밖의 실제 문제가 확인될 때 — BE-039/040/041/042:** 나머지 fixture 중복·긴 lifecycle 정리, context/JDBC 강제 종료·executor 종료 정책, 동적/alias SQL·reflection·새 원자 writer 검사, driver 내부 round trip 계측을 필요 범위에서 보강한다. 완전한 SQL parser나 모든 writer의 무교착 증명을 독립적인 필수 구현 목표로 두지 않는다.

### 5. 완료되어 현재 미완료 목록에서 제외한 내용

| 이전 목록의 오래된 후속 문구 | 완료 근거 |
| --- | --- |
| BE-015 연계 폐기·Sales·정산 경로의 조회 개선이 남는다는 문구 | 25~27차에서 구현·회귀 완료. 운영 성능은 위 기술 측정/운영 검증에만 남긴다. |
| BE-018 그래프 내부 조회량·truncation과 계보 MIN 반복이 남는다는 문구 | 47차 조회 상한/partial, 48차 계보 MIN 계획 개선 완료. |
| BE-019 Work 보정 조율이 후속이라는 문구 | 32차 BE-020에서 소유권·rollback/동시성 보강 완료. |
| BE-038 fixture·HTTP/future timeout 개선이 남는다는 문구 | 53차 BE-039, 54차 BE-040에서 해당 범위 완료. |
| 이후 차수의 “전체 PostgreSQL 미실행”을 현재 상태로 읽는 경우 | 59차 전체 76개 클래스·780건 통과, 실패·오류·건너뜀 0건. |

그 밖의 완료된 변경 목적은 1~58차 기록에서 확인한다. 새 writer의 잠금 순서·snapshot 보존·호환 검증 같은 상시 구현 규칙은 별도 미완료 작업으로 세지 않는다. 구현 범위 완료, 실제 운영 자료 검증, 미래 기능 확장을 서로 구분하며 BE-001~044 전체가 운영 검증까지 종료됐다고 판정하지 않는다.
