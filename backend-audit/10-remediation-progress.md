# Backend 감사 개선 진행

- 작업일: 2026-10-03~2026-10-04
- 브랜치: `fix/backend-sales-reservation-consistency`
- 시작 기준: `80106917232a671b5a489ad8e59d37b63a06dffe` (`develop`)
- 우선순위 기준: [08 통합 findings](08-findings.md), [09 최종 평가](09-final-assessment.md)의 P0. 기존 감사 문서는 수정 전 판단의 근거로 보존한다.

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
- BE-015 포트 — `refactor: assemble inbound potting responses once`. 중간 상세/중복 완료 제거·경로별 query-count·snapshot/rollback/replay 회귀를 별도 커밋으로 저장한다.

## 남은 작업

- BE-001의 기존 운영 데이터 대사·복구는 별도 작업이다. 수정 코드가 기존 allocation/예약/이력을 자동 보정하지 않는다. 기존 read-only 대사로 영향 전표를 확인하고, 이력 보존 및 원장과 일치하는 복구 정책을 정해야 한다.
- BE-002의 과거 삭제 이력은 코드 수정으로 복원되지 않는다. 운영 영향과 복원 가능한 백업·자료의 존재 여부는 확인하지 않았다.
- BE-003의 과거 잘못된 금액·잔액·입금은 별도 대사·복구 대상이다. V35 적용만으로 운영 자료의 정합성이 입증되거나 기존 위반 행이 모두 검증되는 것은 아니다.
- BE-004 수정 전 확정된 판매 불가 상태의 예약은 자동 해제하지 않았다. 운영 영향과 기존 출고 여부는 별도 대사·업무 판단 대상이다.
- BE-005의 기존 중복 결과·반환 및 정산 영향은 별도 대사 대상이다. 기존 기록을 중복으로 추정해 삭제하지 않는다.
- BE-006의 예정 수정 범위는 완료했다. 판매 교차 수정, Farm 단건·일괄 수정, Work 구조 기록과 Farm 경쟁, 일반 품종별 계획·폐기/Farm·구조 기록·겹치는 계획 경쟁을 검증했다. 경로별 전체 잠금 순서와 기존 대상 변경 거절 계약을 유지한다. 모든 writer·FK·내부 fence의 무교착을 증명한 것은 아니며 새 writer에는 같은 경로별 순서와 경쟁 회귀가 필요하다.
- BE-007의 신규 수량 이력과 경매 시도·반환 확인 이후 직접 보정 제한을 완료했다. 과거 누락된 이력과 수량 불일치는 자동 복원하지 않았으며 운영 데이터 대사가 남는다. 결과 이후의 보상·정정 이벤트는 별도 업무 계약과 구현이 필요한 후속 범위다.
- BE-008의 키가 있는 판매·입고·일반 Work 계획/완료 기록 생성 재전송 방어를 완료 대상으로 삼는다. 기존 자료의 중복 대사와 키 없는 연동의 재시도 정책은 남는다. 입고 포트 계획·폐기 기록 같은 별도 생성 경로는 이번 일반 생성 계약에 포함하지 않으며 필요 시 업무별 재시도 의미부터 정의한다. 후속 작업은 감사 우선순위에 따른 정책 일치·감사 완전성·DB 제약 상태·조회 비용 보강이다.
- BE-009는 일반 metadata·수량·상태·위치 수정의 허용 범위와 보정/실사 제한의 정책 일치가 남는다. 이번 감사 보강은 기존 현장 수정 기능을 임의로 차단하지 않는다.
- BE-010의 전표 최초 생성과 난 묶음 metadata 누락은 14차 범위다. 과거 감사의 복원·입고/inline 품종 등의 필수 생성 감사 범위는 별도 판단이며, 자체 업무 이력을 일반 감사 부재만으로 무기록으로 취급하지 않는다.
- BE-011의 운영 대사·제약별 validation 도구와 rehearsal 회귀는 15차 범위다. 운영 DB의 `convalidated`, 위반 행과 교차 불변식은 조회하지 않았다. 운영 적용·승인된 복구·실제 validation 완료와 그 증적이 남으며 이번 커밋을 운영 데이터 검증 완료로 취급하지 않는다.
- BE-012 포트 입력 타입 유지와 JSON·지문 호환/rollback은 16차, 고정 결과 타입 유지와 JSON 저장 회귀는 17차, 대상·상세·계보·수량의 공통 효과 reader와 형식별 정책 보존은 18차 범위다. 공통 명령 Object/구형 구조 변경·현장 동기화 HTTP DTO 경계 이식과 receipt 호환은 19차 범위다. 자유 JSON·보정 이벤트 해석은 남으며, BE-013의 실제 저장 version 전환과 운영 과거 요청 corpus 검증도 후속 범위다.
- BE-013의 Mutation 중첩 v1 지문 고정·필드 변경 검출·snapshot/구형 지문 replay·배포 기준은 20차 범위다. 실제 format version 전환과 Work/Sales 등 다른 접수 계약·운영 과거 요청 corpus 검증은 남는다.
- BE-013의 Sales 생성과 일반 Work 3생성 경로 v1 지문·필드 변경 검출·구형 접수 replay는 21차 범위다. 그 밖의 명령/응답과 실제 version 전환·운영 corpus 검증은 남는다.
- BE-014의 저장 구조 handler와 계보 분류 계약·strategy 관계 재사용·새 결과 검사·전체 흐름 회귀는 22차 범위다. 신규 유형의 미래 확장 회귀와 운영 unknown code 대사는 별도다.
- BE-015의 구조 변경 기록 계획/시작/실행의 중간 상세 조회 제거와 단건·배치 query-count·rollback/replay 회귀는 23차 범위다. 포트는 24차 범위로 이어졌으며 연계 폐기·Sales·정산 경로와 운영 지연 측정은 남는다.
- BE-015의 포트 신규/활성 계획·단독 실행/기록의 중간 상세/중복 완료 제거와 query-count·snapshot·완료 판정·rollback/replay 회귀는 24차 범위다. 연계 폐기·Sales·정산과 운영 지연 측정은 남는다.
- 성능·추상화·테스트 체계의 나머지 finding도 후속 변경으로 남긴다. P0 5건의 신규 쓰기 방어를 수정해도 과거 데이터 대사와 다른 정합성 위험은 남는다.
