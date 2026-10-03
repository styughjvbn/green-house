# Backend 변경 용이성 평가

평가일: 2026-10-03. 기준 commit: `08b50dc7bc4be84aa4fec2688a47a027f8938e80`.

`01-system-map.md`, `04-code-quality.md`, 현재 구현 기준 문서, 관련 OpenAPI slice를 출발점으로 실제 production/test 코드와 Flyway를 추적했다. **가상 변경의 영향 분석이며 코드는 수정하지 않았다.** 아래의 신규 파일·migration·테스트도 생성하지 않았다.

## 1. 시나리오와 표기

요구사항에 세부 정책이 없으므로 영향 범위를 하나로 확정하지 않는다. 대표 변경을 아래처럼 고정하고, 더 작거나 큰 요구사항의 차이는 별도로 적었다. 이는 구현할 정책의 제안이 아니다.

| 시나리오 | 대표 변경 가정 | 현재 구조에서의 변경 용이성 |
| --- | --- | --- |
| A | 기존 구조 변경 workflow를 쓰는 새로운 고정 Work 코드·실행 효과·계보 유형 | handler/strategy 등록은 확장 가능. 계보의 닫힌 switch와 seed·capability까지 연결해야 함. 기존 기록형 template으로 사용자 유형만 추가하는 경우는 훨씬 작음 |
| B | 새로운 업무 상태 문자열을 추가하고 주의/판매불가 또는 비활성 의미 부여 | 저장·전달은 작음. 상태 의미는 policy·수량 기반 표시·직접 작성한 조회 조건에 나뉘어 있어 일관성 확인이 필요 |
| C | Sales 예약 허용 조건 변경: 특정 상태의 신규 예약을 거부하고 선택 조회와 일치 | writer는 Farm 원장/Entity에 모임. 조회·통계는 다른 조건을 사용. 예약 발생 시점까지 바꾸면 생성·수정·취소·출하·기존 데이터까지 확대 |
| D | 구조 변경 결과에 nullable 속성 `newAttribute` 추가, 결과 묶음에 저장하고 입력·상세·원장에 보존 | 가장 넓은 값 전달 변경. 생성/상속/호환 mapper, 여러 snapshot, fingerprint와 과거 데이터 해석을 함께 확인해야 함 |
| E | 여러 직접 판매 전표를 주간 단위로 묶는 새 정산·입금 대상 | 설정 enum 추가로 구현되지 않음. 현재 전표별 입금·경매일별 정산 외의 실제 aggregate/연결/잔액 규칙이 필요 |
| F | 같은 Spring/JPA 실행 환경에서 AI Agent 입력을 받아 기존 public application use case 호출 | 업무 실행 재사용 가능. 입력 검증·인가·실행자/감사 context·재시도 계약은 새 채널에서 확보해야 함 |

파일 표의 **수정**은 대표 가정에서 동작/값 전달을 바꿀 지점, **조건부**는 추가 요구에 따라 바뀌는 지점, **확인**은 영향받지만 본문 수정이 필수라고 확인하지 못한 지점이다. 테스트는 **수정/추가 사례 후보**와 **회귀 재실행 후보**를 구분한다. 목록의 파일 수를 작업량 점수로 사용하지 않는다.

## 2. A — 새로운 Work 유형 추가

### 현재 경로와 확장 지점

`WorkTypeService.getByCode/getActiveForPlan → WorkType.definition/handlerCode → WorkEffectProcessor → WorkEffectHandler → StructureChangeExecutor → StructureChangeStrategyRegistry → BatchStructureTransformationExecutor → mutationEngine.transform`.

[WorkTypeDefinition.java](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkTypeDefinition.java) `forCode`는 등록되지 않은 코드를 `GENERIC`으로 해석한다. `handlerCode`는 고정 코드 override를 우선하고 그렇지 않으면 저장된 template을 사용한다. [WorkTypeTemplate.java](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkTypeTemplate.java)은 handler code·effect kind·사용자 유형 허용을 정의한다. [WorkEffectProcessor.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java) `validateDefinitions`와 [StructureChangeStrategyRegistry.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistry.java) `validateDefinitions`는 정의로부터 필요한 구현을 계산해 기동 시 검사한다.

### 수정 production file / 영향 module

| 구분 | 실제 파일·메서드 | 영향 |
| --- | --- | --- |
| 수정 | [WorkTypeDefinition.java](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkTypeDefinition.java) enum 항목, `historyTitle` | 새 코드의 workflow·대상·등록·handler override·자동 이력 제목 |
| 신규 | 새 `WorkEffectHandler` 구현, 새 `StructureChangeStrategy` 구현 — 파일명 미정 | 기존 [RepotWorkHandler.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/RepotWorkHandler.java), [RepotStrategy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/RepotStrategy.java)처럼 Farm 효과·전략으로 등록. 새로운 Work 코드/handler code/strategy code의 대응 필요 |
| 수정 | [OrchidGroupLineageService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupLineageService.java) `relationType`, [OrchidGroupLineageRelationType.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/transformation/OrchidGroupLineageRelationType.java) | 새 handler code를 조회 계보 관계로 변환. 현재 switch는 MOVEMENT/REPOT/DIVIDE/MERGE 외 코드에서 예외 |
| 확인 | [WorkEffectProcessor.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java), [StructureChangeStrategyRegistry.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistry.java) | Spring 구현 목록에서 자동 등록하므로 registry 본문에 새 항목을 수동 추가할 필요는 없음. 누락/중복 기동 검사는 영향받음 |
| 확인 | [WorkType.java](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkType.java), [WorkTypeService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkTypeService.java), [WorkOperationPlanService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java), [StructureChangeRecordService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java), [StructureChangeExecutionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeExecutionService.java) | 기존 STRUCTURE_CHANGE workflow이면 정의 기반 capability·계획·기록·실행을 재사용. 특수 source/result 규칙이 있을 때만 실행 본문 변경 |
| 조건부 | [WorkTypeTemplate.java](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkTypeTemplate.java), [WorkTypeWorkflow.java](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkTypeWorkflow.java), [WorkEffectKind.java](../backend/src/main/java/com/greenhouse/backend/work/domain/effect/WorkEffectKind.java) | 기존 REPOT template/STRUCTURE_CHANGE workflow/STRUCTURE_CHANGE effect kind로 표현 불가능할 때만 새 enum 필요. 새 Work 코드마다 셋을 모두 추가하는 구조는 아님 |
| 조건부 | [WorkOperationDetailAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailAssembler.java), [WorkOperationMetricsReader.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationMetricsReader.java), [WorkEffectResults.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java), [WorkEffectDetailCodec.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkEffectDetailCodec.java) | 새 template의 표시·집계 의미, 새로운 효과 결과 형식이 필요한 경우 |
| 조건부 | [OrchidGroupMutationCommand.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationCommand.java), [OrchidGroupMutationCommandFingerprint.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationCommandFingerprint.java), [OrchidGroupMutationEngine.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java), [OrchidGroupMutationType.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupMutationType.java) | 기존 `transform`으로 표현되지 않는 새 원장 동작이 필요한 경우. sealed command와 exhaustive fingerprint switch까지 확대 |

주요 영향 module은 **work, farm**. 새 집계 의미를 공개하면 analytics, 새 원장 동작/감사 의미를 추가하면 audit도 재검증한다. 단순 Work 유형 추가만으로 sales·settlement를 수정해야 한다는 근거는 없다.

### 수정 test file

- **수정/추가**: [WorkTypeCapabilitiesTest.java](../backend/src/test/java/com/greenhouse/backend/work/domain/operation/WorkTypeCapabilitiesTest.java)의 코드별 CSV·template 대응과 capability/title 사례, [WorkEffectProcessorTest.java](../backend/src/test/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessorTest.java)의 handler 목록·누락/중복 등록, [StructureChangeStrategyRegistryTest.java](../backend/src/test/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistryTest.java)의 명시적 전략 목록.
- **수정/추가**: [WorkOperationStructureChangeCapabilityTest.java](../backend/src/test/java/com/greenhouse/backend/work/domain/operation/WorkOperationStructureChangeCapabilityTest.java), [OrchidGroupLineageIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupLineageIntegrationTests.java), [WorkStructureChangeE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkStructureChangeE2ETest.java)에 새 유형의 계획→실행→계보→취소 사례. 새 handler/strategy의 정책 테스트는 신규 파일이 필요하다(이름 미정).
- **회귀 재실행**: [WorkOperationIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/WorkOperationIntegrationTests.java), [WorkEffectStoreTest.java](../backend/src/test/java/com/greenhouse/backend/work/application/effect/WorkEffectStoreTest.java), [WorkIdempotencyPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkIdempotencyPostgresE2ETest.java), [ModularArchitectureTests.java](../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java), [ModuleBoundaryInventoryTest.java](../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java). 새 원장 명령이면 [MutationFingerprintCompatibilityTest.java](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/mutation/MutationFingerprintCompatibilityTest.java)와 [OrchidGroupMutationPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupMutationPostgresE2ETest.java)도 포함.

### switch/registry · DTO · DB · API/OpenAPI

| 항목 | 영향 |
| --- | --- |
| switch/registry | `WorkTypeDefinition.historyTitle` switch와 `OrchidGroupLineageService.relationType` switch 수정. handler/strategy는 구현 Bean 추가; registry 검사 본문은 자동 확장. 새 template이면 DetailAssembler의 template switch/집계 조건 확인 |
| DTO | 기존 구조 변경 입력·응답 형식이면 [StructureChangeCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeCommand.java), [StructureChangeResultInput.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeResultInput.java), [WorkTypeResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkTypeResponse.java)의 필드 추가 불필요. code/name/capability 값은 바뀜. 새로운 입력/결과 의미일 때만 계약 변경 |
| DB migration | 고정 유형은 `work_types`에 행이 있어야 `getByCode`가 찾는다. **신규 seed migration 필요**, code UNIQUE·template·active/system/sort order 확인. 기존 [V8](../backend/src/main/resources/db/migration/V8__migrate_work_operation_v2_data.sql)·[V13](../backend/src/main/resources/db/migration/V13__rename_movement_work_type.sql)을 수정하는 방식은 아님. 새 enum 저장값·원장 관계 유형이 있다면 배포 호환성도 확인 |
| API/OpenAPI | 기존 code는 string이므로 새 DB 행만 추가하고 schema를 유지하면 enum/schema 변경은 없음. 새 template/workflow/DTO/endpoint는 [work slice](../docs/api/slices/work.openapi.yaml), [work-operation slice](../docs/api/slices/work-operation.openapi.yaml) 등 실제 노출 schema 재생성 대상. 새 계보 enum은 계보 응답의 [farm-structure slice](../docs/api/slices/farm-structure.openapi.yaml)와 원장 응답의 [orchid-mutation slice](../docs/api/slices/orchid-mutation.openapi.yaml)에 전파 |

### 중복 규칙 수정 지점과 regression 위험

- 등록/계획/실행 서비스의 모든 곳에 새 코드 비교를 추가하는 것이 필수는 아니다. 대부분 `WorkTypeDefinition` capability를 읽는다. 다만 **정의의 코드**, **handler supports**, **strategy supports**, **계보 switch/enum**, **seed 값**의 일치는 직접 확인해야 한다.
- effect 조회인 [StructureChangeLineageQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeLineageQueryService.java) `isStructureChangeExecution`은 **저장된 handler code**를 `WorkTypeDefinition.forCode`에 넣는다. handler 이름이 유형 코드와 다르면 실행은 성공해도 구조 변경 계보에서 누락될 수 있다.
- registry 등록만 완료하면 계보 switch의 default 예외는 드러나지 않는다. 실행·조회·취소까지 이어지는 테스트가 필요하다.
- template의 분류와 handler의 실제 effect kind는 별도다. 기존 이동의 template handler `MOVE`와 구조 실행 결과 `MOVEMENT` 같은 호환 의미를 변경하지 않아야 한다.
- 새 원장 동작이면 멱등 fingerprint, source/result role, 수량 보존/증가/손실, 후속 사용 제한·보상 취소가 확대된다.

**더 작은 변경**: 기존 MEMO/PESTICIDE 등 허용 template으로 사용자 기록형 유형을 추가하는 것은 [WorkTypeService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkTypeService.java) `create`가 이미 제공한다. `CUSTOM_*` 행 생성이고 새로운 handler·strategy·enum·migration이 필요하지 않다. 테스트 사례 확장은 별도이며 production 코드 수정 0개로 가능한 경우다.

## 3. B — OrchidGroup에 새로운 상태 추가

### 현재 경로와 실제 제약

[OrchidGroup.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) `status`는 `String`이고, [OrchidGroupMutationDetails.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationDetails.java)는 비어 있지 않은 값과 `생성 취소`의 전용 경로 제한을 검사한다. [OrchidGroupStatusPolicy.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupStatusPolicy.java)는 주의·비활성·판매불가 목록을 제공하지만 전체 상태 whitelist는 아니다. 새 문자열 저장과 새 상태의 업무 의미 적용은 다른 변경이다.

### 수정 production file / 영향 module

| 구분 | 실제 파일·메서드 | 영향 |
| --- | --- | --- |
| 수정 | [OrchidGroupStatusPolicy.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupStatusPolicy.java)의 상수·`WARNING_STATUSES/INACTIVE_STATUSES/UNAVAILABLE_FOR_SALE_STATUSES` | 주의 표시, 감사 비활성 판단, 품종/분석의 판매 가능 수량 분류 |
| 조건부 수정 | [OrchidGroupRepository.java](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java) `findActiveWorkTargetsByHouseId/findActiveWorkTargets/findActiveWorkTargetsByIds/findDerivedGroupCandidates` | 새 상태가 비활성이라면 JPQL의 직접 상태 목록 4곳과 일치시켜야 함. 주의 상태만 추가하는 경우 이 목록의 수정은 필수가 아님 |
| 조건부 수정 | [OrchidGroup.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) `isVisibleInActiveViews`, `applyQuantityAndStatus`, `updateDetails`, 상태를 변경하는 domain method | 현재 active visibility는 `quantity > 0`이다. 수량을 남긴 채 비활성화하거나 특별 전이/수정 금지를 요구하면 별도 변경 필요 |
| 확인 | [FarmStatusService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmStatusService.java), [FarmMetricsReader.java](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmMetricsReader.java), [VarietyResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyResponseAssembler.java), [OrchidGroupAuditSupport.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupport.java) `actionForCorrection` | policy를 사용하므로 목록 변경이 자동 전파. 기본적으로 새 분기를 각 Service에 복제할 필요 없음 |
| 조건부 수정 | [OrchidGroupRepository.java](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java) `searchSellable`, [OrchidGroup.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) `reserve` | 새 상태를 **실제 Sales 예약에서도 거부**하려면 C의 변경까지 필요. 현재 두 경로는 판매불가 목록을 적용하지 않음 |
| 조건부 | [FarmWorkCorrectionAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java), [ReconciliationWorkHandler.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/ReconciliationWorkHandler.java), [OrchidGroupMutationDetails.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationDetails.java) | 상태별 보정·현장 동기화·생성 금지 규칙까지 새로 정의하는 경우. 기존 `생성 취소`만 특별 취급하는 guard와 혼동하지 않음 |

수정의 중심은 **farm**, 자동 영향은 **work**의 대상 선택, **sales**의 선택/예약, **analytics/dashboard**의 재고·주의 집계, **audit**의 사건 분류다. 각각 같은 상태에 대해 같은 의미를 적용하는지 확인해야 한다.

### 수정 test file

- **수정/추가**: [OrchidGroupInvariantTest.java](../backend/src/test/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupInvariantTest.java), [OrchidGroupCorrectionPolicyTest.java](../backend/src/test/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupCorrectionPolicyTest.java), [OrchidGroupAuditSupportTest.java](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupportTest.java), [OrchidGroupAuditIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupAuditIntegrationTest.java). 새로운 policy 분류 자체는 작은 domain 테스트를 추가할 수 있다(현재 전용 StatusPolicy 테스트 파일은 조사에서 확인하지 못함).
- **수정/추가**: [DerivedOrchidGroupIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/DerivedOrchidGroupIntegrationTests.java), [WorkOperationIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/WorkOperationIntegrationTests.java)의 새 상태 대상 선택/제외, [FarmStructureIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/FarmStructureIntegrationTests.java) 및 [SalesAnalyticsPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAnalyticsPostgresE2ETest.java)의 표시·집계 사례.
- 예약 금지까지 포함하면 [SalesInventoryMutationContractIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/SalesInventoryMutationContractIntegrationTest.java), [SalesOrchidGroupMutationEngineIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/SalesOrchidGroupMutationEngineIntegrationTest.java)에 직접 write 경로 거부/rollback 사례.
- **회귀 재실행**: [OrchidGroupCreationCancellationIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupCreationCancellationIntegrationTest.java), [WorkCorrectionAuditPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionAuditPostgresE2ETest.java), [OrchidGroupLedgerReconciliationPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupLedgerReconciliationPostgresE2ETest.java). 새 상태의 이력은 기존 snapshot에 그대로 보존되는지 확인.

### switch/registry · DTO · DB · API/OpenAPI

| 항목 | 영향 |
| --- | --- |
| switch/registry | 상태 enum/registry 추가는 없음. policy 목록과 4개 JPQL 목록, 수량 기반 표시 조건을 확인. 별도 전이 규칙이 필요할 때 Entity/Policy에 추가 |
| DTO | 생성/수정/보정/조회 status는 string이다. 값 추가만이면 [OrchidGroupCreateRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupCreateRequest.java), [OrchidGroupUpdateRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupUpdateRequest.java), [OrchidGroupResponse.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupResponse.java), [OrchidGroupCorrectionInput.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/OrchidGroupCorrectionInput.java)의 필드 변경 불필요. capability·상태 metadata를 새로 공개할 때만 DTO 추가 |
| DB migration | 상태 컬럼은 문자열이고 조회한 Flyway에는 OrchidGroup 상태 허용값 CHECK가 없음. 기존 컬럼 크기 안의 새 값만 추가하면 schema migration 불필요. 기존 상태 재분류/데이터 변환·새 constraint·수량 0 전환을 요구하면 신규 migration과 원장 정합성 작업 필요 |
| API/OpenAPI | [orchid-command slice](../docs/api/slices/orchid-command.openapi.yaml)의 status도 string이며 enum 목록이 없음. 값 추가만으로 enum 재생성 대상이 되지는 않음. 새 validation/metadata/capability 계약을 공개하면 해당 Farm/Work/Sales slice 갱신 |

### 중복 규칙 수정 지점과 regression 위험

- `inactiveStatuses`와 Repository JPQL에 비활성 값이 따로 있다. Entity active visibility와 여러 배치 조회는 수량으로 판정한다. **policy 한 곳을 바꿨다고 모든 active 조회가 바뀌지 않는다.**
- 새 상태를 목록에 빠뜨리면 `isSaleable`의 blacklist 방식에서 기본적으로 판매 가능으로 분류한다. 반대로 판매불가 목록을 바꿔도 실제 예약을 막는 것은 아니다.
- 양수 수량의 새 비활성 상태는 작업 대상·자동 그룹·배치 화면·주의 집계·감사 이벤트가 서로 다르게 취급할 수 있다.
- 과거 상태 문자열을 rename/backfill하면 원장 before/after와 현재 Entity, 감사 데이터, 저장된 효과의 의미까지 영향을 받는다. 표시 이름 변경과 저장값 변경을 구분해야 한다.

**변경 용이성 판정**: 문자열 전달은 작지만 의미를 부여하는 변경은 컴파일러가 누락 지점을 찾아주지 않는다. 값 저장 성공을 상태 정책 변경의 완료로 볼 수 없다.

## 4. C — Sales 예약 정책 변경

### 현재 경로와 실제 규칙

`SalesSlipCreationService.create → allocation snapshot → inventoryService.reserve → mutationEngine.reserve → OrchidGroup.reserve`.

수정은 `SalesSlipUpdateService.update → releaseForEdit → 새 allocation → reserve`, 완료는 `SalesSlipStatusService.updateStatus → SalesSlipOutboundService.complete → outbound → consumeReservation`, 취소는 완료 여부에 따라 `cancelOutbound` 또는 `cancelReserve`다.

[OrchidGroup.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) `reserve`는 양수·가용 수량을 검사하고 예약량을 더한다. `getAvailableQuantity`는 `max(0, quantity - reservedQuantity)`다. [OrchidGroupRepository.java](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java) `searchSellable`도 수량 차이와 검색 조건을 사용한다. **현재 이 두 지점에는 `OrchidGroupStatusPolicy.isSaleable` 검사가 없다.** 분류 policy는 품종/분석 통계에서 사용되므로 예약 허용 조건과 동일한 규칙이라고 가정하지 않는다.

### 수정 production file / 영향 module

| 구분 | 실제 파일·메서드 | 영향 |
| --- | --- | --- |
| 수정 | [OrchidGroup.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) `reserve`, [OrchidGroupStatusPolicy.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupStatusPolicy.java) | 새 상태별 예약 거부를 실제 writer의 불변식/정책으로 적용 |
| 수정 | [OrchidGroupRepository.java](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java) `searchSellable` | 검색에서 예약 불가 대상을 제외하거나 불가 이유를 반환하는 계약과 일치 |
| 확인/조건부 | [OrchidGroupReader.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java), [OrchidGroupState.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupState.java), [SalesOrchidGroupQueryService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupQueryService.java), [SalesOrchidGroupSearchResponse.java](../backend/src/main/java/com/greenhouse/backend/sales/dto/SalesOrchidGroupSearchResponse.java) | 필터만 바꾸면 그대로 재사용. 예약 가능 수량/사유·capability를 새로 공개하면 application/read DTO 변경 |
| 확인 | [OrchidGroupMutationEngine.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java) `reserve/applyQuantityMutation`, [SalesSlipInventoryService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java) `reserve/release/outbound` | 상태 거절의 rollback·lock·replay·이동 이력 보존. Entity guard만 바꾸면 기존 명령/registry 본문 변경은 필수가 아님 |
| 조건부 | [SalesSlipCreationService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java), [SalesSlipUpdateService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java), [SalesSlipStatusService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipStatusService.java), [SalesSlipOutboundService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipOutboundService.java) | 예약 **시점** 변경 시 생성/수정/완료/취소 호출 순서 및 대칭 처리 수정 |
| 조건부 | [SalesSlip.java](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlip.java), [SalesSlipActionResolver.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipActionResolver.java), [SalesInventoryMovementType.java](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesInventoryMovementType.java), [SalesOrchidGroupLedgerRehearsalInspector.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupLedgerRehearsalInspector.java) | 예약을 가진 전표 상태/행동, 새 movement 구분, 기존 예약/출하 원장 대조의 의미가 바뀌는 경우 |
| 조건부 | [OrchidGroup.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) `getAvailableQuantity`, [FarmMetricsReader.java](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmMetricsReader.java), [VarietyResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyResponseAssembler.java) | 판매 전용 가용량과 농장 전체 가용량을 다르게 만들거나 통계 의미를 바꾸는 경우. 공유 가용량 변경은 구조 변경·폐기에도 전파되므로 필수 변경으로 단정하지 않음 |

주요 수정은 **farm, sales**. 예약 시점을 바꾸면 **auction**의 lot 생성/출하와 **work**의 예약량을 고려하는 변형·폐기, 재고 **analytics**도 재검증한다. 새로운 입금/정산 정책까지 연동하지 않는 한 settlement 수정은 필수라고 확인하지 않았다.

### 수정 test file

- **수정/추가**: [OrchidGroupInvariantTest.java](../backend/src/test/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupInvariantTest.java), [SalesInventoryMutationContractIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/SalesInventoryMutationContractIntegrationTest.java), [SalesOrchidGroupMutationEngineIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/SalesOrchidGroupMutationEngineIntegrationTest.java), [SalesIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/SalesIntegrationTests.java)에 상태별 허용·거절, 생성/편집 전체 rollback, 중복 배분 수량 사례.
- 예약 시점까지 변경하면 [SalesSlipStatusRulesTest.java](../backend/src/test/java/com/greenhouse/backend/sales/domain/SalesSlipStatusRulesTest.java), [SalesSlipActionResolverTest.java](../backend/src/test/java/com/greenhouse/backend/sales/application/SalesSlipActionResolverTest.java), [SalesSlipOutboundServiceTest.java](../backend/src/test/java/com/greenhouse/backend/sales/application/SalesSlipOutboundServiceTest.java), [SalesOrchidGroupSnapshotIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/SalesOrchidGroupSnapshotIntegrationTest.java)의 예상 전표 행동·snapshot 시점도 변경.
- **PostgreSQL 사례 수정/추가**: [SalesInventoryPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesInventoryPostgresE2ETest.java)의 병렬 예약/출고/취소, [SalesAuctionBoundaryPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAuctionBoundaryPostgresE2ETest.java)의 원자 출하. 기존 pending 전표를 가진 상태에서 새 정책 배포 후 편집·출하하는 사례 필요.
- **회귀 재실행**: [WorkStructureChangeE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkStructureChangeE2ETest.java), [OrchidGroupMutationPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupMutationPostgresE2ETest.java), [OrchidGroupLedgerReconciliationPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupLedgerReconciliationPostgresE2ETest.java). 공유 `getAvailableQuantity` 변경이면 Work의 부분 변형·폐기까지 포함.

### switch/registry · DTO · DB · API/OpenAPI

| 항목 | 영향 |
| --- | --- |
| switch/registry | 기본 허용 조건 변경에는 새 registry 없음. 예약 시점 변경이면 생성/수정/상태 서비스의 완료/취소 if 분기 수정. 새 전표 상태·원장 명령을 도입하면 Sales 상태 조건, movement enum, sealed mutation command/fingerprint switch까지 확대 |
| DTO | 거부 조건만 바꾸면 SalesSlipCommand/명령 schema 유지 가능. 선택지의 예약 가능 수량·불가 사유를 공개하면 [SalesOrchidGroupSearchResponse.java](../backend/src/main/java/com/greenhouse/backend/sales/dto/SalesOrchidGroupSearchResponse.java)와 [OrchidGroupState.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupState.java) 등 변경. 상태를 추가하면 status 계약도 변경 |
| DB migration | 조건만 변경하고 기존 데이터를 그대로 유지하면 필수 DDL은 없음. [V14](../backend/src/main/resources/db/migration/V14__enforce_inventory_and_sales_consistency.sql)의 `0 <= reserved_quantity <= quantity`와 Sales 상태 CHECK는 그대로 검토. 시점 변경으로 기존 예약을 풀거나 재구성하면 **원장·movement·전표를 일치시키는 전환 작업** 필요. 새 상태/컬럼/명령 저장값은 신규 migration 여부 결정 |
| API/OpenAPI | 같은 입력에 대한 새 거절 의미는 동작 계약 변경이지만 schema diff가 반드시 생기지는 않음. 새 선택지/capability/status/오류 code가 있으면 [sales slice](../docs/api/slices/sales.openapi.yaml)와 관련 Farm slice 갱신. 판매 정책 설명도 갱신 대상 |

### 중복 규칙 수정 지점과 regression 위험

- 저장 시 예약(CreationService), 편집 시 release/re-reserve(UpdateService), 완료 시 consume(OutboundService), 미완료 취소 시 release(StatusService)가 **하나의 예약 lifecycle을 나눠 표현**한다. 시점 변경이면 한 지점만 바꿀 수 없다.
- 수량 차이는 Entity 가용량·Repository 선택 쿼리·집계에 반복된다. 판매 전용 제한을 전역 가용량에 반영하면 Work 변형·폐기까지 제한될 수 있다.
- 새 정책이 이미 예약된 묶음의 출하를 거부할지, 새 예약에만 적용할지 결정되지 않았다. 기존 전표 수정은 기존 예약을 풀었다 다시 예약하므로 새 조건에서 실패할 수 있다. 트랜잭션 rollback으로 원래 예약이 복구되는지 확인해야 한다.
- 재시도는 `RESERVE/RELEASE_EDIT/OUTBOUND:<version>` 등 source key와 fingerprint를 사용한다. 기존 키/원장을 다른 뜻으로 재해석하거나 직접 `reserved_quantity`만 고치면 정합성을 잃을 수 있다.
- 예약 시점 변경은 이중 예약·해제 누락·출하 중복 차감·취소 복구와 생성/출하 snapshot 시점의 회귀 위험이 크다. 문서상의 가정만으로 테스트를 완료 처리할 수 없다.

**변경 용이성 판정**: 허용 조건은 실제 writer 한 곳에 넣을 수 있지만 read 조건까지 일치시켜야 한다. 예약 lifecycle 변경은 상태 전이·snapshot·원장을 함께 바꾸는 변경이다.

## 5. D — 구조 변경 결과에 새로운 속성 추가

### 현재 값 전달 경로

`StructureChangeResultInput → BatchStructureTransformationExecutor.planResults → OrchidGroupCreateRequest → mutationCommand/OrchidGroupMutationDetails → OrchidGroupMutationEngine.createGroup → OrchidGroup`.

추가로 `StateSnapshot.from/canonical → mutation entry`, `WorkEffectResults.toMap → effect JSON → WorkEffectDetailCodec → WorkExecutionResultResponse`, `OrchidGroupResponse` 및 그래프용 `State → WorkOperationGraphStateResponse`를 확인해야 한다. 대표 가정은 저장·조회되는 nullable 속성이다. 실행 당시 메모성 결과 JSON만 추가하는 경우와 다르다.

### 수정 production file / 영향 module

| 구분 | 실제 파일·메서드 | 영향 |
| --- | --- | --- |
| 수정 | [StructureChangeResultInput.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeResultInput.java) | 새 입력, validation/default/생략 의미. `@JsonIgnoreProperties(ignoreUnknown = true)`를 사용하므로 구버전의 알 수 없는 입력 속성 처리도 고려 |
| 수정 | [BatchStructureTransformationExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java) `planResults/mutationCommand/moveExistingGroups/isIdentityPreservingMovement` | 상속할지 입력값을 쓸지, 결과 DTO와 mutation details 복사. 동일 ID 이동에서 속성이 변하면 여전히 순수 이동인지 확인 |
| 수정 | [OrchidGroupCreateRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupCreateRequest.java), [OrchidGroupMutationDetails.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationDetails.java)와 `withPlacement` | 현재 중간 생성 DTO와 명령 값 전달. placement 재해결 시 새 속성을 잃지 않도록 보존 |
| 수정 | [OrchidGroup.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java), [OrchidGroupMutationEngine.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java) `createGroup/updateDetails` | Entity 필드 저장·정규화·수정 동작. 새로운 속성이 수량/배치 policy에 영향을 준다면 그 정책도 조건부 변경 |
| 수정/확인 | [OrchidGroupStateSnapshot.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupStateSnapshot.java) `from/canonical`, [OrchidGroupMutationEntry.java](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupMutationEntry.java) | 원장 before/after에 새 속성 포함. entry는 typed snapshot을 JSON으로 저장하므로 컬럼을 하나씩 추가하는 구조는 아님 |
| 수정 | [WorkEffectResults.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java) `ResultGroup/Transformation`, [WorkEffectDetailCodec.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkEffectDetailCodec.java) `results`, [WorkExecutionResultResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkExecutionResultResponse.java), [OrchidGroupResponse.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupResponse.java) | 실행 당시 속성의 결과 JSON과 상세·묶음 응답 보존. input command row의 값과 저장 결과 row의 값 중 무엇을 표시할지 정의 |
| 수정/조건부 | [LegacyStructureChangeRequestMapper.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/LegacyStructureChangeRequestMapper.java), [MergeWorkHandler.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/MergeWorkHandler.java), [RepotWorkOperationRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/transformation/RepotWorkOperationRequest.java), [MergeWorkOperationRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/transformation/MergeWorkOperationRequest.java) | 기존 결과 DTO→StructureChangeResultInput의 positional 생성 지점. 기존 호환 endpoint에서 새 속성을 입력받는다면 해당 요청도 확장; 그렇지 않으면 명시적 기본/상속 처리 |
| 확인/수정 | [OrchidGroupCommandService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java) `mutationDetails/hasSameDetails`, [VarietyService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyService.java) `update`, [InboundRecordService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java) `create`, [InboundPottingService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingService.java) `potting` | 같은 MutationDetails 생성 지점. 새 record component의 compile 영향뿐 아니라 일반 수정·품종 변경에서 기존 속성을 null로 덮지 않는지 확인 |
| 조건부 수정 | [OrchidGroupUpdateRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupUpdateRequest.java), [InboundPottingResultInput.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/InboundPottingResultInput.java), [InboundRecordPottingRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/inbound/InboundRecordPottingRequest.java), [InboundPlacementInput.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPlacementInput.java) | 해당 경로에서도 새 속성을 입력/수정할 수 있어야 하는 경우. 모든 요청에 무조건 필드를 추가하는 것은 아님 |
| 수정/조건부 | [OrchidGroupAuditSnapshot.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSnapshot.java), [OrchidGroupAuditSupport.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupport.java) `FIELDS/snapshot/values` | 새 속성을 업무 감사의 changedFields/before/after에 포함한다면 세 위치와 snapshot을 함께 변경. 원장 snapshot 확장과 감사 snapshot 확장은 별도 계약 |
| 조건부 수정 | [WorkOperationMutationGraphPort.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationMutationGraphPort.java) `State`, [FarmWorkOperationMutationGraphAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/FarmWorkOperationMutationGraphAdapter.java) `stateNode`, [WorkOperationGraphQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationGraphQueryService.java) `state`, [WorkOperationGraphStateResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationGraphStateResponse.java) | 그래프 상태에서도 새 속성을 공개할 때의 연속 mapping |
| 확인/조건부 | [OrchidGroupMutationCommandFingerprint.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationCommandFingerprint.java), [OrchidGroupMutationFingerprint.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationFingerprint.java), [WorkRequestFingerprint.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkRequestFingerprint.java), [WorkEffectStore.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java) | nested details/요청 직렬화에 새 필드가 자동 포함되어 과거 멱등 hash가 달라질 수 있음. 호환 정책에 따라 계산 구현 수정 필요 |
| 확인/조건부 | [OrchidGroupMutationEffectiveHeadPolicy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEffectiveHeadPolicy.java), [OrchidGroupLedgerReconciliationService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerReconciliationService.java), [OrchidGroupStateChainMigrationService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupStateChainMigrationService.java), [OrchidGroupStateChainMigrationManifest.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupStateChainMigrationManifest.java) | canonical snapshot equality, 현재/과거 상태 비교, baseline/manifest fingerprint·복구 도구의 호환 |
| 조건부 | [OrchidGroupState.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupState.java), [SalesSlipAllocationBatch.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationBatch.java), [SalesOrchidGroupSnapshot.java](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesOrchidGroupSnapshot.java), [SalesOrchidGroupSnapshotData.java](../backend/src/main/java/com/greenhouse/backend/sales/application/document/SalesOrchidGroupSnapshotData.java), [SalesSlipDocumentAllocation.java](../backend/src/main/java/com/greenhouse/backend/sales/application/document/SalesSlipDocumentAllocation.java) | Sales 선택/출하 당시 새 속성도 보존·노출해야 하는 경우. 현재 Entity를 나중에 조회해 과거 속성을 채우는 변경은 아님 |

대표 변경의 핵심 module은 **work, farm**. 감사에 포함하면 **audit**, 판매 snapshot에 포함하면 **sales/print**, 자동 그룹의 키·검색·통계에 쓰면 **farm 조회/analytics**까지 확대된다.

### 수정 test file

- **수정/추가**: [WorkStructureChangeE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkStructureChangeE2ETest.java), [OrchidGroupMutationEngineIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupMutationEngineIntegrationTest.java), [OrchidGroupMutationRoutingIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupMutationRoutingIntegrationTest.java), [OrchidGroupLineageIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupLineageIntegrationTests.java). 값 입력·상속·복수 결과·같은 ID 이동·취소 후 보존/복원 사례.
- **수정/추가**: [WorkEffectResultsTest.java](../backend/src/test/java/com/greenhouse/backend/work/application/effect/WorkEffectResultsTest.java), [WorkOperationDetailContractTest.java](../backend/src/test/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailContractTest.java) 및 [detail fixture](../backend/src/test/resources/work/detail-contract.json). 새 속성과 속성이 없는 과거 JSON을 모두 검사.
- **수정/추가**: [MutationFingerprintCompatibilityTest.java](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/mutation/MutationFingerprintCompatibilityTest.java), [WorkRequestFingerprintTest.java](../backend/src/test/java/com/greenhouse/backend/work/application/operation/WorkRequestFingerprintTest.java) 및 [mutation fingerprint fixture](../backend/src/test/resources/farm/mutation-fingerprints.json). 새 null 속성 때문에 과거 hash가 달라지는지 확인하며, 기대값 교체만으로 호환 문제를 덮지 않음.
- 감사에 포함하면 [OrchidGroupAuditSupportTest.java](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupportTest.java), [OrchidGroupAuditIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupAuditIntegrationTest.java), [WorkCorrectionAuditPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionAuditPostgresE2ETest.java). Sales snapshot에 포함하면 [SalesSlipAllocationBatchTest.java](../backend/src/test/java/com/greenhouse/backend/sales/application/SalesSlipAllocationBatchTest.java), [SalesOrchidGroupSnapshotIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/SalesOrchidGroupSnapshotIntegrationTest.java).
- **PostgreSQL 수정/추가·회귀**: [OrchidGroupStateChainMigrationPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupStateChainMigrationPostgresE2ETest.java), [OrchidGroupLedgerReconciliationPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupLedgerReconciliationPostgresE2ETest.java), [CorrectionCompensationOrchidGroupMutationEngineIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/CorrectionCompensationOrchidGroupMutationEngineIntegrationTest.java), [OrchidGroupMutationPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupMutationPostgresE2ETest.java). 기존 ACTIVE 원장과 구형 snapshot을 가진 DB에서 변경 후 쓰기/비교/복구 확인.
- record/생성자 signature 변경으로 fixture를 고칠 수 있는 테스트는 `new OrchidGroupMutationDetails/StructureChangeResultInput/OrchidGroupStateSnapshot` 전체 참조도 확인해야 한다. 호환 생성자를 추가해 컴파일을 유지하더라도 기본값 유실 검증은 남는다.

### switch/registry · DTO · DB · API/OpenAPI

| 항목 | 영향 |
| --- | --- |
| switch/registry | 속성만 추가하면 Work handler/strategy registry 변경 없음. 새 purpose/속성에 따라 상태를 계산하면 `BatchStructureTransformationExecutor.resultStatus` switch 조건부 수정. 새로운 원장 command가 필요한 경우에만 sealed command/fingerprint switch 변경 |
| DTO | 위 입력·중간 CreateRequest·MutationDetails·효과 결과·상세·묶음 응답의 값 전달 수정. 그래프·Sales snapshot·일반 수정/입고는 요구 범위에 따라 확대. JSON Map에는 새 key가 compiler 없이 추가되므로 typed 입력만 바꿔 끝나지 않음 |
| DB migration | Entity에 저장하는 속성이므로 **새 OrchidGroup 컬럼 migration 필요**. nullable/default/backfill 순서 결정. 원장 entry/effect/audit의 JSON 컬럼 자체는 유지 가능하지만 구형 JSON의 없는 속성 해석은 필요. 현재 값으로 과거 snapshot을 채우면 안 됨 |
| DB 쓰기 fence | [V22](../backend/src/main/resources/db/migration/V22__enforce_orchid_group_mutation_write_fence.sql)는 ACTIVE 원장의 모든 INSERT/UPDATE에서 mutation context·revision·entry를 검사한다. 실제 row backfill이면 단순 UPDATE와 원장 기록의 정합성을 함께 검토. nullable 컬럼 추가만 하고 row UPDATE를 하지 않는 경우와 구분 |
| API/OpenAPI | [work-operation](../docs/api/slices/work-operation.openapi.yaml), [orchid-command](../docs/api/slices/orchid-command.openapi.yaml), [farm-structure](../docs/api/slices/farm-structure.openapi.yaml), [orchid-mutation](../docs/api/slices/orchid-mutation.openapi.yaml)의 실제 노출 schema 확인. 입고/Sales/그래프 계약도 변경하면 해당 slice 포함. 생성된 명세는 직접 수정하지 않음 |

### 중복 규칙 수정 지점과 regression 위험

- 속성 복사는 `planResults → CreateRequest → MutationDetails → Entity`에 이어, 일반 수정·입고·품종 변경 mapper에도 있다. 같은 record 생성 지점을 고치는 것과 **기존 값 보존/새 값 상속 정책을 고치는 것**을 구분해야 한다.
- 감사의 `FIELDS`, `values`, snapshot 필드는 위치 대응을 사용한다. 새 속성을 snapshot에만 넣으면 changedFields에 잡히지 않을 수 있다.
- 효과 결과는 typed `toMap`과 상세 codec의 문자열 key mapping을 각각 변경해야 한다. 입력 Map에만 존재하면 실행 결과 DTO에 자동 노출되지 않는다.
- 현재 fingerprint는 JSON key 순서 등을 정규화하지만, 새 필드를 과거 hash에서 제외하는 version 호환 처리는 자동 제공하지 않는다. **생략된 새 nullable 필드도 직렬화에 추가되면 과거 요청/명령과 hash가 달라질 수 있다.**
- snapshot record 확장은 canonical equality와 baseline fingerprint에도 영향을 준다. 과거 null과 현재 기본값이 다르면 현재 상태/원장 head 불일치로 해석할 수 있다. rolling 배포 중 구버전 writer의 새 속성 보존도 확인해야 한다.
- 현재 `OrchidGroupMutationEngine.compensate`는 source의 수량·상태·배치 위치를 `reconcile`로 복원한다. 결과 묶음에만 새 속성을 저장하고 source는 바뀌지 않으면 취소 알고리즘 변경이 필수는 아니다. source 속성까지 바꾸는 요구라면 새 속성 복원 규칙과 `reconcile/compensate` 수정이 필요하다.

**더 작은 변경**: Entity 속성이 아니라 실행 당시 메모성 결과 항목만 추가하면 Work 입력/효과 JSON/상세 계약에 한정할 수 있고 새 OrchidGroup 컬럼은 필요 없다. 그 경우에도 멱등 hash·과거 JSON·노출 계약은 확인해야 한다.

## 6. E — 새로운 Settlement 방식 추가

### 현재 구현이 제공하는 것

[SettlementUnit.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/SettlementUnit.java)에는 `SALES_SLIP/MONTHLY_BATCH/AUCTION_DATE`가 있다. 그러나 [PartnerSettlementSettings.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerSettlementSettings.java) `calculateExpectedPaymentDate`는 **payment delay와 PaymentDayMode만 사용**한다. `settlementUnit/ruleJson/autoSettleEnabled` 등은 설정 저장·응답·감사에서 확인되며, 단위별 aggregate를 만드는 실행 registry는 없다.

실제 입금 경로는 [SalesPaymentService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java) `confirmPayment`의 직접 판매 전표와 [PaymentService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentService.java) `confirmAuctionPayment`의 경매 정산이다. [AuctionSettlementService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java) `rebuild/rebuildExistingResults`와 [AuctionSettlement.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/AuctionSettlement.java)은 경매장·경매일 기준 정산을 생성/보존한다. 따라서 `MONTHLY_BATCH` enum 존재를 월별 일괄 정산의 구현 증거로 볼 수 없다.

### 수정 production file / 영향 module

대표 가정은 주간 일괄 직접 판매 정산이다. 아래 신규 책임의 클래스명·저장 설계는 이 조사에서 확정하지 않았다.

| 구분 | 실제 파일·메서드 또는 신규 책임 | 영향 |
| --- | --- | --- |
| 수정 | [SettlementUnit.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/SettlementUnit.java) | 새 방식 값을 설정/API에 노출한다면 enum 확장 |
| 신규 | 일괄 정산 aggregate/line·생성/조회/입금 application service·Repository·입력/응답 DTO | 대상 전표 연결, 기간·거래처별 묶음, 금액 snapshot·상태·중복 연결·재정산/취소 의미가 현재 모델에 없음. 실제 파일명은 미정 |
| 수정/조건부 | [PartnerSettlementSettings.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerSettlementSettings.java), [PartnerSettlementSettingsService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerSettlementSettingsService.java), [PartnerSettlementSettingsRequest.java](../backend/src/main/java/com/greenhouse/backend/settlement/dto/PartnerSettlementSettingsRequest.java), [PartnerSettlementSettingsResponse.java](../backend/src/main/java/com/greenhouse/backend/settlement/dto/PartnerSettlementSettingsResponse.java), [SettlementAuditSupport.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/SettlementAuditSupport.java) | 방식별 parameter·기본값·검증·감사. enum 값 추가만이면 기존 typed DTO의 Java 필드 추가는 불필요; 추가 설정 필드는 변경 필요 |
| 수정 | [PaymentTargetType.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PaymentTargetType.java) | 별도 일괄 정산을 입금 대상으로 삼으면 새로운 target type 필요 |
| 확인/조건부 | [PaymentLedgerService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java), [PartnerPaymentEvent.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerPaymentEvent.java), [PartnerPaymentEventRepository.java](../backend/src/main/java/com/greenhouse/backend/settlement/repository/PartnerPaymentEventRepository.java), [PaymentEventReader.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentEventReader.java), [PartnerPaymentEventResponse.java](../backend/src/main/java/com/greenhouse/backend/settlement/dto/PartnerPaymentEventResponse.java) | target type+ID 기반 event/replay 조회는 이미 범용이다. enum 전파만으로 재사용 가능한 부분과 새 대상 입력·조회·금액 규칙을 구분 |
| 수정/조건부 | [SalesPaymentService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java), [SalesSlip.java](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlip.java), [SalesSlipActionResolver.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipActionResolver.java), [SalesSlipUpdateService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java), [SalesSlipStatusService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipStatusService.java) | 일괄 정산에 연결된 전표의 개별 입금/수정/취소 허용, 분배 후 paid/remaining 반영, 중복 입금 방지. 기존 action은 SALES_SLIP target event를 조회하므로 새 target event만 기록하면 자동 인지되지 않음 |
| 수정/조건부 | [PartnerBalanceService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerBalanceService.java), [SalesSlipRepository.java](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipRepository.java), [AnalyticsQueryService.java](../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java) | 전표/일괄 정산 금액을 중복 채권으로 집계하지 않을 규칙. 현재 전표 채권 합계와 새 방식의 연결 의미에 따라 변경 |
| 조건부 | [ExpectedPaymentDateCalculator.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/ExpectedPaymentDateCalculator.java) 및 [PartnerSettlementSettings.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerSettlementSettings.java) `calculateExpectedPaymentDate` | 주간 마감/납기 계산을 실제 요구할 때. 설정에 새 unit만 넣어도 주간 날짜 계산이 추가되지 않음 |
| 확인 | [AuctionSettlementService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java), [AuctionSettlement.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/AuctionSettlement.java), [AuctionSettlementLine.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/AuctionSettlementLine.java), [PaymentService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentService.java), [AuctionSettlementInitializer.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementInitializer.java) | 직접 판매의 새 방식이면 기존 경매 정산 구현을 고칠 필요는 기본적으로 없음. 경매도 새로운 grouping으로 바꿀 때는 house/date 키·조회·initializer·보존 line까지 변경 |
| 신규/조건부 | 새 HTTP endpoint 또는 새 입력 adapter, [PaymentController.java](../backend/src/main/java/com/greenhouse/backend/settlement/controller/PaymentController.java), [PartnerSettlementSettingsController.java](../backend/src/main/java/com/greenhouse/backend/settlement/controller/PartnerSettlementSettingsController.java) | 새 정산 생성/조회/입금 계약의 전달 지점. 기존 confirmAuctionPayment 경로를 다른 대상에 그대로 재사용할 수는 없음 |

주요 영향 module은 **settlement, sales, partner**. **audit**와 **analytics**는 사건·잔액 해석을 재검증한다. Farm 재고/Work를 변경할 필요는 없으며 새 정산 생성 자체가 출고를 자동 수행한다고 가정하지 않았다.

### 수정 test file

- **신규 테스트 필요**: 일괄 정산 생성·전표 연결·금액 보존·입금 분배·상태·취소·재생성, 동시 생성/입금·동일 전표 중복 연결의 PostgreSQL 검증. 현재 전용 유스케이스가 없으므로 기존 enum 테스트 확장만으로 충분하지 않다.
- **수정/추가**: [PaymentTests.java](../backend/src/test/java/com/greenhouse/backend/PaymentTests.java), [PaymentLedgerContractIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/settlement/application/PaymentLedgerContractIntegrationTest.java), [SalesSlipActionResolverTest.java](../backend/src/test/java/com/greenhouse/backend/sales/application/SalesSlipActionResolverTest.java), [SalesSlipStatusRulesTest.java](../backend/src/test/java/com/greenhouse/backend/sales/domain/SalesSlipStatusRulesTest.java), [SalesSlipAuditIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/SalesSlipAuditIntegrationTest.java). 개별/일괄 입금 충돌·전표 변경 금지·target별 event·감사 사례.
- 납기 방식도 바꾸면 [ExpectedPaymentDateCalculatorTest.java](../backend/src/test/java/com/greenhouse/backend/settlement/application/ExpectedPaymentDateCalculatorTest.java), [PartnerSettlementPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/PartnerSettlementPostgresE2ETest.java)에 마감일·월/연 경계·설정 변경 전후 사례.
- **회귀 재실행**: [AuctionSettlementTests.java](../backend/src/test/java/com/greenhouse/backend/AuctionSettlementTests.java), [AuctionSettlementPaymentStateTest.java](../backend/src/test/java/com/greenhouse/backend/settlement/domain/AuctionSettlementPaymentStateTest.java), [AuctionSettlementClockIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/settlement/application/AuctionSettlementClockIntegrationTest.java), [SettlementPartnerQueryTest.java](../backend/src/test/java/com/greenhouse/backend/SettlementPartnerQueryTest.java), [SalesAnalyticsPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAnalyticsPostgresE2ETest.java). 직접 판매와 경매의 paid/remaining·거래처 채권을 혼합 집계하지 않는지 확인.

### switch/registry · DTO · DB · API/OpenAPI

| 항목 | 영향 |
| --- | --- |
| switch/registry | 현재 Settlement 방식 dispatch registry 없음. `SettlementUnit` enum 추가는 실행 연결이 아님. 새 target enum 전파, SALES_SLIP 전용 event 존재 여부 조건, 전표 payment target rejection/action을 확인. 납기 정책 변경이면 `calculateExpectedPaymentDate`의 mode 분기·요일 switch 조건부 변경 |
| DTO | 새 정산 command/result/list/detail, 추가 방식 parameter와 capability 필요. 단순 enum 값은 기존 settings DTO에 전파. event의 target enum도 API 계약 변경 |
| DB migration | **실제 일괄 정산은 신규 정산/전표 연결의 영속 구조와 중복 방지·금액·상태 제약이 필요**. 동시 갱신 제어·ID 생성 방식은 새 aggregate 설계에 따라 migration에 반영. 기존 입금/event는 보존. 기존 전표를 신규 방식에 편입할 때 paid/remaining·채권·snapshot 전환 규칙 필요. enum 값 저장만이면 현재 varchar 컬럼의 native enum 변경은 없음 |
| API/OpenAPI | settings는 [partner slice](../docs/api/slices/partner.openapi.yaml), 입금/event는 [payment slice](../docs/api/slices/payment.openapi.yaml), 전표 action은 [sales slice](../docs/api/slices/sales.openapi.yaml). 새 controller tag면 [API_INDEX](../docs/api/API_INDEX.md)와 [split_openapi_slices.py](../scripts/split_openapi_slices.py)의 `DOMAIN_MAP`도 확인 |

### 중복 규칙 수정 지점과 regression 위험

- 전표 입금과 경매 입금은 target 검증·금액 반영·잔액 갱신·감사·응답을 각 service에서 조정한다. ledger 기록 자체는 공통이다. 새 정산에 같은 조정 코드를 복사하면 대상별 정책 차이와 중복 금액 반영을 다시 관리해야 한다.
- `PaymentTargetType.SALES_SLIP` 조회는 action, 편집 제한, 취소 제한에 각각 있다. 새 일괄 target에 연결된 원본 전표를 어떻게 인지할지 각 지점 확인이 필요하다.
- 새 aggregate와 원본 SalesSlip의 미수금을 동시에 잔액에 더하면 이중 채권이 된다. 입금 분배, 부분입금, 재계산, 취소 후 이력 보존을 하나의 의미로 정의해야 한다.
- 기존 `MONTHLY_BATCH/ruleJson/autoSettleEnabled`는 실행 확장점이라고 확인되지 않았다. 값을 추가/설정하는 것만으로 실제 정산이 수행되지 않는다.
- 경매 grouping까지 변경하면 `auction_house_id + auction_date` UNIQUE와 initializer의 result 연결·기존 line 보존까지 영향을 받는다. 기존 결과 snapshot을 현재 값으로 재계산하는 변경은 별도 위험이다.

**더 작은 변경**: 납기 계산 방식만 추가하는 요구라면 [PaymentDayMode.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PaymentDayMode.java), [PartnerSettlementSettings.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerSettlementSettings.java) 계산/추가 parameter, settings DTO, 날짜 계산 테스트로 좁힐 수 있다. `paymentMethod` 문구만 추가하는 요구는 현재 string 저장이며 정산 dispatch를 추가하는 변경과 다르다.

**변경 용이성 판정**: 입금 ledger·거래처 잠금·감사는 재사용 가능하지만, 새 정산 업무 모델은 아직 확장 가능한 strategy 한 개를 등록하는 형태가 아니다. 설정 계약의 유연성과 실제 유스케이스의 구현 범위를 구분해야 한다.

## 7. F — HTTP가 아닌 AI Agent 입력 채널

### 실제 재사용 가능한 호출

예를 들어 검증된 [WorkCorrectionCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkCorrectionCommand.java)로 `WorkOperationCorrectionService.create`, [StructureChangeCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeCommand.java)로 `StructureChangeExecutionService.execute`, [ManualPaymentCommand.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/ManualPaymentCommand.java)로 `SalesPaymentService.confirmPayment`, 기존 요청으로 `WorkOperationPlanService.createBatch`를 호출할 수 있다. 해당 public application service는 Spring 트랜잭션을 소유하고 HttpServletRequest를 인자로 요구하지 않는다.

반면 `WorkOperationPlanService` 등 일부 application은 `work.dto.*`의 HTTP 요청/응답 형식도 그대로 사용한다. DTO 이름/`@Schema`만으로 HTTP 호출이 강제되지는 않지만 새 채널이 해당 계약의 검증·기본값·응답 해석까지 알아야 한다. 모든 DTO를 새 Agent DTO로 복제해야 하는 구조라고 단정하지 않는다.

### 수정 production file / 영향 module

| 구분 | 실제 파일·메서드 또는 신규 책임 | 영향 |
| --- | --- | --- |
| 신규 | Agent 입력 adapter와 작업별 입력 schema/허용 use case 목록·결과/오류 변환 — 위치·파일명 미정 | 자연어/도구 입력을 기존 typed 요청으로 변환하고, Bean Validation 및 인가 후 Spring Bean을 호출. application 중간 service/Repository를 임의 호출하는 형태와 구분 |
| 확인 | [WorkOperationPlanService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java), [StructureChangeExecutionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeExecutionService.java), [WorkOperationCorrectionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java), [SalesPaymentService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java), [SalesSlipCreationService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java) | 기존 public 진입점 재사용. 기존 필드·업무 의미를 그대로 호출하면 이들의 본문 수정은 기본적으로 불필요 |
| 확인/필요 책임 | [SecurityConfig.java](../backend/src/main/java/com/greenhouse/backend/auth/SecurityConfig.java) `securityFilterChain`, [AuthRole.java](../backend/src/main/java/com/greenhouse/backend/auth/AuthRole.java), [AuthService.java](../backend/src/main/java/com/greenhouse/backend/auth/AuthService.java) | 현재 인가는 HTTP 요청 matcher에 있다. Work type 관리 ADMIN 등 기존 권한 의미를 새 채널의 인증 주체/호출 허용 규칙으로 적용해야 함. SecurityFilterChain 본문을 고치는 것만으로 비 HTTP 인가가 생기지 않음 |
| 수정 필요 조건 | [AuditRequestContext.java](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditRequestContext.java) `current` | 신뢰된 Agent 주체를 기존 감사 actor/request identity에 기록해야 한다면 context 공급 방식 확장 필요. servlet context가 없으면 actor/session/client 값은 null이고 MDC request ID만 보존 |
| 확인/조건부 | [AuditEventWriter.java](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditEventWriter.java), [AuditEvent.java](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditEvent.java), [JpaAuditRecorder.java](../backend/src/main/java/com/greenhouse/backend/audit/application/JpaAuditRecorder.java), [AuditEventEntity.java](../backend/src/main/java/com/greenhouse/backend/audit/domain/AuditEventEntity.java), [RequestIdFilter.java](../backend/src/main/java/com/greenhouse/backend/audit/application/RequestIdFilter.java) | 현재 event Identity·기존 저장 컬럼으로 표현 가능하면 recorder/schema 수정 불필요. channel/agent tool ID를 별도 영속 필드로 요구하면 DTO·DB까지 변경. RequestIdFilter는 HTTP에만 적용 |
| 확인/조건부 | [RequestActorProvider.java](../backend/src/main/java/com/greenhouse/backend/common/application/RequestActorProvider.java), [WorkOperationSupport.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationSupport.java) `actor`, [PaymentLedgerService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java) | requested worker를 trim하고 demo에서는 강제 이름을 사용한다. 인증된 actor의 대체 공급자가 아니다. 업무 worker와 감사 authenticated actor를 동일하다고 가정하면 안 됨 |
| 확인/조건부 | [WorkCommandReceipts.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java), [WorkRequestFingerprint.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkRequestFingerprint.java), [OrchidGroupMutationSources.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationSources.java), [SalesSlipCreationService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java) | 멱등 지원 유스케이스는 기존 key를 사용. plan/create 등 멱등 키가 없는 진입점을 Agent가 재시도하려면 별도 dedup 의미 필요. 모든 use case에 receipt가 있다고 가정하지 않음 |
| 조건부 | [ModularArchitectureTests.java](../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java), [ModuleBoundaryInventoryTest.java](../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java), 새 모듈의 `package-info.java` | 새 top-level 입력 모듈로 추가하면 module 목록/허용 의존/계층 검사도 영향. 기존 모듈 안에 두면 그 모듈의 현재 허용 의존을 확인. 이 보고서에서 새 모듈 위치를 확정하지 않음 |

신규 입력 adapter와 **common/audit/auth**의 context·검증·인가가 주요 추가 범위다. 호출하는 **work/farm/sales/settlement**는 값 계약과 기존 트랜잭션을 재사용한다. domain 기능을 새로 구현하는 변경으로 확장할 필요는 없다.

### 수정 test file

- **신규 채널 테스트 필요**: Spring proxy를 통한 호출, nested Bean Validation, 인증/권한 거부, 신뢰된 actor와 worker 분리, 오류 변환, key 재사용/중복 입력, 감사 correlation, async context 정리. 파일명은 adapter 위치가 결정된 뒤 정한다.
- **수정/추가**: [AuditEventWriterTest.java](../backend/src/test/java/com/greenhouse/backend/audit/application/AuditEventWriterTest.java)와 신규 AuditRequestContext 비 servlet context 테스트. [RequestActorProviderTest.java](../backend/src/test/java/com/greenhouse/backend/common/application/RequestActorProviderTest.java)는 worker 정규화/demo 강제값을 이미 명시하므로 기존 의미를 보존하고 별도의 감사 identity 사례를 추가.
- **회귀 재실행**: [AuthIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/AuthIntegrationTests.java), [CustomAccountAuthIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/CustomAccountAuthIntegrationTest.java), [WorkCorrectionAuditPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionAuditPostgresE2ETest.java), [OrchidGroupAuditRollbackIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/OrchidGroupAuditRollbackIntegrationTest.java), [PaymentLedgerContractIntegrationTest.java](../backend/src/test/java/com/greenhouse/backend/settlement/application/PaymentLedgerContractIntegrationTest.java), [WorkIdempotencyPostgresE2ETest.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkIdempotencyPostgresE2ETest.java). HTTP 행동을 유지하면서 non-HTTP 호출도 같은 DB 결과/rollback을 내는지 비교.
- 신규 모듈이면 [ModularArchitectureTests.java](../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java), [ModuleBoundaryInventoryTest.java](../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java) 수정/재실행. 권한·감사 context 변경이 없다면 모든 기존 업무 테스트의 본문 수정은 필요하지 않다.

### switch/registry · DTO · DB · API/OpenAPI

| 항목 | 영향 |
| --- | --- |
| switch/registry | Work effect/structure registry 변경 불필요. 새 채널의 공개 tool→허용 application use case 연결은 신규 계약. 기존 비공개 helper나 handler를 공개 tool로 직접 매핑할 필요는 없음 |
| DTO | 기존 application command를 재사용 가능. Bean Validation annotation이 있다고 직접 호출 때 자동 검증되는 것은 아님. Agent의 자연어/임의 Map 입력은 typed 계약으로 해석. 다른 의미/인증·correlation 정보만 새 adapter 계약에 추가 |
| DB migration | 기존 use case·기존 감사 컬럼만 쓰면 불필요. channel/Agent ID/new dedup receipt를 영속화하거나 현재 감사 source enum에 새 값을 넣을 경우 저장/배포 호환성 검토. 새 enum string만 저장하는 것과 컬럼/테이블 추가는 구분 |
| API/OpenAPI | 기존 HTTP 입력/응답을 바꾸지 않으면 OpenAPI 변경 없음. Agent tool schema는 별도 채널 계약이며 Controller가 없는데 HTTP slice에 endpoint를 만들어 넣을 이유는 없음. 공용 command/capability를 변경하면 기존 slice에도 전파 |

### 중복 규칙 수정 지점과 regression 위험

- Controller의 `@Valid`를 건너뛴다. 대상 application service에는 `@Validated` 기반의 동일 검증 진입을 확인하지 못했다. 예: 빈 results/null payment는 HTTP에서는 validation 오류지만 직접 호출에서는 내부 인덱싱/NPE 등 다른 결과가 가능하다. 새 채널은 기존 annotation의 nested validation을 재사용해야 하며 업무 규칙을 별도로 복제할 필요는 없다.
- `SecurityConfig`는 `@EnableMethodSecurity`를 사용하지만 조사한 업무 service에는 `@PreAuthorize/@Secured/@RolesAllowed`가 없다. HTTP 인가를 통과했다는 전제가 새 입력에는 없으므로 동일 권한을 별도로 연결해야 한다.
- [AuditRequestContext.java](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditRequestContext.java)는 servlet attributes를 먼저 확인하므로 SecurityContext만 설정해도 non-HTTP 감사 actor가 자동 채워지지 않는다. 입력 worker는 감사 actor가 아니다. Agent가 보낸 이름을 인증 주체로 신뢰하는 방식은 기존 인증 의미와 달라진다.
- Spring Bean을 수동 `new`로 만들거나 내부 helper를 호출하면 transaction proxy를 우회할 수 있다. `WorkCommandReceipts/PaymentLedgerService/JpaAuditRecorder`의 MANDATORY 경계도 영향을 받는다. 공개 최상위 유스케이스를 DI된 Bean으로 호출하는 흐름을 검증해야 한다.
- Agent 재시도/timeout은 업무 실행 실패와 다르다. receipt가 있는 보정/실행/입금과 키가 없는 일반 계획·판매 전표 생성은 중복 입력 위험이 다르다. `OrchidGroupMutationSources.farmRequest` 같은 새 source 생성도 입력 채널 dedup을 자동 제공하지 않는다.
- Agent 추론/외부 네트워크 호출을 기존 DB 트랜잭션 안에 넣으면 lock 유지 시간이 새로 늘어난다. 추론·입력 확정과 application DB 실행의 단계가 분리되어야 기존 유스케이스의 비용을 유지할 수 있다.

**변경 용이성 판정**: 기존 업무 실행의 재사용 범위는 넓다. 채널별 입력/신뢰/context/재시도 계약을 함께 연결해야 HTTP와 동등한 호출이 된다. 새로운 wrapper를 모든 service 앞에 추가하는 작업으로 단정하지 않는다.

## 8. 공통 검증 및 문서 영향

| 시나리오 | 실제 구현 시 우선 검증 | 관련 기준 문서 |
| --- | --- | --- |
| A | 코드/template/handler/strategy 대응 → capability/기동 검사 → 새 유형의 실행·계보·취소·replay | Work 기능 문서, domain model, 실제 계약이 바뀐 API slice |
| B | 문자열 저장 → policy 분류 → active/derived/work target 조회 → 예약 write → 감사/집계 | domain model, DOMAIN_RULES. 새 값만 추가하면 자동 OpenAPI enum 변경이라고 보지 않음 |
| C | 허용 조건 → 생성/편집/완료/취소 대칭 → 기존 전표 → 병렬 예약/출하/rollback | sales-auction-settlement, domain model, 관련 API 동작/계약 |
| D | 전체 값 전달/상속 → 저장 snapshot/현재 값 → legacy JSON/hash → 취소/복구 → 실제 PostgreSQL backfill/fence | domain model, Work 구조 변경 정책, 변경된 API schema |
| E | 방식의 실제 grouping/분배/잔액 의미 → 기존 입금 보존 → 중복 연결/병렬 입금 → 통계 | sales-auction-settlement, domain model, 후속 범위/API_GAP_ANALYSIS |
| F | validation/인가/identity → DI proxy transaction → 재시도 → DB/audit parity | 채널 구현 기준·인증/감사 정책. HTTP/API 사용 방식이 실제로 바뀔 때만 API 가이드 |

실제 구현 시 Controller/계약/테스트를 먼저 바꾸고, schema 변경이 있을 때 [generate_openapi.py](../scripts/generate_openapi.py)로 전체·slice를 재생성한다. 생성 TypeScript 계약에도 전파되며 `frontend`의 `npm run api:types`/계약 drift 검증이 필요하다. frontend 화면 구현은 이번 backend 영향 조사 범위에 포함하지 않았다.

DB·트랜잭션·동시성·수량·정산이 실제로 바뀌는 A/C/D/E 및 F의 해당 경로는 관련 PostgreSQL E2E가 필요하다. 범위별 집중 검증 뒤 전체 backend test를 한 번 수행하며, 문서 작성 단계에서는 가상 정책을 구현한 것처럼 테스트 성공을 주장하지 않는다.

## 9. 조사 한계와 변경 기록

- 모든 시나리오의 production/test **참조 파일과 메서드, switch/registry, DTO, migration 필요 조건, API 영향, 중복 지점, 회귀 위험**을 정적 추적으로 기록했다. 정확한 수정 파일 수·공수는 정책이 확정되지 않아 산정하지 않았다.
- A의 새 효과 규칙, B의 전이/표시 의미, C의 기존 예약 처리, D의 상속/default/복원, E의 입금 분배/전표 연결, F의 Agent 신뢰/운영 방식은 실제 구현 전에 결정해야 한다. 영향이 달라지는 부분은 조건부로 남겼으며 현재 제공된 요구사항을 미완성 구현으로 바꾸지 않았다.
- 이번 작업에서는 **production/test 코드, DB, API/OpenAPI, 기존 audit 문서를 수정하지 않았다. 테스트와 애플리케이션도 실행하지 않았다.** 새 파일은 이 평가 문서뿐이다.
