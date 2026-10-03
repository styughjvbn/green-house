# Backend 감사 개선 진행

- 작업일: 2026-10-03
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

## 남은 작업

- BE-001의 기존 운영 데이터 대사·복구는 별도 작업이다. 수정 코드가 기존 allocation/예약/이력을 자동 보정하지 않는다. 기존 read-only 대사로 영향 전표를 확인하고, 이력 보존 및 원장과 일치하는 복구 정책을 정해야 한다.
- BE-002의 과거 삭제 이력은 코드 수정으로 복원되지 않는다. 운영 영향과 복원 가능한 백업·자료의 존재 여부는 확인하지 않았다.
- 다음 P0는 BE-003 직접 판매 금액 overflow, BE-004 판매 가능 상태 정책의 조회·예약 통일, BE-005 경매 부분 결과·반환 재전송의 중복 반영 방지다.
- BE-006의 전역 lock ordering, 성능·추상화·테스트 체계의 나머지 finding은 후속 변경으로 남긴다. 이번 변경으로 전체 P0 또는 운영 정합성이 해결됐다고 판정하지 않는다.
