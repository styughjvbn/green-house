# Mutation Engine 및 호출부 리팩터링 상세

- 작성일: 2026-09-05
- 검토 기준: `feature/orchid-group-mutation-engine`, `3aa8fc54`
- 상태: 코드·문서 검토 완료, 구현 전 계획
- 중심 범위: `farm.orchid.mutation`, 이를 호출하는 Farm·Inbound·Work·Sales
- 문서 성격: 현재 구현에 대한 진단과 제안. 승인된 정책은 기존 ADR과 기능 문서를 따른다.

이 문서는 [전체 백엔드 리팩터링 계획](backend-refactoring-plan.md)의 하위 상세다.
아래 PR-01~09는 이 상세 문서 안의 작업 번호이며, 전체 계획의 B-01~17과는 구분한다.
전체 실행 순서와 범위는 상위 계획을 따른다. 테스트·포맷·Work·Sales 작업을 두 번 수행하지 않는다.

## 1. 현재 상태와 판단

현재 교체 작업의 중심은 **난 묶음의 물리 상태 변경을 Farm Mutation Engine으로 모으는 것**이다.
Work의 작업 진행, Sales의 예약·출고 업무, Inbound의 입고 lifecycle까지 엔진으로 옮기는 설계는 아니다.
`orchid_groups`를 현재 상태의 기준으로 유지하고, 연속 revision과 전체 스냅샷을 ledger에 남긴다.

방향 자체를 다시 설계할 필요는 없다. 가독성을 떨어뜨리는 주된 원인은 다음 세 가지다.

1. 엔진에 조회·잠금·검증·상태 적용·ledger 저장이 함께 있고 공통 실행 절차가 반복된다.
2. 전환용 Legacy 분기가 업무 처리 및 결과 조립과 섞여 있다.
3. 엔진 바깥의 Entity 전달, Work JSON 해석, 멱등성 처리는 새 엔진만큼 정리되지 않았다.

포맷 통일만으로 해결되는 범위는 작다. **검증 기반 확보 → 책임 분리 → 호출 계약 정리 → 조건부 Legacy 제거** 순서가 적절하다.

### 구현 현황

| 항목 | 확인 내용 |
|---|---|
| 전체 구조 | Java 21·Spring Boot 모듈러 모놀리스, 13개 업무·지원 모듈 |
| 코드 규모 | 운영 Java 562파일, 30,173줄. Farm 231파일, Work 128파일, Sales 53파일 |
| 핵심 엔진 | `OrchidGroupMutationEngine` 894줄, 주입 의존성 12개 |
| 전환 도구 | state-chain importer 537줄, reconciliation service 489줄 |
| writer 전환 | 12개 클래스에 `routesToEngine()` 호출 34곳. 기본 설정은 `LEGACY` |
| 보존 장치 | typed command, Mutation fingerprint/replay, 연속 revision, PostgreSQL fence, writer inventory 테스트 |
| 과거 이력 이관 | ADR-002에 2026-08-28 복원 DB rehearsal 결과 기록. 운영 전환 완료의 증거와는 구분 |
| Work | 작업·대상·실행·효과·보정 책임이 이미 분리되어 있으며 capability와 이력 조회 구현 |
| Sales | 전표 생성 시 예약, 출고·출하 완료 시 실수량 차감, 생성·출고 스냅샷과 Mutation 연결 구현 |

파일 수와 줄 수는 검토 범위를 설명하는 지표다. 파일 길이만으로 분리 여부를 결정하지 않는다.
운영 DB와 배포 환경에는 접속하지 않았으므로 실제 `ACTIVE` 여부와 안정화 기간은 미확인이다.

### 확인한 기준 문서

- [개요](../01-overview.md), [도메인 모델](../02-domain-model.md), [기능 요약](../03-feature-summary.md), [아키텍처](../04-architecture.md)
- [API 가이드](../06-api-guide.md), [API 색인](../api/API_INDEX.md), [도메인 규칙](../api/DOMAIN_RULES.md), [API 구현 차이](../api/API_GAP_ANALYSIS.md)
- [작업·난 그룹](work-operation-and-orchid-collection.md), [판매·경매·정산](sales-auction-settlement.md), [인증](authentication.md), [데모 운영](demo-operations.md)
- [배포](../07-deployment.md), [로드맵](../08-roadmap.md), [ADR-001](../adr/ADR-001-orchid-group-mutation-engine.md), [ADR-002](../adr/ADR-002-orchid-group-historical-migration.md), [전환 코드 수명](orchid-group-mutation-transition.md)

코드는 위 문서에 관련된 핵심 실행 경로를 집중 검토했다. 모든 Controller·Repository를 전수 감사한 결과는 아니다.
`docs/archive/`는 현재 설계의 기준으로 사용하지 않았다.

## 2. 실제 코드에서 확인한 문제

우선순위의 의미: P0는 큰 변경 전 검증·정합성 확인, P1은 핵심 구조 개선, P2는 후속 정리다.
아래의 '확인'은 코드 구조를 확인했다는 뜻이며, 별도로 표시한 위험은 장애를 재현했다는 뜻이 아니다.
소스 링크의 행 번호는 위 기준 커밋을 따른다.

### F1. 엔진의 공통 절차가 반복되고 변경 순서가 메서드마다 다름 — P1

근거: [MutationEngine](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java)의 `create`, `updateDetails`, `move`, `discard`, `correct`, `applyQuantityMutation`.

- fingerprint 계산, 잠금 전 replay, 잠금, 잠금 후 replay가 반복된다.
- before/after snapshot, revision 증가, Mutation/Entry/Relation 조립이 여러 경로에 있다.
- 생성·입고 배치·구조 변경의 그룹 생성과 배치 조회 책임도 함께 있다.
- `applyQuantityMutation`은 9개 인자를 받고 `Object`, nullable 관계 인자와 `BiConsumer`를 조합한다. 호출부를 봐도 허용 조합이 명확하지 않다.

개선: 엔진의 public 업무 메서드는 유지하고 잠금 로딩, ledger 기록, 관련 Mutation 검증을 응집된 내부 책임으로 분리한다.
잠금 후 두 번째 replay는 동시 요청을 위한 것이므로 단순 중복으로 삭제하지 않는다.

### F2. 배치 생성의 조회·저장 순서가 JPA 자동 flush에 의존 — P0 확인, P1 개선

근거: [MutationEngine](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java) `createMany:97`, `createFromInbound:138`, `createGroup:825`, `saveMutation:742`와 [OrchidPlacementPolicy](../../backend/src/main/java/com/greenhouse/backend/farm/application/structure/OrchidPlacementPolicy.java).

- 결과를 순회하며 기존 배치를 조회하고 그룹을 `save`한다. Mutation 저장과 fence context 설정은 루프 뒤의 `recordCreated`에서 수행한다.
- 다음 결과의 배치 검증이 이전 결과 INSERT를 자동 flush하면 `ACTIVE` fence context 설정보다 실제 쓰기가 앞설 가능성이 있다.
- `transform`은 상태 변경 전에 Mutation context를 설정하므로 생성 경로와 실행 순서가 다르다.
- 배치 검사 또한 결과마다 같은 구역을 재조회한다. 입력 건수에 따른 SELECT 증가가 코드에서 확인된다.

**자동 flush에 의한 실패는 이번 검토에서 재현하지 않은 위험이다.** 기존 ACTIVE 포트 라우팅 테스트는 결과 1개를 사용한다.
ACTIVE에서 결과 2개 이상을 같은 구역/서로 다른 구역에 만드는 테스트를 먼저 추가해 판단한다.
실패하면 `fix`로 해결하고, 읽기 최적화는 별도 변경으로 진행한다.

### F3. 전환 분기가 업무 데이터 변환까지 복제함 — P1

근거: [BatchStructureTransformationExecutor](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java) `execute`, `mutationCommand`, `executeWithEngine`와 [SalesSlipInventoryService](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java).

- 구조 변경 결과의 속성 상속, purpose→상태 변환, 결과 details 조립이 Legacy/Engine 양쪽에 있다.
- Sales의 예약·해제·출고·복구마다 command 생성, 모드 확인, 직접 변경, movement 연결 패턴이 반복된다.
- 한 메서드에서 모드를 여러 번 확인하고 `command == null`, `mutation == null`을 Legacy 경로 표현에 사용한다.

개선: 공통 업무 입력·결과 모델을 먼저 만들고 writer 선택을 한 번의 명시적 분기로 모은다.
Legacy 실행기는 전환 수명 표식을 유지한다. 12개 서비스 각각에 interface와 구현체 두 개를 기계적으로 만들지는 않는다.

2026-09-07, 백엔드 14차에서 Sales 부분을 이식했다. Farm 예약 API가 기존 typed command로 Engine/Legacy를 선택하고 수량을 변경하며, Sales의 반복 모드 검사·직접 Entity 변경을 제거했다. 배분별 movement 생성과 Mutation 연결은 하나의 경로로 합쳤다. 구조 변경 결과의 양쪽 변환 중복은 후속 범위로 남는다.

### F4. application Reader를 거쳐도 타 모듈 Entity가 노출됨 — P1

근거: [OrchidGroupReader](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java), [SalesSlipAllocationBatch](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationBatch.java), [SalesSlipItemAllocation](../../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipItemAllocation.java), [SalesInventoryMovement](../../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesInventoryMovement.java).

- Reader의 반환값이 `OrchidGroup`이며, Sales가 그 Entity와 JPA 연관을 사용한다.
- Engine 모드에서도 Sales가 Entity로 allocation과 스냅샷을 조립하고 Farm 잠금 조회를 호출한다.
- [WorkEffectHandler](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectHandler.java)도 Farm 구현체에 Work Entity를 전달하는 계약이다.
- [ModularArchitectureTests](../../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java)는 타 모듈 Repository import와 의존 방향을 검사하지만 이러한 Entity 노출까지 금지하지 않는다.

개선: 변경 우선순위가 높은 Sales–Farm 계약부터 식별자와 immutable application 값으로 바꾼다.
단순히 클래스 이름을 Reader에서 Gateway로 바꾸는 것으로 완료 처리하지 않는다.
기존 Legacy 직접 writer와 JPA 연관 제거는 단계가 다르므로 아래 PR-09에서 나눠 진행한다.

2026-09-07, 백엔드 14차에서 Sales–Farm의 Entity 노출과 JPA 연관을 제거했다. 기존 DB 외래키를 유지하는 ID와 Farm application 상태 값을 사용하고, 이전 Entity 잠금 반환 API는 삭제했다. Legacy 쓰기는 Farm 내부 예약 API로 이관했으며 제거 gate는 유지한다. `ModuleBoundaryInventoryTest`의 정확한 예외 목록에서 해당 의존 21쌍을 삭제했다. Work handler의 Entity 전달은 다음 경계 작업으로 남는다.

### F5. typed command 내부에 다시 비정형 타입과 긴 인자 조합이 있음 — P1

근거: [OrchidGroupMutationCommandFingerprint](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationCommandFingerprint.java) `calculate(Object):19`, [OrchidGroupMutationDetails](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationDetails.java), 엔진의 수량 처리 helper.

- command가 추가되어도 fingerprint 지원 누락은 컴파일 단계에서 드러나지 않는다.
- 여러 곳에서 긴 positional constructor를 통해 같은 상태 정보를 옮긴다.
- 단순 생성자 주입과 수동 생성자, 한 줄 override, import 순서가 혼재한다. `RepotStrategy`와 `BatchStructureTransformationExecutor`가 예다.

개선: command fingerprint 진입점은 명시적 overload를 우선한다. 공통 속성을 여러 소비자가 필요로 할 때만 닫힌 command 타입을 도입한다.
기존 semantic payload의 JSON 필드명·정규화·순서·해시값은 유지한다. serializer 변경은 보일러플레이트 정리로 취급하지 않는다.

### F6. Work 효과의 Map 계약 때문에 읽기와 쓰기가 강하게 결합됨 — P1

근거: [WorkExecutionResult](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkExecutionResult.java), [WorkOperationDetailService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailService.java) 408줄.

- 실행 결과는 `Map<String, Object>`이고 읽기는 `sources`, `results`, `adjustments` 등 문자열 키와 다중 fallback으로 형태를 추론한다.
- 상세 서비스에 DB 조회, 구형 JSON 호환, 수치 파싱, 화면용 label/문자열 변환이 함께 있다.
- 구조 변경 handler의 결과 키를 바꾸면 상세·계보·보정 읽기까지 함께 추적해야 한다.

개선: Work 내부의 효과 종류별 결과 값과 전용 codec을 둔다. 기존 JSON 저장 형태와 API 응답을 유지하면서 쓰기·읽기 경계를 좁힌다.
기존 JSON을 일괄 재작성하거나 과거 사실을 현재 Entity 값으로 채우지 않는다.
화면 label은 우선 응답 assembler로 격리하며, 프론트 이전은 별도 API 변경으로 다룬다.

### F7. 작업 보정 상세에서 명확한 반복 조회가 남아 있음 — P1

근거: [WorkOperationDetailService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailService.java) `corrections:298`.

보정 목록의 `.map()` 내부에서 매번 `findByWorkOperationIdAndEffectKey(..., "OPERATION")`를 호출한다.
보정 건수에 비례해 효과 조회가 늘어난다. Repository에는 이미 `findByWorkOperationIdInAndEffectKey`가 있다.

개선: 보정 작업 ID를 모아 기존 일괄 API로 읽고 ID별 map으로 조립한다.
correction operation의 lazy loading도 함께 계측한다. 단순히 effect 조회 하나만 줄여 완료 처리하지 않는다.

### F8. importer와 상시 대사의 수명·검증·조회 책임이 한 패키지에 혼재 — P1/P2

근거: [StateChainMigrationService](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupStateChainMigrationService.java), [LedgerReconciliationService](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerReconciliationService.java).

- importer에 입력 검증, source 해석, chain 검증, replay 검증, 적재, Work/Lineage 연결과 coverage 관리가 함께 있다.
- importer의 `validateChains`와 대사의 `inspectLedger`가 연속 revision·snapshot·terminal 조건을 각각 해석한다.
- reconciliation은 그룹을 500개씩 조회하지만 최종적으로 전체 그룹과 ledger를 메모리에 모은다. 무제한 API 문제와는 구분하되, 누적 이력 증가 시 측정이 필요하다.

개선: 순수 chain 검증과 데이터 로딩·연결을 분리한다. importer는 실패 시 중단, reconciliation은 issue 수집이라는 차이를 유지한다.
전환 도구는 최소한의 분리만 하고, 유지할 reconciliation을 중심으로 정리한다. 별도 migration 모듈은 만들지 않는다.

### F9. Work 멱등성의 목표 계약과 현재 구현 사이에 간극 — P0 판단, 별도 계약 보강

근거: [ADR-001의 요청·효과 멱등성](../adr/ADR-001-orchid-group-mutation-engine.md), [StructureChangeExecutionService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeExecutionService.java) `execute`, [InboundPottingOperationService](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java) `validatedOperationId`, [WorkAppliedEffect](../../backend/src/main/java/com/greenhouse/backend/work/domain/effect/WorkAppliedEffect.java), [WorkAppliedEffectRepository](../../backend/src/main/java/com/greenhouse/backend/work/repository/WorkAppliedEffectRepository.java).

- 구조 변경 실행은 진행 상태를 먼저 검증하고 기존 effect key가 있으면 payload 비교 없이 반환한다.
- 포트 실행은 기존 command 내용까지 비교한다. 재시도 규칙이 유스케이스별로 다르다.
- 현재 DB UNIQUE는 `(operation, effect_key, effect_kind)`, 단건 조회는 `(operation, effect_key)` 기준이다.
- ADR의 구조화된 effect identity·fingerprint·요청 receipt는 목표이며 현재 코드에 완전히 구현되어 있지 않다.

단순 중복 제거 과정에서 이 동작을 조용히 바꾸면 안 된다. 같은 키·다른 payload, 완료 후 재시도, 같은 키·다른 kind를 재현 테스트로 고정한다.
정책·DB 제약·오류 코드 보강은 `refactor`와 분리한 계약 변경 작업으로 다룬다.

### F10. 테스트가 존재하는 범위와 자동으로 지켜지는 범위가 다름 — P0

근거: [Gradle 설정](../../backend/build.gradle.kts), [CI](../../.github/workflows/verify.yml), [WorkE2ETestBase](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkE2ETestBase.java).

- 기본 `test`는 `work-e2e`, `work-benchmark` 태그를 제외한다. CI의 backend job은 기본 `test`만 호출한다.
- 7개 기존 통합 테스트 클래스, 총 49건이 seed fixture 문제로 `@Disabled` 상태다.
- PostgreSQL E2E는 Docker가 없으면 skip할 수 있다. 작업 성공 여부와 실제 테스트 실행 건수를 함께 확인해야 한다.
- 기존 `CoreQueryRegressionTest`와 Work benchmark는 좋은 기반이나, 다중 Mutation 생성과 여러 보정 상세를 직접 보호하지는 않는다.

개선: 필요한 fixture 복구와 PostgreSQL CI 실행을 리팩터링 선행 작업으로 둔다.
테스트 파일명을 전부 바꾸거나 기존 테스트를 새로 작성하는 작업은 우선순위가 낮다.

## 3. 지켜야 할 동작과 책임

| 경계 | 유지할 규칙 |
|---|---|
| 트랜잭션 | 최상위 업무 서비스가 소유. Engine의 `MANDATORY` 유지. 상태·revision·ledger·Work/Sales 연결·감사는 같은 트랜잭션 |
| 물리 상태 | 수량·예약·상태 전이는 Entity/Domain Policy, 업무 lifecycle은 Work·Sales·Inbound에 유지 |
| 잠금 | 상위 업무 행부터 실제 난 묶음·목적 구역까지 전체 호출 경로의 순서 확인. 각 일괄 대상은 결정적 ID 순서 |
| 멱등성 | 잠금 전/후 replay, source identity, 기존 fingerprint, 결과 순서와 correlation 연결 보존 |
| 배치 | 논리 구역 기준, 기존 점유·이번 실행의 결과 간 중복·비워진 뒤쪽 구간을 모두 검사 |
| 구조 변경 | N:M 실행 회차 계보 유지. 분주 수량 증가 허용과 일반 분갈이·합식 수량 규칙을 혼동하지 않음 |
| 이동 | 이동 결과와 손실에 해당하는 별도 폐기 효과를 함께 기록 |
| 판매 | 예약 시 실수량 유지, 출고·출하 완료에서 차감, 취소·보상 및 생성/출고 스냅샷 유지 |
| 이력 | 연속 revision, full snapshot, 삭제 tombstone, Work·Sales·Lineage 원본 사실 보존 |
| 시간 | 주입 `Clock`, UTC timestamp, `TimeConfig` 농장 업무일 유지 |
| 계약 | 1차 리팩터링은 HTTP·JSON·DB 스키마·에러 의미 유지. 변경이 필요하면 별도 커밋과 계약 검증 |

두 번의 조회가 항상 불필요한 중복은 아니다. replay 재확인, 상위 업무 허용 조건과 물리 상태 불변식 검사는 목적이 다르다.
반대로 한 목록의 동일 참조 반복 조회와 동일 결과 조립의 복제는 제거 대상이다.

## 4. 목표 코드 형태와 스타일

큰 모듈 구조는 유지한다. 새로운 top-level 모듈·메시징·Event Sourcing·범용 workflow framework는 범위에 넣지 않는다.

```text
업무 application service
  업무 상태와 대상 확정 / 상위 트랜잭션
  → 업무 입력을 immutable command로 변환
  → Legacy 또는 Engine writer 선택 (전환 중에만)
  → 업무 이력·응답 조립

OrchidGroupMutationEngine
  replay → 정렬된 잠금 → replay 재확인
  → 현재 상태·관계·배치 검증
  → Mutation header / fence context 준비
  → Entity 상태 변경 / revision
  → Entry·Relation 저장 / 결과 반환
```

엔진 내부 협력 객체는 필요가 확인된 다음 세 책임부터 추출한다. 명칭은 구현 시 최종 확정한다.

| 책임 후보 | 포함 | 제외 |
|---|---|---|
| Mutation 대상 로더 | 정렬된 그룹·구역 잠금, 누락 확인, 품종 일괄 조회 | Work/Sales lifecycle 판단 |
| Ledger recorder | Mutation/fence, Entry·Relation 저장, 결과 조립 | 업무별 수량 계산, 외부 트랜잭션 생성 |
| Mutation 관계 검증 | 보정·보상 유형 및 대상 포함 관계 확인 | 원본 Work/Sales 취소 허용 여부 |

1차에는 Entity mutator 호출을 Engine에 유지해 writer inventory를 불필요하게 늘리지 않는다.
보조 객체가 실제 `OrchidGroup` 생성·저장을 맡게 되면 해당 책임 이동을 설명하고 architecture test와 수명 inventory를 함께 수정한다.
`mutation` 패키지 전체를 무조건 허용하는 식으로 검사를 느슨하게 만들지 않는다.

스타일 기준:

- 동작 없는 생성자 주입은 `@RequiredArgsConstructor`. 검증·테스트 편의 등 이유가 있는 수동 생성자는 유지.
- import 정렬, 들여쓰기, 중괄호와 override 배치를 하나의 도구 설정으로 고정. 초기 적용은 변경 패키지부터.
- 조회·저장·상태 변경이 있는 순회는 명시적 `for`를 우선. 순수 변환은 stream 사용.
- `var`는 우변에서 타입과 의미가 드러날 때 사용. 여러 문맥을 운반하는 map에는 업무 의미가 있는 이름 또는 record 사용.
- 긴 인자 목록은 함께 변하는 업무 값으로 묶는다. 모든 DTO에 builder를 추가하지 않는다.
- `execute`, `load`, `validate`, `record`, `toCommand` 등 메서드 역할을 구분. 단순히 `Manager`, `Helper`, `CommonService`로 옮기지 않는다.
- null의 업무 의미가 혼동되면 명시적 분기/결과 타입을 사용. 기존 외부 nullable 계약은 유지.
- 주석은 잠금·flush·호환·보존 이유를 설명. 코드가 이미 말하는 순서를 반복하지 않는다.
- canonical fingerprint mapper를 일반 응답 mapper로 바꾸지 않는다. 직렬화 목적과 과거 해시 호환이 다르다.
- 파일 길이와 메서드 길이는 검토 신호로 사용하며, 줄 수를 맞추기 위한 과도한 위임 계층은 만들지 않는다.

## 5. 실행 계획과 변경 단위

### PR-01. 회귀 기준과 검증 공백 정리

대상: `backend/src/test`, `backend/build.gradle.kts`, `.github/workflows/verify.yml`.

1. 이번 기준 커밋의 기본 테스트·PostgreSQL E2E 실행/skip 건수 기록.
2. disabled 49건을 시나리오별로 기존 활성 테스트와 대조. 중복이면 대체 근거를 남기고, 공백이면 전용 fixture로 복구.
3. Farm 생성·일괄 수정, 배치, Inbound 다중 결과, Sales 생성→출고→취소를 우선 보호.
4. PostgreSQL E2E를 CI에 추가하고 테스트 0건·전체 skip을 통과로 보지 않도록 검증.
5. 아래 검증표의 ACTIVE 다중 생성과 역순 배치 잠금을 추가. 발견된 실패는 재현 테스트와 별도 `fix`로 처리.

완료: 기존 성공 시나리오 유지, 필요한 disabled 테스트의 복구/대체 근거, PostgreSQL 실실행 결과 확보.
커밋 예: `test: restore deterministic inventory fixtures`, `chore: run postgres regression tests in ci`.

### PR-02. 포맷과 보일러플레이트만 정리

대상: Mutation 및 직접 호출부 중 이후 수정할 파일, formatter 설정.

1. 현재 주류 스타일에 맞춰 formatter를 선택·버전 고정하고 검사용 task 추가.
2. import·override·중괄호·생성자 주입만 정리. 메서드 이동과 동작 변경은 포함하지 않음.
3. JPA 생성자, 의도적인 mapper 초기화, 테스트용 생성자는 개별 확인.

완료: 포맷 검증 재실행 시 diff 없음, 기본 테스트 유지, 무관한 전체 백엔드 포맷 diff 없음.
커밋 예: `style: align mutation and writer code formatting`.

### PR-03. command 변환과 fingerprint 타입 정리

선행: PR-01. 대상: command fingerprint, 수량 command helper, 반복 details 변환.

1. 기존 command별 canonical payload와 해시의 고정 fixture를 먼저 확보.
2. `calculate(Object)` 업무 진입점을 typed overload로 변경. 범용 SHA-256 serializer는 별도 유지.
3. 수량 helper의 nullable 관계 인자 조합을 의도가 드러나는 값/메서드로 정리.
4. command 정규화와 Entity invariant를 구분. 필수 입력 확인을 이유로 Entity 보호를 제거하지 않음.
5. source 정렬과 결과 배열의 의미 있는 순서를 구분. result index와 ID 연결 유지.

완료: 기존 명령의 fingerprint 동일, 같은 키 replay 및 다른 payload 충돌 유지, command 지원 누락을 컴파일 단계에서 확인 가능.
커밋 예: `refactor: type mutation fingerprint inputs`.

### PR-04. 엔진의 잠금·ledger 책임 분리

선행: PR-01, PR-03. 대상: `OrchidGroupMutationEngine`, replay resolver, recorder/loader 후보.

1. 생성, 단건 변경, 수량 변경, 구조 변경, 보정의 실행 순서를 현재 코드로 표로 고정.
2. 정렬 잠금·누락 확인을 로더로 추출. 기존 Repository의 ID 정렬도 유지.
3. Mutation/fence와 Entry/Relation 기록을 분리하되 저장·flush 순서를 명시.
4. 단건 변경부터 옮긴 뒤 수량 변경, 마지막으로 N:M 생성·변환을 옮김.
5. `requireBaseline`과 같은 명칭이 'revision chain 존재'라는 현재 의미를 정확히 드러내도록 정리. persisted enum/issue code는 바꾸지 않음.
6. 상위 트랜잭션의 여러 Engine 호출과 최종 commit까지 테스트. helper에 `REQUIRES_NEW`를 추가하지 않음.

완료: 모든 public 메서드에서 실행 순서를 바로 읽을 수 있음, 동일 상태·revision·ledger·관계 결과, writer architecture 검사 유지.
커밋은 잠금 로더와 ledger recorder 추출을 나눔.

### PR-05. 배치 배치검증과 잠금 순서 개선

선행: PR-01, PR-04. 대상: placement policy, 생성/변환 경로, 일괄 수정·다중 실행 진입점.

1. ACTIVE에서 다중 생성의 flush 위험을 재현. 실패하는 경우 header/context 선행과 미저장 생성 계획으로 해결.
2. 목적 구역별 기존 점유를 일괄 조회하고, 순수 범위 검증과 DB 조회를 분리.
3. 검증용 점유 상태에 이번 요청에서 생성한 결과를 즉시 반영. 자동 배치와 명시 배치를 같은 상태에서 판정.
4. 구조 변경에서 비워진 원본 뒤쪽 구간, 수량 0 원본, 배치 제외 ID의 기존 의미 유지.
5. `updateBatch`처럼 단건 엔진을 반복 호출하는 상위 메서드의 전체 잠금 순서 점검. 한 호출 안의 정렬만으로 여러 호출의 deadlock이 해결된다고 가정하지 않음.
6. 조회 횟수와 JDBC batch 동작을 계측. INSERT 건수는 결과 수에 비례하므로 SELECT 상한과 구분.

완료: 겹침·자동 배치·부분 구간 해제 회귀 통과, 역순 요청 병렬 테스트 통과, 같은 구역의 결과 증가에 따라 동일 점유 SELECT가 반복되지 않음.
실패 수정은 `fix`, 조회 책임 분리는 `refactor` 커밋으로 분리.

### PR-06. Work·Inbound 호출부의 변환과 결과 조립 통일

선행: PR-03~05. 대상: `BatchStructureTransformationExecutor`, `InboundPottingService`, `InboundRecordService`, 관련 handler.

1. 구조 변경의 속성 상속·목적·손실을 담는 실행 계획 값을 계산. 상태 변경 전 원본 속성을 보존.
2. 같은 계획을 Legacy/Engine 경로에 전달하고 writer 선택은 한 번 수행.
3. 결과 ID·수량·계보·Work details 조립을 공통 factory로 모음. 두 분기의 물리 writer만 다르게 유지.
4. 입고 포트는 원본이 Inbound이므로 별도 executor 유지. 분갈이·분주와 같은 범용 실행기로 합치지 않음.
5. Work 포트의 활성 계획 재사용, 품종별 batch, 이동 손실의 별도 폐기 생성을 회귀 검증.
6. 단일 대상·기존 합식 호환 API는 입력 adapter에 모으고 기존 응답 유지.

완료: Legacy/Engine 결과 JSON과 업무 이력 의미 동일, 공통 결과 계산 한 곳, 실패 시 계획·그룹·효과 일부 잔존 없음.
커밋은 구조 변경과 입고를 분리.

### PR-07. Work 상세의 typed 해석과 일괄 조회

선행: PR-01. F7의 일괄 조회 수정은 PR-06 전에 독립적으로 진행 가능.

1. 보정 작업 ID로 effect를 일괄 조회하고 correction 연관의 lazy SELECT까지 측정.
2. 사용 중인 Work command/result JSON 형태를 테스트 fixture로 확보. 운영 개인정보를 그대로 복사하지 않음.
3. 효과 종류별 내부 결과 값과 codec을 만들고 map 접근을 codec에 한정.
4. `WorkOperationDetailService`는 로딩과 응답 조립을 조율. label/표시 변환은 별도 assembler에 둠.
5. handler→codec→기존 저장 JSON→기존 응답의 호환을 검증. 알 수 없는 구형 데이터의 fallback도 fixture로 유지.
6. 과거 품종·위치 표현이 현재 참조를 사용하는 부분은 snapshot 존재 여부를 먼저 확인. 과거값이 없으면 추정 backfill하지 않음.

완료: 보정 1/10/50건에서 추가 effect SELECT 상한 고정, 기존 상세 응답 동일, 신규 결과 키 변경 시 codec 테스트가 탐지.
커밋 예: `refactor: batch load correction details`, `refactor: isolate work effect detail codecs`.

### PR-08. 상시 대사와 전환 importer 분리

선행: PR-01. 대상: migration/reconciliation service, 관련 CLI.

1. Entity·manifest 어느 쪽에도 의존하지 않는 chain 검증 입력과 결과를 추출.
2. importer는 검증 실패를 적재 전 오류로 변환, reconciliation은 issue 목록으로 변환.
3. importer에서 manifest 검증과 source/link 해석을 분리하되 `importManifest`의 원자 트랜잭션 유지.
4. runtime/dedicated reconciliation/transition 도구의 위치와 Javadoc을 정돈. CLI main class 경로를 옮기면 Gradle·배포 명령·테스트를 함께 변경.
5. importer가 없어져도 상시 대사가 전환 package에 의존하지 않는지 확인.
6. 전체 이력 메모리 사용은 실제 건수 기준으로 측정. 현재 규모에서 문제가 없으면 streaming·청크 commit을 임의 도입하지 않음.

완료: chain 오류별 판정 유지, tombstone·현존 집합·최종 snapshot·replay·중간 rollback 검증, 대사 결과와 CLI 계약 유지.
전환 코드에 범용 importer framework를 만들지 않음.

### PR-09. Sales–Farm Entity 의존 축소

선행: PR-01, PR-04. 영향이 크므로 두 변경으로 나눔.

**09A, 전환 전:** Engine 경로에서 allocation 입력을 ID·수량 값으로 조립. 생성·출고 시점 Farm snapshot을 application 계약으로 반환받아 Sales snapshot으로 저장.
Legacy writer가 필요한 Entity는 식별된 호환 경로 안에 제한. 조회 계약을 단순 변경하면서 잠금 트랜잭션이 끝나버리지 않도록 최상위 경계를 유지.

**09B, 단일 Engine 전환과 연결:** Legacy 직접 변경을 제거한 뒤 allocation/movement 등의 타 모듈 Entity 연관을 ID 기반으로 정리.
기존 DB FK와 데이터 보존이 가능한 매핑 변경부터 적용하고, DDL 변경이 필요하면 별도 Flyway migration으로 수행.
Repository join·snapshot 생성·인쇄 및 조회 조립의 영향도 함께 처리.

완료: Engine 업무 계약에 managed `OrchidGroup` 노출 없음, 예약·출고·보상 원자성과 snapshot 시점 유지, 판매 상세 query 상한 유지.
모듈 외 Entity 직접 사용 금지 검사는 정리된 계약부터 적용하고 잔여 예외는 명시적으로 추적.
Work–Farm Entity 인자는 같은 방식으로 후속 정리하되 Sales와 한 PR에 묶지 않음.

### 별도 작업 A. Work 멱등성 계약 보강

F9는 동작 보존 리팩터링에 포함하지 않는다. ADR-001 완성 작업으로 별도 추적하고 운영 전환 판단 전에 범위를 확정한다.

1. 재현표 작성: 같은 key/같은 payload, 같은 key/다른 payload, 완료 후 replay, 부분 실행 후 replay, 병렬 요청, batch 결과 ID 재반환.
2. 외부 요청·효과·Mutation의 identity와 fingerprint를 각각 정의. 세 키를 하나로 합치지 않음.
3. 현재 저장된 command로 비교 가능한 범위는 먼저 보강. 구조화 effect identity와 receipt가 필요한 요청은 별도 스키마 변경 설계.
4. UNIQUE와 Repository 조회 기준을 맞추기 전 기존 중복 조합을 검사. `nullable 추가 → 검증 가능한 backfill → 제약 적용` 순서 사용.
5. 기존 effect 문자열을 Mutation source로 사용하는 이력·manifest 연결은 보존. 새 key 포맷으로 과거 ledger를 재작성하지 않음.
6. 충돌 오류 코드를 안정적으로 제공하고 Controller→OpenAPI→생성 TypeScript 순서로 갱신.

완료: 동일 내용 재시도는 기존 결과, 다른 내용은 명시적 충돌, ledger·effect·작업 중복 없음.
이는 `fix`/`feat` 변경이며 별도 API·PostgreSQL 검증이 필요하다.

### 별도 작업 B. 전환 안정화 후 Legacy 제거

[전환 코드 수명 문서](orchid-group-mutation-transition.md)의 기존 gate를 그대로 적용한다.
운영 `ACTIVE`, 전 인스턴스 Engine 고정, 합의된 기간의 smoke/reconciliation 통과, 진행 중 Work·기존 Sales 예약 회귀, 복구 절차 확보가 선행 조건이다.

1. 직접 writer와 routing flag 제거. Engine 전용 업무 facade 유지.
2. importer·cutover CLI·startup/routing 설정의 제거 범위를 운영 복구 정책과 함께 확정.
3. TARGET인 ledger·coverage·fence·상시 대사 및 DATA_RETAIN인 Work/Sales/Lineage/manifest 근거 유지.
4. writer inventory의 Legacy/transition 항목을 비우고 단일 Engine 경계를 검사.
5. 이미 적용된 V21~V23 수정·삭제 없음. 필요 변경은 후속 migration으로 추가.

완료: mode 설정 없이 Engine만 실행, 직접 writer 우회 없음, 보존 이력 조회·운영 검증 유지.
코드가 길다는 이유만으로 이 단계를 앞당기지 않는다.

## 6. 검증표

| 검증 대상 | 최소 시나리오 | 수단 |
|---|---|---|
| command·fingerprint | 공백·화분·decimal 정규화, source 순서, 결과 순서, 기존 해시 호환 | 순수 단위 + 기존 Engine 통합 |
| 생성·변경 | 단건/다건, no-op, 미존재, revision 없는 그룹, 실패 중간 저장 없음 | Engine 통합 |
| ACTIVE fence | 다중 생성/포트 2개 이상, transform, 한 트랜잭션 여러 Mutation, commit 검사, 실패 후 context 유출 없음 | PostgreSQL commit 포함 |
| 배치 | 같은/다른 구역, 결과끼리 겹침, 자동 배치, 뒤쪽 해제·재사용, 수량 0 | 순수 범위 검증 + PostgreSQL |
| 동시성 | 같은 원본 transform, 예약 vs 폐기, 보정 vs 예약, 역순 다중 수정, 같은 입고 다중 생성 | barrier/latch 기반 병렬 테스트 |
| Work | 계획/부분/완료, 이동 손실 폐기, 분주 수량 증가, 속성 상속, 포트 기존 계획 재사용 | 기존 Work E2E 확장 |
| Sales | 생성 예약, 수정 해제·재예약, 출고 snapshot·차감, 취소 복구·COMPENSATES, 전환 전 예약 후속 실행 | Sales 통합 + PostgreSQL |
| Work 상세 | 기존/신규 JSON, 보정 1/10/50건, 삭제·이름 변경 후 과거 표현 | fixture 기반 응답 + query count |
| 이관·대사 | revision gap, snapshot 불연속, terminal DELETE, manifest 불일치, Work/Lineage 연결, 재실행 0건, 중간 rollback | 순수 chain + 실제 PostgreSQL |
| 모듈 경계 | 신규 직접 writer 없음, 타 모듈 Repository/Entity 누출 회귀, transition 수명 | ArchUnit·기존 architecture 검사 |

실행 기준:

```bash
cd backend
./gradlew test --no-daemon
./gradlew workE2eTest --no-daemon
./gradlew workBenchmark -PworkBenchmarkEnforce=true --no-daemon
```

benchmark는 조회 변경 전후에 필요한 범위로 실행하고 `results.json`을 별도로 보관한다.
응답 시간은 환경 영향을 받으므로 전후 median/p95 비교 자료로 사용하고, CI에서는 정합성과 SQL 상한을 우선한다.
Reconciliation 성능은 '전체 이력 건수 무관 고정 쿼리'를 요구하지 않고 배치당 쿼리 수·메모리 사용으로 측정한다.

외부 계약 변경 시에만 아래 생성 절차를 추가한다.

```bash
python3 scripts/generate_openapi.py
cd frontend
npm run api:types
npm run check
```

## 7. 작업 순서와 완료 판단

권장 순서:

```text
PR-01 검증 기반
→ PR-02 포맷
→ PR-03 타입/변환
→ PR-04 엔진 책임
→ PR-05 배치/잠금
→ PR-06 Work·Inbound
→ PR-07 상세 조회
→ PR-08 이관·대사
→ PR-09A Sales 계약
→ 별도 Work 멱등성 계약 판단·보강
→ 운영 전환 검증·안정화
→ PR-09B 및 Legacy 제거
```

PR-07의 반복 조회 개선과 PR-08의 순수 chain 검증 추출은 엔진 변경과 독립적이다.
실제 구현은 변경 목적별 브랜치/PR로 나누고 하나의 거대한 리팩터링 PR로 합치지 않는다.

1차 완료 기준은 파일 수 감소가 아니다. 다음을 리뷰에서 확인한다.

- 실행 순서를 Engine과 해당 업무 facade에서 읽을 수 있음.
- 업무 계산·결과 변환에 Legacy/Engine 간 복제 없음.
- fingerprint·JSON·API의 의도치 않은 변경 없음.
- 대상 건수 증가로 같은 참조 SELECT가 반복되는 경로 제거.
- 엔진 및 배치 변경에 실제 PostgreSQL 회귀 결과 있음.
- 미완료 ADR 항목과 운영 전환 여부를 완료로 잘못 표기하지 않음.

## 8. 문서 정합성과 제외 범위

문서 갱신 필요:

- `08-roadmap.md`에는 구현된 자동/사용자 그룹·기간 작업·스냅샷 항목이 후속 후보와 섞여 있다. 현재 완료분과 남은 확장분을 분리할 필요가 있다.
- ADR-001의 receipt·effect identity는 목표 설계이고, 현재 구현 설명에는 기존 문자열 key가 남아 있다. 구현 상태표로 구분해야 한다.
- architecture의 application DTO/식별자 원칙과 현재 Entity 기반 Reader·handler 계약 사이의 잔여 전환 범위를 명시해야 한다.
- 운영 전환 기록은 복원 DB rehearsal과 실제 운영 적용을 구분해 유지해야 한다.

이번 작업에서는 계획과 색인만 작성한다. 새 원칙이 실제 구현되면 `04-architecture.md`, 정책이 바뀌면 해당 기능 문서와 `api/DOMAIN_RULES.md`, 전환 코드 이동·제거 시 수명 inventory를 함께 갱신한다.

이 하위 상세의 범위에서 제외:

- 인증·데모·대시보드·분석·정산 등 나머지 모듈의 리팩터링은 상위 전체 계획에서 다룬다.
- ledger 기반 Timeline/과거 시점 조회 신규 기능, Event Sourcing 전환, 신규 외부 연동.
- 문자열 상태를 전부 enum으로 바꾸는 API/DB 일괄 변경.
- 모든 CRUD에 interface/port 추가, 모든 helper의 Spring Bean 등록, 모든 DTO의 builder화.
- 기존 이력 삭제, 과거 snapshot 추정 복원, 운영 DB import 또는 ACTIVE 전환 실행.

## 9. 이번 검토의 실행 결과

- `./gradlew test --offline --no-daemon --rerun-tasks`: 성공. 239건 중 실행 190건 통과, 기존 disabled 49건, 실패 0건.
- `./gradlew workE2eTest --offline --no-daemon --rerun-tasks`: 성공. 실제 PostgreSQL E2E 22건 통과, skip·실패 0건.
- frontend 검증과 benchmark는 이번에 변경하지 않은 범위라 미실행. 구현 단계의 해당 검증은 위 계획에 포함.
- 백엔드 운영 코드·API·DB schema 수정 없음. 운영 환경 접근 및 전환 실행 없음.
