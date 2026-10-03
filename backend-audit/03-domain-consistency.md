# Backend 도메인·트랜잭션·일관성 평가

평가일: 2026-10-03, Asia/Seoul. 기준: `develop`, HEAD `80106917232a671b5a489ad8e59d37b63a06dffe`, 조사 시작 시 작업 트리 clean.

현재 Controller → application → domain/policy → Repository → Flyway 제약을 추적했다. 이전 평가 문서의 commit과 이번 기준은 다르다. 정책 기준은 [도메인 모델](../docs/02-domain-model.md), [백엔드 구현 기준](../docs/04-architecture.md), [작업 정책](../docs/features/work-operation-and-orchid-collection.md), [판매·경매·정산 정책](../docs/features/sales-auction-settlement.md), [DOMAIN_RULES](../docs/api/DOMAIN_RULES.md), [ADR-001](../docs/adr/ADR-001-orchid-group-mutation-engine.md)이다. archive는 현재 정책의 근거로 사용하지 않았다.

## 1. 결론

**최상위 transaction과 Mutation/Work/입금의 기본 원자성은 잘 갖춰져 있다. 그러나 판매 수정에서 멱등 키가 실제 업무 변경을 누락시키고, 경매에서는 상태 변경을 통해 실제 결과 이력을 삭제할 수 있다. transaction이 성공해도 도메인 일관성이 깨지는 경로가 존재한다.**

| ID | 심각도 | 확인된 문제 | 증거 수준 |
| --- | --- | --- | --- |
| DC-01 | High | 품목만 수정하면 재예약이 이전 Mutation replay로 처리되어 allocation은 남고 예약은 사라짐 | 실제 PostgreSQL 재현 |
| DC-02 | High | 일반 판매의 품목 금액·전표 합계가 int overflow; 음수 금액 저장 가능 | PostgreSQL 저장 + Java 도메인 재현 |
| DC-03 | High | 판매 불가 상태 난 묶음도 예약·판매 전표 생성 가능 | 실제 PostgreSQL 재현 |
| DC-04 | High | 경매 결과가 있는 lot를 WAITING으로 바꾼 뒤 전표 취소하면 결과·시도·상태 이력 삭제 | 실제 PostgreSQL 재현 |
| DC-05 | High | 자동 차수 부분 낙찰·부분 반환의 동일 요청이 수량을 다시 누적 | PostgreSQL 결과 중복 + Java 반환 재현 |
| DC-06 | Medium | 기존 예약 해제와 신규 예약 대상의 잠금 순서가 전역적으로 정렬되지 않아 교차 수정 deadlock | PostgreSQL `40P01` 재현 |
| DC-07 | Medium | 같은 상태에서 반환·수량을 바꾸면 변경 이력 누락; lot 수량 보정과 결과/정산 사이 연결 부족 | Java 재현 + end-to-end 소스 확인 |

High는 금액·수량·과거 사실의 잘못된 확정/삭제, Medium은 정상 요청 실패 또는 감사·재조정의 공백이다. 운영 DB에서 이미 발생했다는 판정은 아니다.

## 2. transaction과 rollback의 공통 경계

| 경계 | 실제 구현 | 판단 |
| --- | --- | --- |
| 최상위 쓰기 | OrchidGroupCommand, InboundRecord, Work 계획/진행/실행/취소/보정, Sales 생성/수정/상태, AuctionTracking 쓰기, Settlement 재구성/입금에 `@Transactional` | 한 HTTP 유스케이스의 동기 DB write는 기본 REQUIRED transaction에 참여 |
| 하위 필수 참여 | Mutation Engine, WorkCommandReceipts, WorkOperationLockService, BusinessPartnerLock, AuctionShipmentCreator/Lifecycle 쓰기, PaymentLedgerService, JpaAuditRecorder는 `MANDATORY` | 독립 transaction을 열고 lock을 즉시 해제하는 형태를 막음 |
| annotation 없는 helper | WorkEffectProcessor/Store, SalesSlipInventory/Outbound, MutationRecorder는 caller transaction 안에서 호출 | annotation 부재 자체는 원자성 결함이 아님. 독립 호출은 이 helper의 외부 계약으로 보장되지 않음 |
| JDBC와 JPA | 같은 DataSource의 JdbcTemplate로 receipt claim, 전표 번호 발급, transaction-local fence context 설정 | PostgreSQL 회귀에서 JPA 업무 변경과 함께 rollback 확인 |
| 실패 처리 | 도메인 예외·DB 예외는 RuntimeException 계열. JpaAuditRecorder는 catch 후 재throw | 감사 실패를 삼키거나 기록만 별도 commit하는 경로 없음 |
| isolation/재시도 | 조사 범위에 별도 isolation·REQUIRES_NEW·자동 deadlock retry 없음 | PostgreSQL 기본 READ COMMITTED와 row lock/버전에 의존. conflict는 사용자 재요청 필요 |

private overload 호출은 최상위 public method가 이미 transaction을 연 경우에 사용된다. transaction 개시를 self invocation에 의존하는 문제로 판정하지 않았다. sequence/pooled ID는 rollback되어도 번호 공백을 허용하며, 업무 사실의 부분 commit과 구분한다.

감사 원자성 근거: [JpaAuditRecorder.record](../backend/src/main/java/com/greenhouse/backend/audit/application/JpaAuditRecorder.java), [AuditEventWriter.recordChanges](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditEventWriter.java). 감사 저장은 caller와 함께 rollback된다. 다만 변경 필드가 없으면 이벤트를 만들지 않으며, 모든 업무 생성·수정에 AuditEvent가 있다는 보장은 별개다.

## 3. OrchidGroup invariant와 Mutation Engine

### 3.1 현재 상태의 불변식

| 불변식 | 적용 지점 | 보장 및 한계 |
| --- | --- | --- |
| `quantity >= 0`, `0 <= reservedQuantity <= quantity` | [OrchidGroup](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) 생성/수정/보정/reserve/release/outbound; V14 CHECK | 예약 침해 감소·음수 수량 방어. DB CHECK는 `NOT VALID`: 기존 위반 행의 정리가 증명된 상태는 아님 |
| 구조 변경·폐기의 사용량은 가용 수량 이하 | `applyTransformation`, `discard` | 예약된 수량을 구조 변경/폐기로 소비하지 않음 |
| 논리 구역에 배치, 범위·충돌 검증 | `bed_zone_id` NOT NULL/FK, [OrchidPlacementPolicy](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/OrchidPlacementPolicy.java), Engine zone lock | 양수 수량의 배치 충돌은 application 정책으로 검사. DB exclusion constraint는 없음 |
| 예약 합계 = 작성중 allocation 합계 | Sales 유스케이스의 reserve/release 조율 | 개별 Entity CHECK로는 보장되지 않음. DC-01에서 실제 불일치 발생 |
| 판매 가능 상태 | [OrchidGroupStatusPolicy.isSaleable](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupStatusPolicy.java) | 목록/집계의 정책이 예약 명령에 적용되지 않음: DC-03 |
| 과거 결과의 안전한 취소 | usage inspector + effective-head 검사 + compensation | 후속 상태를 과거 snapshot으로 덮어쓰지 않도록 방어 |
| version과 상태 revision | `@Version`과 `stateRevision` 분리 | version은 ORM 충돌, revision은 원장 상태 순서. 판매 업무 멱등 identity로 version을 사용하는 것은 DC-01의 원인 |

`outboundReserved`는 수량·예약을 같이 줄이되 상태를 자동으로 판매 완료로 바꾸지는 않는다. 수량 0 여부와 status를 동일한 상태 축으로 취급하면 안 된다. `restoreOutbound`는 수량을 더하며 이전 품종·상태·위치 전체를 덮어쓰는 복원은 아니다.

### 3.2 Engine의 실행 순서

[OrchidGroupMutationEngine](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java)의 create/transform/update/move/quantity/correct/compensate 경로를 확인했다.

1. typed command 정규화·fingerprint 계산 → source identity로 기존 Mutation 확인.
2. 대상 root lock → 같은 identity 재확인. 그룹·목적지 구역의 다건 조회는 ID 오름차순.
3. 현재 수량·예약·baseline·품종·배치를 검증. before snapshot을 상태 변경 전에 생성.
4. 상태 변경과 revision 증가 → header/context/Entry 저장. create/transform은 신규 그룹 저장 전에 header와 context를 설정한다. 일부 update/quantity 경로는 메모리 변경 뒤 recorder가 header/context를 설정한다.
5. WorkEffect·SalesInventoryMovement·Lineage·Audit 등 caller의 후속 저장과 함께 commit. 보정·보상은 원본 Mutation과 relation 저장.

원장 저장: [MutationRecorder](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationRecorder.java). 재요청: [MutationReplayResolver](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationReplayResolver.java). 같은 source/fingerprint는 저장된 Entry 결과를 반환하고, 다른 payload는 ConflictException이다. 이는 **source key를 올바르게 발급한 경우**의 보장이다. caller가 새 업무에 과거 key를 재사용하면 Engine은 이를 신규 변경으로 판단할 수 없다.

보상은 선택한 Mutation의 연속 chain, 이미 보상했는지, 현재 effective head, 복구 위치·표시 순서 충돌을 검사한다. 원본 Entry를 삭제하지 않고 새 COMPENSATION과 COMPENSATES relation을 추가한다. [EffectiveHeadPolicy](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEffectiveHeadPolicy.java)는 이미 상쇄된 구간을 실제 revision/snapshot 연결로 검사한다.

### 3.3 DB fence의 실제 보장 범위

[V21](../backend/src/main/resources/db/migration/V21__add_orchid_group_mutation_engine.sql)은 source UNIQUE, 그룹별 revision UNIQUE, Mutation별 그룹 UNIQUE, Entry kind/revision/snapshot 존재 조건을 제공한다. [V22](../backend/src/main/resources/db/migration/V22__enforce_orchid_group_mutation_write_fence.sql)은 ACTIVE일 때 context 없는 INSERT/UPDATE와 모든 물리 DELETE를 차단하고, revision 증가와 대응 Entry 존재를 commit 시 검사한다.

한계는 다음과 같다.

- context는 `MUTATION:<숫자>` 형식만 확인한다. 해당 숫자의 header 존재·해당 Entry의 mutation_id와 일치까지 검사하지 않는다.
- deferred trigger는 그룹 ID/revision에 Entry가 있는지 검사한다. `after_state`와 실제 행이 같은지, 앞 Entry의 after와 다음 before가 같은지는 DB trigger의 검사가 아니다.
- 원장 테이블 자체의 일반 UPDATE/DELETE를 막는 append-only trigger는 확인되지 않았다. Entry에 orchid_groups FK를 두지 않은 것은 과거 삭제 tombstone 보존과 양립하는 설계다.
- ACTIVE coverage가 없으면 fence는 우회된다. startup guard는 baseline 누락/PREPARING/minimum writer version을 검사하지만, 모든 기동에 전체 reconciliation을 수행하지 않는다.

따라서 DB fence를 완전한 원장·snapshot 동치 보장으로 표현하면 과장이다. 일반 application write에서는 Engine과 도메인 검증이 추가 보호한다. [LedgerReconciliationService](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerReconciliationService.java)와 [SalesRehearsalInspector](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupLedgerRehearsalInspector.java)는 chain/current state/예약 합계를 사후 검출한다. 사후 진단은 잘못된 commit을 막는 constraint와 다르다.

## 4. Work와 구조 변경의 end-to-end 추적

### 4.1 계획·대상·일반 실행

`WorkOperationController → WorkOperationPlanService → WorkTargetResolver → WorkOperationAggregateCreator → WorkOperation/Target/Execution`.

- [PlanService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java)는 선택 범위를 resolve하고 제외 대상·품종별 계획 규칙을 검증한다.
- [AggregateCreator](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationAggregateCreator.java)는 저장 전 대상 그룹을 잠그고 활성 여부를 다시 확인한다. 선택 당시 수량·품종·위치 등은 Work target snapshot으로 보존된다.
- **잠금 전 조회의 stale snapshot 가설을 실제 병렬 실행으로 확인했다.** resolve 이후 다른 transaction이 수량 100→60을 변경한 경우 Farm resolver의 pessimistic 조회에서 version 충돌을 잡아 `WORK_TARGET_CHANGED`로 거절했고, Work target은 저장되지 않았다. 이번 실험을 snapshot 오염 결함으로 분류하지 않는다.
- 계획/키 없는 일반 기록 API의 HTTP 재전송은 새 작업을 만들 수 있다. [작업 정책](../docs/features/work-operation-and-orchid-collection.md)의 명시된 범위와 일치하며, 키 있는 즉시 실행·구조 변경·포트 기록과 구분해야 한다.

`ProgressService.completeTarget → WorkEffectProcessor → handler → Farm Engine → WorkEffectStore → Execution 완료 → 전체 완료 판정`.

[ProgressService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java)는 Work root와 대상 실행을 잠그고 완료 효과의 replay를 먼저 확인한다. 신규 효과만 IN_PROGRESS 조건을 요구한다. 효과 identity는 `(workOperationId, effectKey)`이며 [V26](../backend/src/main/resources/db/migration/V26__align_work_effect_idempotency.sql)의 UNIQUE가 effectKind와 무관하게 중복을 막는다. 마지막 대상 실패 시 같은 transaction의 앞 효과도 rollback된다.

[WorkEffectStore](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java)는 원문 지문, 실행 시점, worker, command/result, 그룹 SOURCE/RESULT 링크, mutationId/correlationId를 함께 저장한다. 기록 전용 효과는 Mutation이 없어도 정상이다. 모든 handler에 Mutation이 있다고 가정하면 안 된다.

전체 완료는 대상 terminal 여부를 검사한다. 실제 `isTerminalForCompletion`은 COMPLETED/SKIPPED뿐 아니라 CANCELED도 포함한다. 입고 부분 취소와 연결된 동작이다. DOMAIN_RULES의 “모든 대상 COMPLETED 또는 SKIPPED” 설명은 이 예외를 반영할 필요가 있다.

### 4.2 분갈이·분주·합식·자리 이동

`StructureChangeRecordService/StructureChangeExecutionService → Work root/execution lock → StructureChangeExecutor/Strategy → BatchStructureTransformationExecutor → Engine.transform 또는 moveAll → 효과·계보·처리량`.

| 단계 | 실제 규칙/저장 |
| --- | --- |
| 즉시 기록 | receipt claim → 계획 생성 → start → 전체 원본 수량 실행. 일괄 기록은 한 transaction이며 두 번째 기록 실패 시 첫 번째 결과와 receipt도 rollback |
| 계획 실행 | 회차 key replay를 IN_PROGRESS 검사보다 먼저 처리. 선택 원본은 확정된 target 안에 있어야 하며 중복 금지·누적 계획 잔여량 검사 |
| 현재 재고 | 원본 그룹 lock 후 현재 가용량·품종 검증. Work의 계획 수량과 현재 재고 검증은 서로 다른 책임 |
| 수량 규칙 | REPOT/DIVIDE는 증식 허용. MERGE/MOVEMENT는 결과 합계가 투입 합계 이하. 과거 수량 수지 정정은 별도 WorkQuantityBalancePolicy에서 검사 |
| 속성/snapshot | 결과 상속 계획은 원본을 차감하기 전에 계산. Engine은 원본 before/after와 신규 결과 CREATE snapshot을 저장 |
| N:M 계보 | source/result 효과 링크와 실행 회차가 권위 있는 연결. 단일 source 호환 Lineage만 추가 저장하며 모든 원본×결과 조합을 임의 생성하지 않음 |
| 전량 1:1 이동 | 기존 그룹 ID와 속성을 유지하고 MOVE 저장. 그 밖의 이동은 결과 생성과 MOVED_TO 회차 연결 |
| 이동 잔량 폐기 | 이동 후 잔량을 별도 MOVEMENT_DISCARD Work로 비례 배분·차감. 부모 이동/연관 폐기/여러 Mutation/WorkEffect가 같은 최상위 transaction |

근거: [StructureChangeExecutionService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeExecutionService.java), [BatchStructureTransformationExecutor](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java), [StructureChangeStrategy](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategy.java), [DiscardRecordService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/DiscardRecordService.java).

### 4.3 취소·보정

- [WorkOperationVoidService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java)의 가능 여부 조회는 advisory다. 실행은 root/execution/group lock 이후 Farm port에서 외부 참조·후속 Mutation·복구 위치를 재검증한다.
- 구조 변경/폐기의 단건·일괄 취소는 새 compensation → effect 취소 시각 → 원본 VOIDED와 compensation ID를 함께 저장한다. 원래 완료 실행·효과·Entry를 삭제하지 않는다. 연관 폐기는 부모 이동과 함께 취소한다.
- 포트 취소는 생성 그룹의 CREATE를 보상하고 입고를 POTTING_PENDING으로 다시 연다. 그룹에 후속 변경·판매·다른 유효 Work가 있으면 거절한다.
- [WorkOperationCorrectionService](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java)는 correction receipt → 원본 root lock → Farm 검증 → correction event ID 확정 → Mutation → 작업일/보정 결과 저장 순서다. 보정은 독립 WorkOperation이 아니다. 날짜만 변경하면 Mutation을 만들지 않는다.
- [FarmWorkCorrectionAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java)는 결과 대상·후속 사용·실사 이후 변경·수량 수지를 검사한다. 보정 실패 시 receipt/event/Mutation/전체 결과가 rollback된다.
- `features.work-quantity-correction.enabled`, stock-count gate가 비활성화된 상태와 활성화 테스트를 구분했다. 실사·수량 정정 보류가 모든 일반 PATCH 수량 변경까지 금지한다는 의미는 아니다.

근거: [FarmStructureChangeVoidAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmStructureChangeVoidAdapter.java), [FarmPottingVoidAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/FarmPottingVoidAdapter.java), [WorkQuantityBalancePolicy](../backend/src/main/java/com/greenhouse/backend/work/domain/correction/WorkQuantityBalancePolicy.java), [OrchidStockCountService](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidStockCountService.java).

농장 기준 구조의 배치 수용 프로필 변경도 확인했다. [BedPlacementProfileService.updateProfile](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/BedPlacementProfileService.java)는 전체 규칙 검증 → capacities 교체 → Audit을 한 transaction에 저장한다. BedZone의 row lock/@Version 없이 교체하므로 동시 편집 직렬화는 보장되지 않는다. 규칙 자연키 UNIQUE는 중복 행을 막지만 전체 프로필의 lost update까지 막는 장치는 아니다. 실제 동시 편집 재현은 미실행이다.

## 5. Inbound의 end-to-end 추적

| 흐름 | transaction 안의 순서 | 일관성 판단 |
| --- | --- | --- |
| FLASK 입고 | 품종 resolve/create → Inbound 저장(PENDING) → INBOUND Work/Target/기록 효과 저장 | 입고 사실과 시스템 Work 원자 저장. 예상량과 추후 실물 결과량은 같아야 하는 invariant가 아님 |
| 즉시 배치 입고 | Inbound 저장 → 배치 범위 resolve → Engine.createFromInbound → 그룹/CREATE Entry → PLACED → INBOUND Work/효과 link | 입고/품종/그룹/원장/시스템 Work가 함께 rollback |
| 포트 계획 | 연결 입고와 형제 입고 lock → 현재 상태 검증 → 품종별 계획/target/execution → IN_PROGRESS 입고 | 같은 입고에 활성 포트 계획을 만드는 경쟁을 잠금/재확인으로 조율 |
| 즉시/계획 포트 실행 | receipt → 형제 입고/Work/execution lock → 실행 전 입고 snapshot 갱신 → Engine.createFromInbound → 입고 PLACED → 효과/결과 그룹/완료 상태 | 같은 입고를 여러 요청이 중복 생성하지 않음. 실제 FK 존재를 lock 이후 별도 쿼리로 재확인 |
| 포트 무효화 | 기존 요청 receipt 확인 → 연결 입고/Work/group lock → 생성 보상 → Work VOIDED/effect canceled → 입고 PENDING → Audit | replacement 포트 작업이 생긴 뒤 과거 undo 재요청으로 새 작업을 되돌리지 않음 |
| 입고 취소 | 연결 Work/형제 입고 lock → 필요하면 CREATE 보상 → 연결 작업 취소/무효화 → Inbound CANCELED → Audit | 입고 기록 물리 삭제 없음. 이후 참조·변경이 있으면 거절 |

근거: [InboundRecordService](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java), [InboundPottingOperationService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java), [InboundPottingService](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingService.java), [InboundWorkOperationLifecycleService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundWorkOperationLifecycleService.java), [InboundRecord](../backend/src/main/java/com/greenhouse/backend/farm/domain/inbound/InboundRecord.java).

입고 생성 자체에는 요청 receipt/key가 없다. 같은 HTTP body를 재전송하면 새 Inbound ID와 새 Work가 생성될 수 있다. Engine의 입고 ID별 중복 방어는 **동일 입고의 중복 결과 생성**을 막으며 **중복 입고 생성 요청**을 막지는 않는다. 취소는 이미 CANCELED이면 현재 결과를 반환하지만 바뀐 취소 사유를 같은 요청으로 검증하는 receipt 계약은 없다. 포트 실행/무효화의 엄격한 멱등성 범위와 구분해야 한다.

## 6. Sales reserve → outbound → cancel 추적

| 흐름 | 실제 저장 순서/잠금 | snapshot·이력 |
| --- | --- | --- |
| 생성 | 활성/유형 거래처 검사 → DIRECT partner lock → 일별 번호 원자 발급 → allocation group lock → Slip/Item/Allocation 저장 → reserve → 필요하면 완료 처리 → DIRECT 잔액 갱신 | CREATION은 group lock 후 예약 증가 전 값. reserve Mutation과 배분별 movement 저장 |
| 수정 | Slip lock → 기존/신규 partner ID 정렬 lock → 기존 예약 release → 신규 allocation group lock → 기존 Item/Allocation 교체 → Slip flush → reserve → 양쪽 잔액/Audit | 작성중 DIRECT만 허용. 전체 transaction이나 DC-01/DC-06 존재 |
| 완료 | Slip lock/상태 전이 → allocation group lock → OUTBOUND capture → AUCTION shipment/lot 생성 → consume reservation → movement → Audit | OUTBOUND는 실제 차감 전. shipment 생성 실패·재고 실패 시 전표 상태 포함 rollback |
| 작성중 취소 | Slip lock → DIRECT partner lock/입금 이력 검사 → release → 경매 취소 검증 → Slip 취소/잔액/Audit | 실수량을 늘리지 않음 |
| 완료 취소 | Slip lock → 입금/경매 결과·정산 조건 검사 → group lock/복원 배치 검증 → restore outbound → 경매 삭제 → Slip 취소/잔액/Audit | 수량 복구와 movement/Mutation 원자 처리. 현존 outbound Mutation에 COMPENSATES; 전환 전 자료는 legacy source |

근거: [CreationService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java), [UpdateService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java), [StatusService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipStatusService.java), [OutboundService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipOutboundService.java), [InventoryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java), [AllocationFactory](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationFactory.java).

일별 전표 번호는 [SalesSlipNumberRepository](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipNumberRepository.java)의 PostgreSQL UPSERT RETURNING으로 직렬화한다. 번호 유일성은 생성 요청 멱등성과 다르다. 같은 판매 생성 body를 재전송하면 새 번호/Slip/reserve가 생길 수 있다.

### DC-01 — High: 판매 수정의 version 기반 Mutation key가 예약을 누락

**재현:** 10개·단가 1,000원의 작성중 전표를 생성한 뒤 판매일·거래처·금액·메모는 유지하고 품목 `spec`만 변경한다. 실제 PostgreSQL에서 수정이 성공했지만 allocation=10, `reserved_quantity=0`이었다. SALES_RESERVE movement는 2건이었다.

원인:

1. 최초 예약의 key는 `RESERVE:<SalesSlip.version>`이며 최초 version은 0이다.
2. 수정은 `RELEASE_EDIT:0`으로 실제 예약 10을 해제한다.
3. 품목/배분 Entity만 변경하고 Slip root 값이 같으면 root version이 증가하지 않는다. `saveAndFlush` 호출 자체가 version 증가를 보장하지 않는다.
4. 재예약이 `RESERVE:0`을 재사용한다. 같은 수량/group/date/memo라서 Engine은 최초 예약의 기존 Mutation을 반환한다. 실제 reserve는 수행하지 않는다.
5. Sales는 replay 결과로 새 reserve movement를 저장하고 성공 응답한다. Entity CHECK와 원장 revision chain은 정상이어도 Sales allocation과 예약 합계가 달라진다.

같은 조건에서 allocation만 다른 그룹으로 바꾸면 기존 `RESERVE:0`과 fingerprint가 달라 ConflictException으로 정상 수정이 거절되는 것도 재현했다. 문제는 Engine replay 구현보다 **caller의 업무 identity 발급**이다.

근거: [InventoryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java) `reserve/releaseForEdit/recordMovements`, [UpdateService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java) 70행 이후, [SalesSlip](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlip.java)의 root version/amount 갱신, [SalesSlipItem](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipItem.java)의 독립 수정.

영향: 예약이 없어 다음 출고/취소가 실패하거나, 같은 그룹의 다른 전표 예약을 사용할 수 있다. 실제 후속 전표 간 예약 소비까지는 이번 probe에서 실행하지 않았다. `SalesOrchidGroupLedgerRehearsalInspector`는 사후 `SALES_RESERVATION_MISMATCH` 검출을 제공하지만 수정 commit을 막지 않는다.

개선: JPA version과 별개인 판매 변경 operation identity를 같은 transaction에 발급하고 release/reserve를 그 변경에 연결한다. replay에는 movement도 중복 생성하지 않게 한다. spec/품목 메모/동일 총액 배분 변경의 회귀 테스트가 필요하다.

### DC-02 — High: 일반 판매 금액 overflow

실제 PostgreSQL에서 수량 2, 단가 1,500,000,000원의 전표가 `total_amount=-1,294,967,296`으로 저장되고 2개가 예약됐다. 입력은 기존 양수/0 이상 validation 범위 안이다.

[SalesSlipItem](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipItem.java) 78/103행의 `quantity * unitPrice`, [SalesSlip.recalculateAmounts](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlip.java) 237행의 `mapToInt(...).sum()`에 범위 검사가 없다. Java probe에서는 각 1,500,000,000원인 두 품목 합계도 음수였고 잔액은 0으로 clamp됐다. DB에는 해당 금액의 비음수·계산 동치 CHECK가 없다.

경매 낙찰의 단일 행은 `Math.multiplyExact`로 거절하므로 일반 판매와 정책 구현이 다르다. 개선: 금액 계산을 domain에서 long/정확 연산으로 모으고 저장 범위를 검증한다. 품목 곱·전표 합계·잔액 각각의 경계값 테스트와 DB 제약 검토가 필요하다.

### DC-03 — High: 판매 가능 상태 검증 누락

상태가 `병해충`인 100개 그룹으로 전표를 생성했고 PostgreSQL에서 예약 10이 확정됐다.

[AllocationFactory](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationFactory.java)는 품종명/배분 합계만 검사한다. Engine.reserve → [OrchidGroup.reserve](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) 313행은 가용량만 검사하며 `isSaleable`을 호출하지 않는다. `searchSellable` 쿼리도 가용량/선택 status 조건만 적용하므로 검색 단계에서도 제외 정책이 강제되지 않는다.

집계는 `OrchidGroupStatusPolicy.unavailableForSaleStatuses`를 적용하지만 명령이 이를 우회한다. 개선: 예약 가능 상태를 Farm domain/policy의 단일 규칙으로 검증하고 검색/capability도 공유한다. 신규 예약 검사와 기존 예약의 출고/해제 정책은 구분해야 한다.

### DC-06 — Medium: 예약 대상 교체의 deadlock

서로 다른 거래처의 두 전표가 각각 G2/G1을 예약한 상태에서 T1은 G2→G1, T2는 G1→G2로 동시에 배분을 변경했다. 기존 release 이후 두 thread를 맞춰 실행하자 PostgreSQL `40P01 deadlock detected`, `orchid_groups` tuple lock 교착을 확인했다.

| 시점 | T1 | T2 |
| --- | --- | --- |
| 기존 예약 해제 | G2 lock 유지 | G1 lock 유지 |
| 신규 allocation 조회 | G1 lock 대기 | G2 lock 대기 |

각 `IN ... ORDER BY id`가 정렬돼도 **서로 다른 호출에서 누적 취득하는 lock 집합**은 정렬되지 않는다. 같은 거래처 요청은 partner lock으로 직렬화되지만 다른 거래처에서는 보호되지 않는다. 교착 후 DB rollback은 작동한다. 이 probe에서는 다른 요청도 DC-01의 key 충돌로 실패했으므로 “한 요청은 반드시 성공한다”는 보장은 하지 않는다.

개선: 기존/신규 allocation ID 합집합을 첫 수량 변경 전에 정렬해 잠근다. [OrchidGroupCommandService.updateBatch](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java)와 품종별 구조 변경 기록의 입력 순서별 누적 잠금도 같은 패턴의 정적 위험이 있다. 이 두 경로의 실제 교착은 미재현이다.

## 7. Auction의 end-to-end 추적

`출하 완료 → shipment/lot 생성 → 결과 입력 → attempt/result line → sold/waiting/returned 및 상태 이력 → 반환 확인/수량 보정 → Settlement 재구성`.

[AuctionTrackingService](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)의 모든 쓰기는 lot row lock 후 [AuctionShipmentLot](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipmentLot.java) 메서드를 호출한다. lot `@Version`, `(lot, auctionDate, attemptNo)` UNIQUE가 동일 차수 충돌을 방어한다. 결과 행/lot 수량/상태 이력은 cascade로 같은 transaction에 저장된다.

낙찰은 남은 수량 전체, 부분 낙찰은 그보다 작은 수량이어야 한다. 유찰은 대기를 유지한다. 반환 추정은 반환 분류로 옮기고 실제 확인은 일부를 다시 대기로 돌릴 수 있다. 반환 확인은 lot 추적이며 **Farm 재입고/재고 복구를 수행하지 않는다**. 해당 API가 실물 재입고까지 처리한다고 가정하면 안 된다.

### DC-04 — High: 현재 상태로 결과 존재 여부를 대체하여 이력 삭제

실제 PostgreSQL 재현 순서:

1. 경매 전표 출하 완료 → lot 생성 → SOLD 결과 1건 등록.
2. 상태 변경 API로 lot를 `WAITING`으로 변경. 수량·attempt/result는 남는다.
3. 정산 재구성 전 전표를 취소.
4. 취소 성공. `auction_result_lines`, `auction_attempts`, `auction_lot_status_history`가 모두 0건이 되고 출고 수량이 복구됐다.

[AuctionShipmentLifecycleService.deleteDraftShipment](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentLifecycleService.java) 27행은 `currentStatus != WAITING`만 검사한다. `findShipmentIdsWithResults`도 이름과 달리 같은 상태 조건이다. [AuctionShipmentLot.changeStatus](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipmentLot.java)는 임의 enum 전환을 받아 과거 결과의 존재를 검사하지 않는다. shipment→lot→attempt→result/history cascade 삭제가 과거 사실을 제거한다.

[AuctionSalesSlipCancellationPolicy](../backend/src/main/java/com/greenhouse/backend/sales/application/AuctionSalesSlipCancellationPolicy.java)는 정산 연결은 별도 검사한다. 이미 정산에 반영됐다면 FK/정산 검사로 방어되므로 재현의 전제는 **결과는 있고 정산은 아직 없음**이다. 결과/정산 존재 검사와 삭제 사이에 lot root를 함께 잠그는 계약도 없다. 별도의 동시 결과 입력/취소 경쟁은 미재현이다.

개선: 상태가 아니라 실제 attempt/result/history 참조를 기준으로 취소 가능 여부를 판단하고, lot 잠금 후 재검증한다. 결과가 생긴 shipment는 물리 제거 대상에서 제외한다. 수동 상태 변경의 허용 전이와 결과 수량 관계도 함께 정리해야 한다.

### DC-05 — High: row lock·UNIQUE가 재전송 중복을 막지 못함

- **부분 낙찰:** 100개 lot에 `attemptNo=null`, 낙찰 10개 동일 요청을 두 번 보내 PostgreSQL에서 sold=20, attempt=2가 됐다. 자동 차수 `max+1`은 새 identity를 만든다. 기존 UNIQUE가 충돌하지 않는다.
- **부분 반환:** 유찰 대기 100개에서 반환 10개 동일 요청을 두 번 실행한 Java probe는 returned=20, waiting=80이었다. 반환 request에는 receipt/key가 없다.
- 명시 차수의 같은 결과는 중복 오류로 거절하며 기존 응답을 반환하는 replay 계약은 아니다. 전량 SOLD/반환은 두 번째 상태·대기량 검사에서 거절될 수 있지만 부분 처리는 다시 누적된다.

개선: 자동 차수 결과/반환의 요청 identity와 fingerprint를 안정적으로 보관하고, 동일 요청은 기존 결과를 반환한다. 실제 두 번째 경매/두 번째 반환과 네트워크 재시도를 구별해야 한다. row lock은 실행을 직렬화할 뿐 그 구별을 제공하지 않는다.

### DC-07 — Medium: 수량 변경 사실과 상태 이력의 불일치

`confirmReturn/adjustQuantities → changeStatus`에서 next status가 현재와 같으면 [changeStatus](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipmentLot.java) 341행이 바로 반환한다. Java probe의 두 번째 부분 반환 및 동일 REAUCTION_WAITING 상태의 수량 보정에서 수량은 바뀌지만 새 history가 없었다. 이 history에는 수량 before/after snapshot 자체도 없다.

`adjustQuantities`는 sold+waiting+returned=shipped만 확인하고 기존 낙찰 결과·정산/입금 연결은 확인하지 않는다. 따라서 lot의 sold 수량은 보정되지만 정산은 변경되지 않은 결과 행의 수량/금액을 계속 사용한다. 현재 값과 과거 낙찰 원장을 의도적으로 구분할 수는 있으나, 그 차이의 보정 근거·정산 영향 판정 계약이 없다. 실제 정산 금액을 잘못 변경하는 경로로 단정하지 않는다.

개선: 상태가 유지되는 반환/수량 보정도 before/after·사유·actor·시점을 남기고, 과거 결과/정산과 불일치가 생길 때의 정책을 명시한다. 이미 입금된 사실을 단순 lot 수정으로 재계산해 덮어쓰는 방향은 피한다.

## 8. Settlement/payment의 end-to-end 추적

### 8.1 경매 정산 재구성

`AuctionSettlementController/Initializer → AuctionSettlementService → partner lock → AuctionDataReader 결과 조회 → Settlement/Line synchronize → 예상 입금일 → 저장`.

- [AuctionSettlementService](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java)는 경매장+경매일로 모으고 양수 금액 결과만 연결한다. 동일 경매장 root lock이 재구성/입금의 공통 직렬화점이다.
- startup `rebuildExistingResults`는 후보 결과를 일괄 읽고 partner lock 이후 이미 연결된 ID를 다시 제외한다. 기존 결과 line UNIQUE와 `(auctionHouseId, auctionDate)` UNIQUE가 중복 저장을 방어한다.
- [AuctionSettlement.synchronizeLines](../backend/src/main/java/com/greenhouse/backend/settlement/domain/AuctionSettlement.java)는 이미 연결된 결과 ID의 수량/단가/금액 snapshot을 새 원본 값으로 덮어쓰지 않는다. paidAmount를 보존하고 합계/잔액을 갱신한다.
- 결과 입력과 정산 재구성은 별도 유스케이스/transaction이다. 새 낙찰 결과가 재구성 전에 정산에 없는 것은 이 모델의 지연 반영이다. 요청 전체에서 Auction와 Settlement가 항상 동시에 commit된다는 보장은 없다.
- 결과 수정/삭제가 없다는 전제 아래 snapshot 보존은 적절하다. synchronize는 후보에서 사라진 line을 제거할 수 있고 잔액을 0으로 clamp하므로, 향후 결과 보정/삭제를 도입하면 paidAmount>재구성 총액 처리 정책이 필요하다. 현재 일반 API 결함으로 판정하지 않았다.

### 8.2 일반 판매·경매 정산 입금

| 대상 | 잠금과 처리 순서 | 동일 요청 |
| --- | --- | --- |
| DIRECT Slip | Slip root → partner root → 기존 payment 확인 → 신규 금액 검증/paid·remaining 변경 → RECEIVED/MANUAL_MATCH → partner balance → Audit | 완납 후에도 같은 금액/날짜 replay 가능. 취소/경매 유형은 대상 검증에서 거절 |
| AuctionSettlement | 경매장 ID 조회 → partner root → Settlement root → 기존 payment 확인 → 신규 금액 검증/paid·remaining 변경 → 두 이벤트 → balance activity → Audit | 동일 key의 결과 재반영 없음. 재구성과 같은 partner 직렬화점 사용 |

근거: [SalesPaymentService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java), [PaymentService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentService.java), [PaymentLedgerService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java), [PartnerBalanceService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerBalanceService.java).

Payment identity는 `MANUAL:<targetType>:<targetId>:<trimmed key>`이고 external_uid UNIQUE가 있다. replay 비교는 금액·입금일이며 worker/memo/입금수단까지 동일한 요청을 요구하지 않는다. 현행 문서와 일치한다. 부분입금·완납·초과입금 거절은 Entity에 있고 신규 입금에만 잔액 검사를 적용한다.

수동 입금에는 **대상 상태 + 원장 2개 이벤트 + 잔액 + 감사**의 원자성이 실제 PostgreSQL rollback/병렬 테스트로 확인된다. 수신 이벤트와 연결 이벤트는 같은 금액의 서로 다른 사실이며 현금이 두 번 들어왔다는 뜻이 아니다. 경매의 balance activity는 일반 판매 미수 합계를 덮어쓰지 않는다. 예치금/계좌 자동 매칭/입금 취소는 이번 구현 범위로 가정하지 않았다.

## 9. 잠금·멱등성·DB 제약의 교차 판정

### 9.1 잠금 순서

| 경로 | root 순서 | 판정 |
| --- | --- | --- |
| Work 진행/포트/취소 | 연결·형제 Inbound ID↑ → Work ID↑ → execution ID↑ → group ID↑ → 필요한 zone ID↑ | 형제 입고 선잠금·계획 재확인, 취소와 신규 Work 등록 경쟁 테스트 통과 |
| Work 일괄 구조 취소 | Work ID↑ → 영향 group ID↑ → 복구 zone ID↑ | 포트 제외. 보상 전 전체 chain/참조 검사 |
| Engine 생성 | Inbound(해당 시) → zone ID↑ | 동일 위치 생성 경쟁을 직렬화 |
| Engine 기존 상태 변경 | group ID↑ → 필요한 zone ID↑ | 공유 원본/예약·폐기·보정 경쟁 테스트 통과 |
| Sales 생성/수정/취소·입금 | 신규 DIRECT: partner→번호→group. 기존: Slip→partner→group; 수정의 old/new group는 별도 취득 | group 합집합 미정렬로 DC-06 |
| 경매 정산 재구성/입금 | partner ID↑ → Settlement | partner 공통 잠금으로 UNIQUE와 version을 보완 |
| Auction 결과/반환/보정 | lot root | 해당 lot의 write 직렬화. shipment 취소 경로와 공통 검증 잠금 부족 |
| 배치 수용 프로필 | 명시 root lock/version 없음 | 실제 동시 변경 검증 미실행 |

일반/경매 입금의 서로 다른 root 순서만으로 deadlock을 판정하지 않았다. 실제 유스케이스가 어느 잠금을 추가로 요구하는지까지 추적해야 한다. 또한 위 표는 명시된 application root 순서이며 FK/JPA가 취득하는 모든 내부 lock의 전수 목록은 아니다.

### 9.2 중복 요청의 범위

| 요청 | 같은 요청의 재실행 보장 |
| --- | --- |
| Mutation | 안정적인 source tuple + fingerprint + 잠금 전후 replay + UNIQUE. caller가 key를 올바르게 구분해야 함 |
| Work 즉시/구조/포트 기록 | claim UPSERT→receipt lock→fingerprint→결과 ID. 실패 시 claim도 rollback. 현재 상세 반환이며 최초 HTTP body 고정은 아님 |
| Work 대상/회차 효과 | operation/effectKey UNIQUE와 저장 command fingerprint |
| Work 보정 | correction receipt와 저장 event ID. 원본 취소 후에도 완료 요청 재조회 |
| Work 취소 | 단건은 key+정규화 사유, 일괄은 Engine command 지문으로 범위/생성 취소 대상 검증 |
| Inbound 생성/일반 Work 계획·기록/Sales 생성 | 안정적 HTTP request identity 없음. Engine/효과의 내부 멱등성과 구분 |
| Sales 상태 | 같은 완료 상태 요청은 no-op. 이미 취소된 전표는 먼저 거절하므로 취소 replay 계약은 동일하지 않음 |
| Auction 결과/반환 | 명시 차수 충돌은 거절. 자동 차수/부분 반환은 DC-05 |
| Settlement 재구성 | 자연키+결과 ID로 중복 연결 방어. 입금·기존 line snapshot 보존 |
| 수동 입금 | 대상별 key, 금액/날짜 비교, 대상/partner lock, 이벤트 UNIQUE |

### 9.3 DB 제약

- V14: Orchid quantity/reservation CHECK, Sales 상태 CHECK, 두 root version. `NOT VALID` 제약의 과거 행 검증은 미확인.
- V15/V16: Settlement/lot/balance version, auction attempt 자연키 UNIQUE.
- V17: 전표 날짜 카운터 PK·양수 CHECK; Slip 번호 UNIQUE는 V1에 존재.
- V21/V22: Mutation source/revision/relation 제약과 ACTIVE write fence/deferred entry 검사.
- V25/V26/V30: command receipt PK·fingerprint 조건, operation/effectKey UNIQUE, membership FK/UNIQUE.
- V28/V33/V34: 취소 요청 key UNIQUE, 보정 receipt/event·Mutation 연결, stock-count receipt와 완료 조건.
- V1의 scalar 식별자 FK는 Sales allocation→group, movement→group/Slip/Item, SettlementLine→result/lot, payment→partner/원본 event에 계속 적용된다.

**DB에서 보장하지 않는 주요 교차 불변식:** 예약=작성중 allocation 합계, item allocation 합계=item 수량, 금액 곱/합계·paid/remaining 동치, lot 합계·result/settlement와의 일치, 모든 snapshot=current state 동치. application 정책과 reconciliation에 의존하며 DC-01/DC-02/DC-07은 그 틈에서 발생한다.

## 10. snapshot과 audit/mutation/work/sales 원자성

| 사실 | snapshot/기록 시점 | 원자 범위와 한계 |
| --- | --- | --- |
| Orchid 상태 | 변경 직전 before, 적용 직후 after, revision 증가와 Entry 저장 | Engine과 caller transaction. DB가 snapshot 내용을 모두 대조하는 것은 아님 |
| Work 계획 대상 | resolve 결과를 잠금/활성·version 검증 후 저장 | 계획 이후 실제 실행 시 현재 재고를 다시 검증. 동시 변경 probe에서 계획 거절 확인 |
| 구조 변경 결과 | 원본 속성 상속은 차감 전, SOURCE/RESULT/명시 수량은 실행 회차 저장 | N:M 효과/원장/단일-source 호환 Lineage는 같은 transaction |
| Inbound 포트 대상 | 실제 실행 직전 최신 입고 metadata로 refresh | 입고 담당자·메모를 포트 작업 정보로 덮어쓰지 않음 |
| Sales allocation | CREATION=예약 증가 전; OUTBOUND=차감 전 | snapshots/예약·출고 Mutation/movement/경매 shipment가 같은 transaction. DC-01에서는 같은 transaction이 잘못된 업무 조율을 확정 |
| Settlement line | 결과 최초 연결 시 수량·단가·금액 복사 | 재구성이 기존 snapshot을 재조회 값으로 덮어쓰지 않음. 이름·출하일 등 현재 참조를 포함한 응답 전체가 과거 snapshot인 것은 아님 |
| 수동 입금 | 대상 before/after, 수신/연결 이벤트, 잔액, Audit | 실제 PostgreSQL rollback 확인 |
| Work 보정 | 독립 correction event의 before/after·사유·actor·시점 | receipt/event/Mutation/작업일 변경 원자 저장. 날짜-only Mutation 없음 |
| 일반 AuditEvent | changedFields가 있으면 caller transaction에서 저장 | 저장 실패 rollback. WorkEffect/Correction/Mutation이 AuditEvent를 대체하는 업무 사실인 경로도 있음 |

`SalesSlipCreationService`는 SALES_MANAGEMENT 생성 AuditEvent를 만들지 않고, `AuctionTrackingService`는 일반 AuditEvent 대신 attempt/status history를 사용한다. 일반 Orchid Audit snapshot은 memo/placementType/trayCount 등의 모든 필드를 포함하지 않는다. 그러므로 **현재 저장되는 기록의 원자성**과 **모든 변경의 actor·이유·전후 값 완전성**을 구분한다. DC-07은 실제 기록도 없는 수량 변경 사례다.

## 11. domain rule 중복과 문서 차이

| 규칙 | 분산/중복 상태 | 평가 |
| --- | --- | --- |
| Orchid 수량·예약 | Entity가 중심, command 정규화·HTTP validation·DB CHECK가 보완 | 계층별 방어는 적절. Sales key identity 오류는 Entity만으로 해결 불가 |
| 판매 가능 상태 | StatusPolicy/집계 vs searchSellable/reserve 실행 | 같은 정책을 실제 명령이 공유하지 않음: DC-03 |
| Work 활성 상태 | StatusPolicy와 Repository의 한국어 inactive 문자열 | 현재 의미는 대체로 일치하나 새 상태 추가 시 조회/명령 동시 변경 위험 |
| 판매 상태 전이 | SalesSlip 전이·초기 상태 검증, StatusService의 취소/완료 검사, action resolver | 기본 전이는 일치. 이미 취소된 동일 요청의 처리와 capability는 별도. aggregate+외부 이력 조건을 모두 Entity에 넣을 필요는 없음 |
| 구조 수량 | Work 계획 잔여량, Strategy 작업별 수량, Orchid 현재 가용량, 보정 수량 수지 | 서로 다른 불변식이므로 일괄 제거할 중복은 아님. int 집계 범위와 장기 수량 계약은 별도 경계 검토 필요 |
| 반환 가능 여부 | Service와 Entity가 `requireReturnConfirmable`을 반복 호출 | 기능적 drift는 없지만 중복. 더 큰 문제는 요청 identity와 수량 사실 기록 |
| 경매 취소 가능 | 상태 predicate를 결과 존재로 사용하는 lifecycle/capability | 양쪽이 같은 잘못된 근거를 공유: DC-04 |
| 입금 가능·잔액 | Slip/Settlement Entity, ledger replay, capability | 신규 금액 검사와 replay를 구분하여 완납 후 재시도 보호 |

문서 수정이 필요한 차이:

- `docs/04-architecture.md`의 Farm 예약 API Legacy/Engine 분기 설명은 현재 Engine 직접 호출·Legacy writer 제거 상태와 다르다. 현행 코드와 판매 기능 문서를 우선했다.
- ADR-001은 일반 상세 수정으로 수량/lifecycle/위치를 임의 변경하지 않는 방향을 제시한다. 실제 `OrchidGroupCommandService.update → Engine.updateDetails`는 양수 quantity·일반 status·범위 변경을 계속 허용하고, Work 보정의 후속 참조/실사 gate를 거치지 않는다. 이것은 Mutation 우회는 아니지만 **명령별 변경 권한의 정책 차이**다. 일반 수정과 실사/보정의 허용 범위를 합의하고 문서/코드를 맞춰야 한다.
- DOMAIN_RULES의 전체 Work 완료 대상 조건은 CANCELED 대상의 실제 terminal 예외를 설명할 필요가 있다.
- 이번 평가에서 이 정책 문서·구현·OpenAPI는 수정하지 않았다. 후속 수정 때 Controller/DTO/테스트 → OpenAPI 생성 → 필요 시 프론트 타입 생성 순서를 적용한다.

## 12. 검증 결과와 남은 범위

### 실행 결과

| 검증 | 결과 |
| --- | --- |
| `backend: ./gradlew test --rerun-tasks` | 113개 테스트 클래스, 525 tests, failure/error/skipped=0. 캐시 결과 대신 실제 재실행 |
| `frontend: npm run check` | 성공. 프론트 코드 변경 없음 |
| PostgreSQL 기존 집중 회귀 `workE2eTest` | 11개 클래스, 91 tests, failure/error/skipped=0. PostgreSQL 18 Testcontainers, 실제 Flyway/ACTIVE fence 포함 |
| 임시 PostgreSQL 감사 probe | 8 tests, failure/error/skipped=0. 결함 재현 7개 조건과 Work snapshot 충돌 방어 1개 조건 검사; 상세는 아래 |
| 임시 Java 도메인 probe | 품목 곱/합계 overflow, 불가 상태 reserve, 부분 결과/반환 재요청 누적, 같은 상태 수량 보정의 history 누락 확인 |

기존 PostgreSQL 회귀 클래스:

`OrchidGroupMutationPostgresE2ETest`, `SalesInventoryPostgresE2ETest`, `PartnerSettlementPostgresE2ETest`, `WorkStructureChangeE2ETest`, `WorkIdempotencyPostgresE2ETest`, `WorkUndoInboundPostgresE2ETest`, `WorkBatchCancellationPostgresE2ETest`, `WorkCorrectionAuditPostgresE2ETest`, `QuantityFeatureHoldPostgresE2ETest`, `OrchidStockCountPostgresE2ETest`, `OrchidGroupStateChainMigrationPostgresE2ETest`.

기존 집중 회귀는 `./gradlew workE2eTest`에 위 클래스별 `--tests '*클래스명'` 필터를 적용했다. 추가 probe의 최종 실행은 `./gradlew -I /tmp/green-house-domain-audit-probe.init.gradle workE2eTest --tests '*AuditDomainConsistencyProbePostgresTest'`였다.

대표적으로 감사 실패 rollback, 마지막 batch 실패 시 receipt/앞 결과 rollback, 공유 원본 transform, 예약 vs 폐기/보정, 같은 입고 중복 생성, 같은 위치 생성, 형제 입고 lock 순서, 취소 vs 신규 Work 등록, 입금 replay/동시 완납/재구성/초기화, 출고 취소의 복구 위치 충돌, ACTIVE fence/context rollback을 확인한다. 기존 테스트 통과가 DC-01~07의 부재를 입증하지는 않는다.

감사 probe는 저장소 코드를 바꾸지 않고 `/tmp` Java source와 Gradle init script로 기존 PostgreSQL 테스트 기반을 확장했다. **결함 조건을 assert하는 probe의 성공은 제품 동작의 올바름을 의미하지 않는다.** 재현 입력과 기대 관찰은 다음과 같다.

| probe 조건 | 확인할 관찰 |
| --- | --- |
| 작성중 10개×1,000원 전표, spec만 변경 | 수정 성공, allocation=10, reserve=0, SALES_RESERVE movement=2 |
| 동일 금액 전표의 allocation만 다른 그룹으로 변경 | Mutation source key 재사용 Conflict, 변경 rollback |
| 2개×1,500,000,000원 직접 판매 | 음수 total 저장, reserve=2 |
| 100개 그룹을 병해충으로 변경 후 판매 10개 생성 | reserve=10 저장 |
| 100개 경매 lot에 자동 차수 부분 낙찰 10개 동일 요청 2회 | sold=20, attempt=2 |
| 경매 SOLD 결과 → 수동 WAITING → 미정산 전표 취소 | result/attempt/history 삭제, 수량 복구 |
| 다른 거래처 두 전표의 G2↔G1 배분 교차 변경, release 뒤 barrier | PostgreSQL `40P01`; 다른 요청은 version 기반 key 충돌도 관찰 |
| Work resolve(100개) 뒤 다른 transaction에서 60개로 변경 | `WORK_TARGET_CHANGED`, target 저장 0건: 보호 동작 |

기존 테스트/임시 probe 모두 격리된 Testcontainers DB를 사용했다. 운영 DB·외부 시스템에는 접속하지 않았다. 재현용 source는 `/tmp`에만 두고 이 평가 문서만 저장소에 추가했다.

미실행/미확인: 전체 `workE2eTest` 전수 실행, 실제 운영 데이터의 constraint VALIDATED/coverage 상태, DC-04의 동시 취소·결과 입력, 일반 Orchid batch/다품종 기록의 실제 deadlock, 프로필 동시 편집, 운영 reconciliation/부하 측정. 이번 결과는 코드 수정·마이그레이션 적용 완료가 아니다.

우선순위: **DC-01 예약 멱등 identity → DC-04 경매 이력 보존 → DC-02 금액·DC-03 판매 가능 정책 → DC-05 결과/반환 재전송 → DC-06 잠금 합집합 → DC-07 보정 이력**. 수정마다 현재 성공한 rollback/동시성 회귀를 유지하고 해당 재현을 저장소의 회귀 테스트로 옮기는 것이 필요하다.
