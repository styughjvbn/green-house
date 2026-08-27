# 난 묶음 Mutation Engine 전환 코드 수명 관리

이 문서는 Mutation Engine 전환 과정에서 코드와 데이터의 수명을 판단하는 기준 원장이다.
전환 절차는 `../07-deployment.md`, 설계 결정은
`../adr/ADR-001-orchid-group-mutation-engine.md`를 따른다.

## 분류

| 분류 | 의미 | 완료 조건 |
|---|---|---|
| `TARGET` | 전환 후에도 유지할 최종 구조 | Engine 단일 writer에서 계속 사용 |
| `TRANSITION_ONLY` | 전환 준비·검증·실행에만 필요한 코드 | 운영 `ACTIVE` 안정화와 제거 gate 승인 후 삭제 |
| `LEGACY_RETIRE` | 기존 동작을 유지하기 위한 직접 writer 또는 호환 분기 | Engine 단일 경로가 확인되면 삭제 |
| `DATA_RETAIN` | 실행 코드를 제거해도 보존할 스키마와 감사 데이터 | 보존 정책에 따라 유지하며 코드 제거와 함께 삭제하지 않음 |

`OrchidGroup`의 상태 변경 메서드는 Engine이 aggregate invariant를 적용할 때도 사용하므로
그 자체를 Legacy로 분류하지 않는다. Engine 밖에서 해당 메서드를 직접 호출하는 경로가
`LEGACY_RETIRE`다.

## TARGET

다음 구조는 전환 후에도 유지한다.

| 범위 | 역할 |
|---|---|
| `OrchidGroupMutationEngine`과 typed mutation command/result | 난 묶음 상태 변경의 단일 application 경계 |
| `OrchidGroupMutation`, `OrchidGroupMutationEntry`, 상태 snapshot과 repository | 변경 원인, revision과 전후 상태 보존 |
| command fingerprint, replay resolver, source identity | 요청 멱등성과 재실행 결과 복원 |
| Work·Sales Mutation 연결 | 업무 사실과 난 묶음 상태 변경의 상관관계 보존 |
| ledger coverage와 PostgreSQL write fence | 적용 범위 증명과 Engine 밖 직접 쓰기 차단 |
| ledger reconciliation service와 모듈별 rehearsal inspector | 전환 후에도 revision·snapshot·업무 연결 정합성 검사 |
| `HISTORICAL` Mutation/Entry | cutover 이전 업무 이력의 영구 조회 자료 |

상시 reconciliation을 어떤 scheduler나 운영 명령으로 호출할지는 별도 운영 정책이지만,
대사 로직 자체는 전환 코드로 보지 않는다.

## TRANSITION_ONLY

| 범위 | 역할 | 제거 gate | 데이터 처리 |
|---|---|---|---|
| `OrchidGroupLedgerWriterMode`, writer properties/configuration, routing policy와 startup guard | `LEGACY\|ENGINE` 선택, 시작된 `PREPARING`의 Legacy 재기동과 구버전 차단 | 모든 환경을 Engine으로 고정하고 fallback 금지 확인 | coverage는 보존 |
| `OrchidGroupLedgerCutover*`, `OrchidGroupLedgerPreparationService` | baseline 생성과 `ACTIVE` 전환 | 운영 cutover 성공 및 재수행 불필요 승인 | baseline과 coverage는 보존 |
| `OrchidGroupHistoryMigration*`, Historical 입력·연결 서비스 | cutover 이전 이력 계획·적재·검증 | 최종 catch-up과 manifest 검증 완료 | Historical Entry와 run 결과는 보존 |
| `migration.*.orchid` operator runtime | migration run 잠금·상태 전이 | historical migration 종료와 감사 보존 확인 | run 테이블과 행은 보존 |
| Work·Sales historical reader/link adapter | 소유 모듈의 과거 사실을 migrator에 제공 | historical migration 종료 | 원본 Work·Sales 사실은 보존 |

전환 전용 클래스의 Javadoc에는 다음 표식을 사용한다.

```text
ORCHID-CUTOVER: TRANSITION_ONLY
Removal gate: <제거 조건>
Inventory: docs/features/orchid-group-mutation-transition.md
```

## LEGACY_RETIRE

다음 클래스 전체가 Legacy라는 뜻은 아니다. 해당 클래스 안의 routing flag와 Engine을
사용하지 않을 때 실행되는 직접 상태 변경 분기만 제거 대상이다.

| 혼합 writer | 제거 대상 |
|---|---|
| `InboundRecordService`, `InboundPottingService` | 난 묶음 직접 생성·수정·repository 저장 분기 |
| `OrchidGroupCommandService` | `createEntity`를 포함한 직접 생성·수정·이동·취소 분기 |
| `DiscardWorkHandler`, `MovementWorkHandler` | Engine 밖 직접 폐기·이동 분기 |
| `BatchStructureTransformationExecutor`, `CorrectionWorkHandler`, `MergeWorkHandler` | 직접 구조 변경·보정 분기 |
| `MultiCreateWorkHandler`, `MultiCreateWorkOperationService` | 직접 다중 생성·생성 취소 분기 |
| `VarietyService` | 품종 변경을 난 묶음에 직접 전파하는 분기 |
| `SalesSlipInventoryService` | 예약·해제·출고·복구를 난 묶음에 직접 적용하는 분기 |

혼합 writer의 클래스 Javadoc에는 `ORCHID-CUTOVER: LEGACY_RETIRE` 표식을 두고,
정확한 호출자 집합은 `OrchidGroupWriterArchitectureTest`가 고정한다. 신규 writer를
inventory에 추가해 우회하지 않고 typed Engine command로 편입한다.

추가 정리 후보인 Work 결과 snapshot과 단일 원본 Lineage 중복 write는 실제 조회
소비자를 ledger로 전환한 뒤 별도 항목으로 등록한다. 소비자 전환 전에 삭제하지 않는다.

## DATA_RETAIN

다음 대상은 runtime 제거와 구분한다.

- 적용된 Flyway V21~V27 파일은 삭제하거나 수정하지 않는다. 사용하지 않는 비교 테이블은
  V26을 수정하지 않고 V27에서 제거한다.
- `orchid_group_mutations`, `orchid_group_mutation_entries`와 historical Entry를 유지한다.
- migration run 결과는 실행 코드가 제거되어도 감사 근거로 유지한다.
- Work·Sales·Lineage 원본 사실은 별도 소비 전환과 보존 정책 없이 삭제하지 않는다.

## 제거 gate

`TRANSITION_ONLY`와 `LEGACY_RETIRE` 코드는 다음 조건을 모두 만족한 뒤 별도 릴리스에서
제거한다.

1. 운영 coverage가 `ACTIVE`이고 모든 backend 인스턴스가 승인된 Engine writer
   version으로 고정되어 있다.
2. 운영 smoke test와 정기 reconciliation이 합의한 안정화 기간 동안 통과한다.
3. 복원 운영 DB에서 전체 ENGINE 시나리오와 `ACTIVE` 전환 rehearsal이 통과한다.
4. 진행 중 Work와 전환 전 Sales 예약의 후속 실행 회귀가 통과한다.
5. architecture inventory에서 미확인 직접 writer가 없다.
6. rollback이 Legacy flag 복귀가 아니라 백업 복구 또는 검증된 전환 재개 절차로
   확정되어 있다.

완전 전환 완료 조건은 architecture test의 `TRANSITION_ONLY` writer inventory와
`LEGACY_RETIRE` writer inventory가 모두 비고, writer mode 설정 없이 Engine만
호출되는 것이다.

## 변경 규칙

- 전환 관련 코드를 추가·삭제하면 이 문서와 architecture inventory를 같은 변경에서
  갱신한다.
- 데이터 보존 대상과 runtime 코드의 수명을 같은 것으로 취급하지 않는다.
- 제거 PR에서는 관련 테스트 제거 이유와 대체 검증을 함께 기록한다.
- 표식 없는 신규 난 묶음 writer는 허용하지 않는다.
