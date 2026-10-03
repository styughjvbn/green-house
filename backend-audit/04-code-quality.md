# Backend 코드 품질·유지보수성 평가

평가일: 2026-10-03. 기준 commit: `08b50dc7bc4be84aa4fec2688a47a027f8938e80`.

`01-system-map.md`를 조사 출발점으로 사용하고 실제 Java 구현과 테스트를 대조했다. 모듈 소유권·의존 방향·공개 API 경계에 대한 판정은 `02-architecture.md`의 범위이며, 여기서는 한 기능을 이해하고 변경하는 데 필요한 추적·변환·검증 비용만 평가한다. 코드와 기존 문서는 수정하지 않았다.

## 1. 범위와 판단 기준

- `backend/src/main/java` 전체 파일·참조를 조사하고 farm/work의 생성·실행·보정·취소, sales/auction/settlement의 출하·입금, analytics의 조회 경로를 상세 추적했다. 인증·공통 예외·설정·운영 도구와 테스트도 책임·이름·호환 코드 관점에서 확인했다.
- 클래스 길이, 주입 필드 수, 파라미터 수는 조사 후보를 찾는 지표다. 수치만으로 결함을 판정하지 않았다. 책임이 연결되어 있는지, 상태를 몇 개 동시에 기억해야 하는지, 필드나 정책 하나를 바꿀 때 어디를 함께 확인해야 하는지로 판단했다.
- **Medium**: 일상적인 기능 변경에서 여러 표현·단계의 동시 이해가 필요하거나, 변경 누락·의미 오해를 만들 수 있는 유지보수 비용. **Low**: 국소적 중복·불필요한 의존·관례 차이로 범위가 제한된 비용. **High/Critical**: 이 평가에서는 확인하지 않았다.
- 아래 항목은 실행 장애가 입증된 버그 목록이 아니다. 성능·쿼리 수·전체 회귀 안정성도 별도 측정 없이 단정하지 않는다. dead code는 저장소 내 실제 참조가 없는 경우로 범위를 제한한다.

## 2. 주요 유스케이스별 파일 추적 비용

### 집계 방법

아래 수치는 **이번 조사에서 실제로 따라간 핵심 구현 파일과 데이터 계약 파일의 고유 개수**다. 같은 파일 안의 nested record·메서드는 추가로 세지 않는다. Controller 진입, Repository/Entity 내부, enum, 공통 Clock/예외/감사 저장소, 모든 보조 메서드의 전이적 의존까지 합친 총수는 아니다. 따라서 전체 이해 비용의 하한이며, 수정해야 하는 파일 수나 품질 점수가 아니다. 구현과 계약을 분리해 DTO가 많은 경우와 업무 단계가 많은 경우를 구분했다.

원장 쓰기를 깊게 확인하는 경로에는 다음 공통 구현 **W(4개 파일)**를 포함했다. 각 유스케이스 안에서는 한 번만 센다.

- [OrchidGroupMutationEngine.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java): `create`, `consumeReservation`, `transform`, `correct`, `compensateTransforms` 등 해당 쓰기의 검증·잠금·원장 처리.
- [OrchidGroupMutationCommandFingerprint.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationCommandFingerprint.java): 쓰기 명령 fingerprint 구성.
- [OrchidGroupMutationReplayResolver.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationReplayResolver.java): 멱등 재실행 확인.
- [OrchidGroupMutationRecorder.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationRecorder.java): mutation 및 entry 기록.

| 유스케이스 | 핵심 구현 | 추가 계약 | 추적 파일 합계 | 추적 비용의 주된 원인 |
| --- | ---: | ---: | ---: | --- |
| U1 난 묶음 직접 등록 | 7 | 4 | 11 | 배치 검증·원장·감사·응답. 단계의 의미가 비교적 분명함 |
| U2 경매 판매 전표 출하 완료 | 13 | 4 | 17 | 출하 snapshot, 재고 차감, 경매 lot 연결을 함께 이해해야 함 |
| U3 구조 변경 즉시 기록: REPOT 단건 기준 | 24 | 11 | 35 | 계획→시작→실행→효과 저장→조회 응답; 전략·결과 변환도 추적 |
| U4 미계획 입고의 즉시 포트 실행 | 20 | 8 | 28 | 새 계획·대상 생성, 작업 진행, 입고 반영, 중간 응답 조립 |
| U5 구조 변경 결과 수량·상태 보정 | 17 | 6 | 23 | 원본 참조·후속 사용 검사·보정 원장·작업일·이력 응답 |
| U6 완료된 구조 변경 작업 취소 | 16 | 6 | 22 | 취소 가능 판정·후속 사용 검사·복원·연관 작업·최종 응답 |
| U7 작업 그래프 MUTATION/LINEAGE 조회 | 3 | 4 | 7 | 그래프 탐색과 표현 조립; 상태·노드 종류·상한을 함께 추적 |
| U8 작업 실행 상세 조회 | 4 | 3 | 7 | 효과 JSON 해석·현재 참조 정보·상세 DTO 조립 |
| U9 판매 분석 조회 | 6 | 1 | 7 | 기간 계산·집계·표시 응답의 역할이 비교적 분명함 |
| U10 직접 판매 전표 수동 입금 | 7 | 3 | 10 | 멱등 입금·채권 잔액·감사·전표 응답 |

### U1. 난 묶음 직접 등록

`OrchidGroupCommandService.create → OrchidGroupMutationEngine.create → 배치 검증/원장 기록 → OrchidGroupAuditSupport.record → OrchidGroupResponse.from`.

핵심 구현: [OrchidGroupCommandService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java), [OrchidPlacementPolicy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/OrchidPlacementPolicy.java), [OrchidGroupAuditSupport.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupport.java) + **W(4)**.

추가 계약: [OrchidGroupCreateRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupCreateRequest.java), [CreateOrchidGroupMutationCommand.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/CreateOrchidGroupMutationCommand.java), [OrchidGroupMutationDetails.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationDetails.java), [OrchidGroupResponse.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/orchid/OrchidGroupResponse.java).

입력→원장 명령→응답의 변환은 서로 다른 역할을 갖는다. 이 경로만으로 DTO 수가 과도하다고 판단하지 않는다.

### U2. 경매 판매 전표 출하 완료

`SalesSlipStatusService.updateStatus → SalesSlipOutboundService.complete → allocation snapshot → createAuctionShipment → SalesSlipInventoryService.outbound → mutationEngine.consumeReservation → 전표 응답`.

핵심 구현: [SalesSlipStatusService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipStatusService.java), [SalesSlipOutboundService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipOutboundService.java), [SalesSlipAllocationBatch.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationBatch.java), [OrchidGroupReader.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java), [SalesSlipInventoryService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java), [AuctionShipmentCreator.java](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentCreator.java), [SalesSlipAuditSupport.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAuditSupport.java), [SalesSlipDocumentAssembler.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipDocumentAssembler.java), [SalesSlipActionResolver.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipActionResolver.java) + **W(4)**.

추가 계약: [ConsumeOrchidGroupReservationsMutationCommand.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/ConsumeOrchidGroupReservationsMutationCommand.java), [OrchidGroupState.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupState.java), [SalesOrchidGroupSnapshotData.java](../backend/src/main/java/com/greenhouse/backend/sales/application/document/SalesOrchidGroupSnapshotData.java), [SalesSlipDocument.java](../backend/src/main/java/com/greenhouse/backend/sales/application/document/SalesSlipDocument.java).

원장·snapshot·lot 연결을 생략하면 과거 출하와 현재 재고의 의미가 달라진다. 파일 수가 많아도 업무상 필요한 단계다. `SalesSlipAllocationBatch`는 ID·수량 집계와 snapshot을 재사용하는 실질적인 역할이 있다.

### U3. 구조 변경 즉시 기록

`StructureChangeRecordService.createStructureChangeRecord → WorkOperationPlanService.create → WorkOperationProgressService.start → StructureChangeExecutionService.execute → WorkEffectProcessor → RepotWorkHandler → StructureChangeExecutor → 전략 선택 → BatchStructureTransformationExecutor.execute → mutationEngine.transform → WorkEffectStore → queryService.get`.

핵심 구현: [StructureChangeRecordService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java), [WorkCommandReceipts.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java), [WorkOperationPlanService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java), [FarmWorkTargetResolver.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolver.java), [WorkOperationAggregateCreator.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationAggregateCreator.java), [WorkOperationProgressService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java), [StructureChangeExecutionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeExecutionService.java), [WorkOperationLockService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationLockService.java), [WorkEffectProcessor.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java), [RepotWorkHandler.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/RepotWorkHandler.java), [StructureChangeExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeExecutor.java), [StructureChangeStrategyRegistry.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistry.java), [RepotStrategy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/RepotStrategy.java), [BatchStructureTransformationExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java), [OrchidGroupLineageService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupLineageService.java), [WorkEffectStore.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java), [WorkOperationQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java), [WorkOperationResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationResponseAssembler.java), [WorkOperationActionResolver.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationActionResolver.java), [WorkOperationSupport.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationSupport.java) + **W(4)**.

추가 계약: [StructureChangeRecordCreateRequest.java](../backend/src/main/java/com/greenhouse/backend/work/dto/effect/StructureChangeRecordCreateRequest.java), [WorkOperationCreateRequest.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationCreateRequest.java), [WorkTargetSelection.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkTargetSelection.java), [ResolvedWorkTarget.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/ResolvedWorkTarget.java), [StructureChangeCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/StructureChangeCommand.java), [WorkEffectCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectCommand.java), [WorkEffectContext.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectContext.java), [WorkExecutionResult.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkExecutionResult.java), [TransformOrchidGroupsMutationCommand.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/TransformOrchidGroupsMutationCommand.java), [WorkEffectResults.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java), [WorkOperationView.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationView.java).

배치 등록은 같은 단건 경로를 반복하며 source 배치 제외 집합과 receipt를 관리한다. 분주·합식·이동에서는 해당 전략과 handler도 바뀐다. 위 수치는 REPOT 경로에 한정한다. 중간 단계마다 `WorkOperationView`를 만들어 ID·target·상태를 꺼내므로 쓰기 이해에 조회 조립까지 필요하다(CQ-001). 타입·속성 변환은 CQ-002/003과 연결된다.

### U4. 미계획 입고의 즉시 포트 실행

`InboundPottingOperationService.executeNow → receipt → executeNewPlan → InboundPottingPlanService.create → progressService.start → completeTarget → WorkEffectProcessor → InboundPottingExecutor → InboundPottingService.potting → mutationEngine.createFromInbound → 진행 완료/조회`.

핵심 구현: [InboundPottingOperationService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java), [WorkCommandReceipts.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java), [WorkOperationLockService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationLockService.java), [InboundPottingPlanService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingPlanService.java), [FarmInboundPottingPlanGateway.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/FarmInboundPottingPlanGateway.java), [WorkOperationAggregateCreator.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationAggregateCreator.java), [WorkOperationProgressService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java), [WorkEffectProcessor.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java), [InboundPottingExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingExecutor.java), [InboundPottingService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingService.java), [InboundRecordFinder.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordFinder.java), [InboundRecordResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordResponseAssembler.java), [WorkOperationQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java), [WorkOperationResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationResponseAssembler.java), [WorkOperationActionResolver.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationActionResolver.java), [WorkEffectStore.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java) + **W(4)**.

추가 계약: [InboundPottingCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/InboundPottingCommand.java), [InboundPottingPlanCreateRequest.java](../backend/src/main/java/com/greenhouse/backend/work/dto/effect/InboundPottingPlanCreateRequest.java), [WorkEffectCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectCommand.java), [InboundRecordPottingRequest.java](../backend/src/main/java/com/greenhouse/backend/farm/dto/inbound/InboundRecordPottingRequest.java), [InboundPottingResult.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingResult.java), [CreateInboundOrchidGroupsMutationCommand.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/CreateInboundOrchidGroupsMutationCommand.java), [WorkEffectResults.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java), [WorkOperationView.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationView.java).

이미 계획이 있는 경우에는 `executeActivePlan` 분기를 추가로 이해해야 한다. 새 계획 흐름은 typed 요청을 Map으로 바꿨다가 다른 요청 DTO로 복원한다. 입고 응답과 작업 응답도 중간에 생성한다(CQ-001/003).

### U5. 구조 변경 결과 보정

`WorkOperationCorrectionService.create → fingerprint/receipt → FarmWorkCorrectionAdapter.correct → StructureChangeReferenceReader → usageInspectors → correction 저장 → mutationEngine.correct → WorkOperationDateCorrectionService.correct → 보정 응답`.

핵심 구현: [WorkOperationCorrectionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java), [FarmWorkCorrectionAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java), [StructureChangeReferenceReader.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/StructureChangeReferenceReader.java), [WorkOperationDateCorrectionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationDateCorrectionService.java), [FarmOrchidGroupUsageInspector.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmOrchidGroupUsageInspector.java), [WorkOrchidGroupUsageAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/WorkOrchidGroupUsageAdapter.java), [WorkOrchidGroupUsageInspector.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkOrchidGroupUsageInspector.java), [SalesOrchidGroupUsageInspector.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupUsageInspector.java), [WorkOperationQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java), [WorkOperationResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationResponseAssembler.java), [WorkOperationActionResolver.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationActionResolver.java), [WorkRequestFingerprint.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkRequestFingerprint.java), [WorkOperationSupport.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationSupport.java) + **W(4)**.

추가 계약: [WorkCorrectionCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkCorrectionCommand.java), [OrchidGroupCorrectionInput.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/OrchidGroupCorrectionInput.java), [CorrectOrchidGroupsMutationCommand.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/CorrectOrchidGroupsMutationCommand.java), [WorkExecutionResult.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkExecutionResult.java), [WorkEffectResults.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java), [WorkOperationCorrectionsResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/correction/WorkOperationCorrectionsResponse.java).

사용 검사·before/after·작업일·결과 생성 취소는 서로 다른 판단이다. 보정 ID 생성 이후 mutation을 기록하고 결과를 연결하는 순서도 의미가 있다. 전체를 한 클래스에 합치는 방식은 이해 비용을 줄인다고 보장할 수 없다. JSON 결과 해석과 최종 응답의 간접 추적 비용은 남는다.

### U6. 완료된 구조 변경 작업 취소

`WorkOperationVoidService.cancelOperation → inspectCancellation → FarmStructureChangeVoidAdapter.inspect/inspectForUpdate → usageInspectors/effective head → compensate → mutationEngine.compensateTransforms → 작업 취소/연관 폐기 반영 → 조회`.

핵심 구현: [WorkOperationVoidService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java), [WorkOperationLockService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationLockService.java), [FarmStructureChangeVoidAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmStructureChangeVoidAdapter.java), [OrchidGroupMutationEffectiveHeadPolicy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEffectiveHeadPolicy.java), [OrchidPlacementPolicy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/OrchidPlacementPolicy.java), [FarmOrchidGroupUsageInspector.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmOrchidGroupUsageInspector.java), [WorkOrchidGroupUsageAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/WorkOrchidGroupUsageAdapter.java), [WorkOrchidGroupUsageInspector.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkOrchidGroupUsageInspector.java), [SalesOrchidGroupUsageInspector.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupUsageInspector.java), [WorkOperationQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java), [WorkOperationResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationResponseAssembler.java), [WorkOperationActionResolver.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationActionResolver.java) + **W(4)**.

추가 계약: [StructureChangeVoidPort.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeVoidPort.java), [WorkOperationCancellationRequest.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationCancellationRequest.java), [WorkOperationCancellationEligibilityResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationCancellationEligibilityResponse.java), [CompensateTransformMutationsCommand.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/CompensateTransformMutationsCommand.java), [OrchidGroupMutationResult.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationResult.java), [WorkOperationView.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationView.java).

여기서는 구조 변경 분기만 센다. 입고 포트·기록 전용 취소는 다른 분기와 계약을 추가로 요구한다. 일반 조회 판정과 잠금 후 재판정을 단순 중복으로 제거하면 안 된다. 판단 단계와 응답 변환이 한 메서드 안에 섞인 비용은 CQ-004에 기록했다.

### U7. 그래프 조회

`WorkOperationGraphQueryService.get → relatedOperations/mutationIds → FarmWorkOperationMutationGraphAdapter.load/discover/assemble → WorkOperationRelationSummaryAssembler.assemble → addMutationFlow/노드 factory → graph response`.

핵심 구현: [WorkOperationGraphQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationGraphQueryService.java), [WorkOperationRelationSummaryAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationRelationSummaryAssembler.java), [FarmWorkOperationMutationGraphAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/FarmWorkOperationMutationGraphAdapter.java).

추가 계약: [WorkOperationMutationGraphPort.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationMutationGraphPort.java), [WorkOperationGraphNodeResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationGraphNodeResponse.java), [WorkOperationGraphEdgeResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationGraphEdgeResponse.java), [WorkOperationGraphResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationGraphResponse.java).

`WORK` 조회는 mutation fragment를 생략한다. 이 경로는 `WorkOperationDetailService`나 `WorkEffectDetailCodec`을 호출하지 않는다. 파일 수는 상대적으로 적지만, 여러 Map과 가변 nodes/edges, node 상한과 `truncated`를 함께 기억하는 지역적 인지 비용이 크다(CQ-005).

### U8. 작업 실행 상세 조회

`WorkOperationDetailService.get → WorkEffectDetailCodec.execution → WorkExecutionReferenceGateway/FarmWorkExecutionReferenceGateway → WorkOperationDetailAssembler → detail response`.

핵심 구현: [WorkOperationDetailService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailService.java), [WorkOperationDetailAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailAssembler.java), [WorkEffectDetailCodec.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkEffectDetailCodec.java), [FarmWorkExecutionReferenceGateway.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkExecutionReferenceGateway.java).

추가 계약: [WorkExecutionReferenceGateway.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkExecutionReferenceGateway.java), [WorkExecutionDetailResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkExecutionDetailResponse.java), [WorkOperationDetailResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationDetailResponse.java).

도메인 조회와 표시 조립 자체는 역할이 분명하다. 과거 JSON을 어떤 우선순위로 해석하는지까지 알아야 하는 부분이 CQ-003의 비용이다.

### U9. 판매 분석 조회

`AnalyticsQueryService.getSalesAnalytics → AnalyticsDateRange → SalesMetricsReader/FarmMetricsReader/BusinessPartnerReader → SalesAnalyticsResponseAssembler → SalesAnalyticsResponse`.

핵심 구현: [AnalyticsQueryService.java](../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java), [AnalyticsDateRange.java](../backend/src/main/java/com/greenhouse/backend/analytics/domain/AnalyticsDateRange.java), [SalesMetricsReader.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesMetricsReader.java), [FarmMetricsReader.java](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmMetricsReader.java), [BusinessPartnerReader.java](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerReader.java), [SalesAnalyticsResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/analytics/application/SalesAnalyticsResponseAssembler.java).

추가 계약: [SalesAnalyticsResponse.java](../backend/src/main/java/com/greenhouse/backend/analytics/dto/SalesAnalyticsResponse.java).

집계 데이터와 응답 조립 사이에 의미 없는 재포장이 연속하지 않는다. `AnalyticsQueryService`의 여러 Reader 의존은 조회 조합의 목적과 연결되어 있다. 클래스 전체 주입 수를 이 메서드의 책임 수로 간주하지 않았다.

### U10. 직접 판매 수동 입금

`SalesPaymentService.confirmPayment → PartnerBalanceService.lockPartners → PaymentLedgerService.findManualPayment → SalesSlip.recordPayment → recordManualPayment → updateReceivable → audit → 전표 응답`.

핵심 구현: [SalesPaymentService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java), [PaymentLedgerService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java), [PartnerBalanceService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerBalanceService.java), [BusinessPartnerLock.java](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerLock.java), [SettlementAuditSupport.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/SettlementAuditSupport.java), [SalesSlipDocumentAssembler.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipDocumentAssembler.java), [SalesSlipActionResolver.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipActionResolver.java).

추가 계약: [ManualPaymentCommand.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/ManualPaymentCommand.java), [SalesSlipDocument.java](../backend/src/main/java/com/greenhouse/backend/sales/application/document/SalesSlipDocument.java), [BusinessPartnerInfo.java](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerInfo.java).

입금·잔액·감사를 따로 읽어야 하지만 각각 다른 상태와 이력을 설명한다. 멱등 키 재사용 오류의 관례 차이는 CQ-009에서 다룬다. 이 수치는 입금 경로의 핵심 파일이며, 공통 전표 응답의 모든 하위 조회까지 확장한 수는 아니다.

## 3. 확인한 유지보수 문제

### CQ-001 — 쓰기 흐름에서 중간 조회 응답을 다시 업무 입력으로 사용

**Severity: Medium**

**근거 코드**

- [StructureChangeRecordService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java) `createStructureChangeRecord` L49, `createStructureChangeRecords` L79, `createInboundPottingRecord` L114.
- [WorkOperationPlanService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java) `create`, [WorkOperationProgressService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java) `start/completeTarget/complete`와 [WorkOperationQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java) `get/getAll`, [WorkOperationResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationResponseAssembler.java) `assemble/assembleAll`.
- [InboundPottingOperationService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java) `executeNewPlan` L95, `executeTarget` L192.
- [InboundPottingService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingService.java) `potting` L33/L60와 [InboundPottingExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingExecutor.java) `execute` L34.

**현재 구조**: 구조 변경 기록은 계획 응답에서 target 수량을 추출하고, 실행 응답에서 완료 상태를 확인한다. 시작 응답은 버리기도 한다. 배치에서는 완료 응답에서 ID를 꺼낸 뒤 `getAll`로 다시 응답을 만든다. 포트 실행은 시작 응답에서 target ID, 완료 응답에서 progress를 읽는다. `InboundPottingService`는 `InboundRecordResponse`까지 조립해 반환하지만 해당 handler는 결과 묶음 ID·수량·mutation link를 사용한다.

**문제점·인지 비용**: 상태 전환의 결과를 이해하려면 action 계산·현재 입고 참조·화면 응답 조립도 따라가야 한다. 내부 계산에 필요한 정보와 외부 상세 응답이 같은 반환값에 묶여 있다. 동일 업무에 조회 계약의 변화가 영향을 미치는 이유를 호출자마다 확인해야 한다. 추가 조회 비용의 크기는 측정하지 않았다.

**실제 변경 시 영향**: 작업 응답에 새 파생 필드를 추가하거나 현재 참조 해석을 바꾸면, 외부 조회뿐 아니라 기록·즉시 실행 중간 단계도 영향을 받는다. 내부 반환값 변경은 Controller 최종 응답과 멱등 재실행 응답을 함께 확인해야 한다.

**개선 방향**: 동일 업무 상태 전환은 ID·target ID·진행 결과처럼 필요한 내부 결과를 반환하게 하고 최종 응답을 마지막에 조립하는 방향을 검토한다. 이를 위해 각 단계마다 새 DTO를 만들기보다 기존 aggregate/내부 결과를 재사용한다. 현재 검증·잠금·receipt 순서와 API 응답은 보존해야 한다.

### CQ-002 — 구조 변경 실행의 단계 혼합과 불필요한 생성 요청 DTO 경유

**Severity: Medium**

**근거 코드**: [BatchStructureTransformationExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java) `execute` L43–131, `planResults` L183, `mutationCommand` L202, `ResultPlan` L225.

**현재 구조**: 하나의 실행 메서드에서 source 잠금·품종 검증·수량 집계·동일성 유지 이동 판정·속성 상속·원장 실행·결과 재조회·lineage·효과 결과를 처리한다. `planResults`는 `OrchidGroupCreateRequest`를 만들고, `mutationCommand`가 그 필드를 다시 `OrchidGroupMutationDetails`로 복사한다.

**문제점·인지 비용**: 긴 메서드에서 실행 전 source 속성과 실행 후 quantity의 구분을 기억해야 한다. 결과 속성 하나를 바꾸려면 결과 입력, 속성 상속, 생성 요청, mutation details, 결과 audit row를 연결해서 읽어야 한다. 생성 요청 DTO를 중간에 만드는 단계에는 별도 요청 검증이나 API 호출이 없다. 이 복사는 새로운 업무 의미를 추가하지 않는다.

**실제 변경 시 영향**: 속성 상속이나 결과 purpose 변경은 `planResults`와 `mutationCommand`, 효과 결과까지 확인해야 한다. 순서를 바꾸면 원본 변형 전에 상속 속성을 확보한다는 현재 보장이 깨질 수 있다. 이동의 원본 ID 유지 분기도 보존해야 한다.

**개선 방향**: 기존 `ResultPlan`이 배치 위치·`OrchidGroupMutationDetails`·purpose를 직접 보유하도록 검토한다. 메서드는 원본 확보/검증, 실행 계획 작성, mutation 적용, 결과 기록으로 의미 있는 단계만 나눈다. 단계마다 별도 Service를 추가하거나 원장 Command 자체를 제거할 필요는 없다.

### CQ-003 — 효과 데이터의 타입 소실과 분산된 JSON 해석

**Severity: Medium**

**근거 코드**

- [WorkEffectCommand.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectCommand.java) record의 `Map<String, Object> resultDetails`, `Object payload`, `payloadAs`.
- [InboundPottingOperationService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java) `commandDetails` L204, [InboundPottingExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingExecutor.java) `execute` L39의 `convertValue`.
- [WorkEffectDetailCodec.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkEffectDetailCodec.java) `sources/results/resultIds` L40/L93/L130, `longValue` L183.
- [WorkOperationTargetView.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkOperationTargetView.java) `resultOrchidGroupIds` L57, [WorkEffectResults.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java) `sourceQuantities/resultQuantities`.
- [WorkOperationAggregateCreator.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationAggregateCreator.java)와 `WorkOperationTargetView`의 `inboundLocation`에서 같은 `tempLocation/pottingDueDate` Map 구성.

**현재 구조**: 포트 명령은 typed command→Map→요청 DTO로 변환된다. 구조 변경 handler는 `payloadAs`로 타입을 복원한다. 저장 효과·상세·target·lineage용 해석이 각각 JSON key를 알아야 한다. 상세 codec은 result ID 목록에 우선순위를 두고 fallback하며 숫자 문자열도 허용한다. target view는 여러 key를 합집합으로 모으고 `Number`만 받는다.

**문제점·인지 비용**: 효과 필드 하나를 바꿀 때 compiler가 모든 독자를 알려주지 않는다. key 이름뿐 아니라 reader별 fallback·순서·숫자 처리까지 함께 이해해야 한다. 같은 위치 snapshot Map을 두 곳에서 구성하는 작은 중복도 있다. 저장 JSON의 존재보다 업무 실행 중에도 타입을 잃는 구간과 분산된 해석이 문제다.

**실제 변경 시 영향**: 저장된 과거 효과, 요청 fingerprint, replay, 상세 응답, target 결과 ID, 수량 집계가 영향을 받는다. 현재 reader 간 차이가 곧 결함이라는 뜻은 아니다. 무조건 하나의 decoder로 합치면 legacy 해석과 결과 순서를 바꿀 수 있다.

**개선 방향**: 실행 중에는 이미 존재하는 typed payload를 활용하고 JSON 변환을 저장/호환 지점에 집중한다. 과거 형식별 해석과 현재 typed 결과를 구분해, 공통 key 접근·형식 처리를 한 곳에서 유지할 수 있는지 검토한다. 합집합/우선순위/숫자 문자열 허용 여부는 명시적 옵션이나 별도 정책으로 보존한다. 위치 Map은 작은 명명된 factory로 충분하며 범용 변환 프레임워크는 필요하지 않다.

### CQ-004 — 취소 서비스의 여러 판단·실행 모드가 한 지역 상태에 모임

**Severity: Medium**

**근거 코드**: [WorkOperationVoidService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java)의 10개 주입 필드, `inspectCancellation` L66–151, `cancelOperation` L225, `cancelBatch` L165. [OrchidGroupMutationEngine.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java) `compensateTransforms` L519 → private `compensate` L536의 복원 실행.

**현재 구조**: 조회 가능성 판정, 상태별 blocker 우선순위, 연관 폐기 수집, effect/mutation 집계, 기록 전용/포트/구조 변경 분기, lock 여부, 영향 응답 변환을 취소 검사 한 메서드에서 처리한다. 서비스 전체는 단건·배치·입고 등록 취소와 최종 조회도 담당한다.

**문제점·인지 비용**: 취소 조건 하나를 추가할 때 어느 분기에서 반환하는지, 먼저 추가한 blocker를 뒤에서 유지하는지, lock 모드에서 동일 판단인지, 영향 group의 의미가 무엇인지 동시에 확인해야 한다. 의존 10개 자체보다 서로 다른 취소 종류의 판정·표현·실행 조합이 비용의 근거다. 깊은 if만 있는 문제가 아니라 early return, lambda, lock ternary와 누적 상태가 섞여 있다.

**실제 변경 시 영향**: eligibility, 실제 취소, batch, 연관 폐기, potting 분기의 일치 여부를 확인해야 한다. 조회 시 검사와 잠금 후 재검사는 동시 변경을 방어하므로 중복으로 제거하면 안 된다. 원장 복원의 긴 메서드에도 잠금·replay·effective head·placement 복원 순서라는 의미가 있다.

**개선 방향**: 먼저 상태/종류 판정, 관련 효과 수집, 종류별 검사, 영향 응답 변환을 private 단계로 분리하고 작은 내부 검사 결과를 유지하는 방향을 검토한다. 별도 클래스 추출은 다른 호출자나 독립 정책이 있을 때만 한다. 일괄 취소와 단건 취소의 동작·잠금 순서를 바꾸는 정리는 별도 DB 검증이 필요하다.

### CQ-005 — 그래프 조립의 다중 가변 인자와 위치 기반 노드 생성

**Severity: Medium**

**근거 코드**: [WorkOperationGraphQueryService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationGraphQueryService.java) `get` L55, `addMutationFlow` L140의 8개 인자, `mutationNode/stateNode/originNode/operationNode` L213/L219/L264/L269. [WorkOperationGraphNodeResponse.java](../backend/src/main/java/com/greenhouse/backend/work/dto/operation/WorkOperationGraphNodeResponse.java)의 21개 record component.

**현재 구조**: `addMutationFlow`는 operations, targets, mutation 연결, fragment, nodes, edges와 선택 ID·상한을 받는다. nodes/edges를 변경하면서 `truncated`는 반환값으로 전달한다. 노드 종류별 factory에서 동일한 넓은 생성자에 많은 `null`을 위치 순서로 넣는다.

**문제점·인지 비용**: 입력 context와 출력 누적 상태가 섞여 있어 메서드 호출만 보고 변경되는 대상을 파악하기 어렵다. node의 같은 타입 필드나 `null` 위치가 틀려도 컴파일은 성공할 수 있다. 새 node 필드를 추가할 때 해당하지 않는 종류의 factory까지 확인해야 한다. 파일 수가 적어도 한 메서드에서 기억하는 상태는 많다.

**실제 변경 시 영향**: 노드 추가·그래프 상한 변경은 node 종류, edge 정합성, 순서, `truncated`, visible operation 관계를 함께 검토해야 한다. JSON schema를 바꾸는 것은 별도의 API 계약 변경이다.

**개선 방향**: 서비스 내부에서만 쓰는 조립 context/result로 관련 상태를 묶는 방안을 검토한다. 이미 있는 서비스의 node factory는 각각 넓은 생성자의 위치를 알아야 한다. DTO 가까이에 종류별 생성 함수나 필드 이름을 드러내는 builder를 두어 서비스의 위치 기반 인자 조립을 줄인다. 외부 DTO나 graph 탐색 알고리즘을 일반화하는 hierarchy를 추가할 필요는 없다.

### CQ-006 — 직접 판매의 공통 입력·거래처 정책 중복

**Severity: Low**

**근거 코드**: [SalesSlipCreationService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java) `create` L40–58, [SalesSlipUpdateService.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java) `update` L46–65와 `validateEditable` L103. [SalesSlipCommand.java](../backend/src/main/java/com/greenhouse/backend/sales/application/command/SalesSlipCommand.java) 및 [SalesType.java](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesType.java)의 기본값.

**현재 구조**: 직접 판매의 거래처 필수·품목 필수·경매장 거래처 사용 금지 조건과 메시지가 생성/수정에서 반복된다. 생성의 입금 상태 기본값은 `SalesType.defaultPaymentStatus`, 수정은 `"미입금"`을 사용한다. 수정은 직접 판매만 허용하고 경매 수정은 차단한다.

**문제점·인지 비용**: 직접 판매 정책·오류 메시지·기본값을 바꾸려면 두 경로가 같은 의도인지 다시 확인해야 한다. Bean Validation과 application 방어 검사는 진입점이 다르므로 전부 불필요한 중복은 아니다. 생성의 경매 분기와 수정 가능성 검증도 같은 조건이 아니다.

**실제 변경 시 영향**: 생성/수정 API 오류, 직접 판매 기본값, 기존 테스트 기대값. 생성과 수정을 무조건 합치면 경매 수정 금지나 품목 수 변경 금지가 흐려질 수 있다.

**개선 방향**: 실제로 같은 직접 판매 거래처·품목 정책만 작은 공통 검증 함수/정책으로 묶는다. 기본값도 의도적으로 같다면 `SalesType`에 모은다. 생성·수정 유스케이스와 각각의 변경 금지 검사는 유지한다.

### CQ-007 — 사용되지 않는 주입 의존과 동작하지 않는 전략 옵션

**Severity: Low**

**근거 코드**

- [ImmediateWorkExecutionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/ImmediateWorkExecutionService.java) L40의 `appliedEffectRepository`: 파일 안에서 선언 이외의 참조가 없다. 결과 ID 조회는 `effectOrchidGroupRepository`를 사용한다.
- [StructureChangeStrategy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategy.java) L20의 `requiresEverySourceResult`, [MovementStrategy.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/MovementStrategy.java) L29의 override. `backend`, `docs`, `scripts` 참조 검색에서는 두 선언만 발견했다.

**현재 구조**: Lombok 생성자에 사용하지 않는 Repository가 포함된다. 전략 interface는 원본별 결과 필수 여부를 설정하는 것처럼 보이지만 실행 코드는 그 메서드를 호출하지 않는다.

**문제점·인지 비용**: 독자는 이 Repository가 즉시 실행의 필수 협력자인지 조사해야 한다. 전략 옵션을 수정하면 정책이 달라진다고 오해할 수 있지만 실제 동작에는 영향이 없다. 이는 작은 규모라도 확인 가능한 불필요한 추적 비용이다.

**실제 변경 시 영향**: 주입 필드 제거 시 생성자 기반 단위 테스트/빈 구성 영향 확인. 전략 메서드 제거 또는 연결 시 실제 source/result 검증을 확인해야 한다. 외부 저장소의 호출까지 조사한 것은 아니다.

**개선 방향**: 사용하지 않는 주입은 정리 후보로 두고, 전략 옵션은 실제 정책을 설명하지 않는다면 제거한다. 연결하는 경우에는 정책 변경으로 취급한다. `allowsMixedVarieties`처럼 실제로 호출되는 확장점, Spring 등록 클래스, migration 코드는 unused로 판정하지 않았다.

### CQ-008 — 테스트의 응답 문자열 파싱과 긴 복합 시나리오

**Severity: Medium**

**근거 코드**

- [InboundPottingPlanIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/InboundPottingPlanIntegrationTests.java): L119/L159 등 응답 ID 정규식 추출, `createsAndReusesOneCompletedPottingRecordForMultipleInboundRecords` L751–847.
- [WorkOperationIntegrationTests.java](../backend/src/test/java/com/greenhouse/backend/WorkOperationIntegrationTests.java) L394/L416 등 같은 종류의 ID 문자열 추출.
- 비교 사례: [WorkE2ETestBase.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkE2ETestBase.java)의 JSON 기반 `ApiResult`, [WorkEffectResultsTest.java](../backend/src/test/java/com/greenhouse/backend/work/application/effect/WorkEffectResultsTest.java)의 작은 결과 계약 테스트, [FarmTestFixtures.java](../backend/src/test/java/com/greenhouse/backend/farm/support/FarmTestFixtures.java).

**현재 구조**: 일부 테스트는 `"data":{"id":...`의 인접·순서를 가정해 ID를 추출한다. 약 100줄의 포트 시나리오에서는 긴 raw JSON 준비 뒤 생성·동일 요청 재실행·작업 무효화·입고 취소를 차례로 검증한다. 다른 테스트에는 JSON tree와 목적별 fixture 지원이 이미 있다.

**문제점·인지 비용**: 응답 필드 순서나 공백이 달라지면 업무 의미와 무관하게 ID 추출이 깨질 수 있다. 긴 시나리오의 첫 준비·검증 실패는 뒤쪽 보존/취소 검증까지 도달하지 못하게 한다. 테스트 이름은 의도를 잘 설명하지만 필요한 수량·대상·상태를 긴 공통 payload에서 찾아야 한다.

**실제 변경 시 영향**: request DTO/응답 형식 변경 때 여러 JSON literal과 파서를 고쳐야 한다. fixture를 지나치게 숨기면 테스트의 핵심 수량·배치 조건이 보이지 않는 역효과도 있다.

**개선 방향**: response ID는 `ObjectMapper.readTree`/JSON path로 추출한다. 정상 요청의 반복 부분만 목적별 helper로 묶고 핵심 수량·위치·멱등 키는 테스트에 드러낸다. 생성·replay·취소의 독립 사례와 전체 lifecycle 사례를 구분한다. invalid payload 테스트와 과거 schema를 검증하는 SQL fixture를 일괄 builder로 치환할 필요는 없다.

### CQ-009 — 같은 종류의 멱등 충돌에 서로 다른 예외 관례

**Severity: Low**

**근거 코드**: [WorkCommandReceipt.java](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkCommandReceipt.java) `validate` L36, [WorkCorrectionReceipt.java](../backend/src/main/java/com/greenhouse/backend/work/domain/correction/WorkCorrectionReceipt.java) `validate` L31, [PartnerPaymentEvent.java](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerPaymentEvent.java) `validateReplay` L145, [GlobalExceptionHandler.java](../backend/src/main/java/com/greenhouse/backend/common/exception/GlobalExceptionHandler.java) `handleConflict` L31와 validation handler L48. [PaymentTests.java](../backend/src/test/java/com/greenhouse/backend/PaymentTests.java) L188/L270의 400 기대값.

**현재 구조**: 작업/보정에서 같은 키의 다른 요청은 `ConflictException("IDEMPOTENCY_KEY_REUSED", ...)`로 409를 반환한다. 수동 입금의 동일 키에 다른 금액/날짜는 `IllegalArgumentException`으로 400 `VALIDATION_ERROR`가 된다. 공통 HTTP 예외 변환은 한 handler에 모여 있다.

**문제점·인지 비용**: 새로운 멱등 유스케이스를 구현할 때 어떤 exception/status/code 관례를 따라야 하는지 인접 기능에서 한 가지 답을 얻기 어렵다. 일반 입력 오류와 replay 충돌의 세부 의미도 payment 경로에서는 같은 validation code로 묶인다.

**실제 변경 시 영향**: payment 테스트가 현재 400을 명시하므로 단순 내부 예외 교체도 API 동작 변경이다. 현재 payment 응답을 버그로 단정하지 않는다. 모든 `IllegalArgumentException`을 409로 바꾸면 실제 입력 오류까지 달라진다.

**개선 방향**: 입력 오류·현재 상태 충돌·멱등 키 충돌의 exception/code 선택 기준을 정하고 새 코드부터 일관되게 적용한다. 기존 payment 경로의 통일 여부는 API 호환성을 검토해 별도 결정한다. 불변식 손상에 대한 `IllegalStateException`은 사용자 검증 오류와 구분한다.

### CQ-010 — 이미 만든 명령을 사용하지 않고 긴 인자 목록을 재전달

**Severity: Low**

**근거 코드**: [ImmediateWorkExecutionService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/ImmediateWorkExecutionService.java) `executeForTarget` L54, `execute` L64, private `ImmediateCommand` L74, `executeNewForTarget` L79 및 `executeNew`.

**현재 구조**: 공개 실행 경로는 9개/8개 인자를 받아 fingerprint용 `ImmediateCommand`를 만든다. receipt callback에서는 같은 값을 다시 긴 인자 목록으로 private 실행 메서드에 넘긴다. actor도 공개 경로에서 정규화한 뒤 private 경로에서 다시 정규화한다.

**문제점·인지 비용**: 파라미터 개수보다 동일 업무 입력이 메서드 서명·record·callback·private 호출에 반복되는 것이 비용이다. 필드를 추가하거나 기본값을 바꾸면 같은 값이 모든 전달 지점에 반영됐는지 확인해야 한다. 여러 `String`의 위치 오류는 타입 검사로 걸러지지 않는다.

**실제 변경 시 영향**: 내부 인자 전달, fingerprint에 포함되는 값, actor 정규화 시점. 공개 메서드의 호출자 변경까지 필요할지는 별도 결정할 수 있다.

**개선 방향**: 이미 만든 `ImmediateCommand`를 private 실행 경로에서도 사용하고 request key만 별도로 전달하는 정도부터 검토한다. fingerprint 대상 필드와 정규화된 값은 보존한다. 이를 위해 추가 wrapper DTO나 범용 command framework를 도입할 필요는 없다.

## 4. 용어·책임·abstraction 판정

| 조사 항목 | 코드 관찰 | 판정 |
| --- | --- | --- |
| Reader | [OrchidGroupReader.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java) `getStates/lockStates`, [SalesMetricsReader.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesMetricsReader.java) 집계, [StructureChangeReferenceReader.java](../backend/src/main/java/com/greenhouse/backend/work/application/correction/StructureChangeReferenceReader.java) 보정 참조 | 조회 목적이 드러난다. `lockStates`는 잠금 동작을 메서드 이름에 명시하므로 Reader suffix만으로 오류로 보지 않음 |
| Resolver | [FarmWorkTargetResolver.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolver.java) 대상 구체화, [WorkOperationActionResolver.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationActionResolver.java) 가능 행동 계산, [OrchidGroupMutationReplayResolver.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationReplayResolver.java) replay 해석 | 모두 입력/상태를 해석해 결과를 결정한다. 책임의 종류는 이름의 앞부분으로 구별 가능 |
| Provider | [RequestActorProvider.java](../backend/src/main/java/com/greenhouse/backend/common/application/RequestActorProvider.java) 요청 사용자 공급 | 환경 context 공급으로 의미가 분명함 |
| Gateway | [WorkExecutionReferenceGateway.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkExecutionReferenceGateway.java) 참조 조회, [InboundPottingPlanGateway.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/InboundPottingPlanGateway.java) 입고 계획 대상 조회/잠금/연결 | Gateway와 Reader가 완전히 동일한 책임은 아니다. 일괄 suffix 변경의 실익을 확인하지 못함 |
| Adapter | [FarmWorkCorrectionAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java), [FarmWorkExecutionReferenceGateway.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkExecutionReferenceGateway.java), [FarmWorkOperationMutationGraphAdapter.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/FarmWorkOperationMutationGraphAdapter.java) | 계약 구현·변환이 실제로 존재. 클래스 이름 suffix를 모두 Adapter로 맞추는 변경은 우선할 근거가 부족함 |
| Facade | main Java에 `*Facade.java` 없음 | 해당 이름의 남용을 확인하지 못함 |
| cancel/void/delete | [WorkOperationVoidService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java) `voidOperation`은 `cancelOperation` 위임. [OrchidGroupCommandService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java) `delete`는 생성 취소를 수행 | 외부 삭제/void 이름과 내부 보존 동작의 차이는 추적 시 주의할 부분. 호출 중인 호환 진입점을 dead code로 보지 않음 |
| View/Response | [WorkOperationView.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationView.java), [WorkOperationTargetView.java](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkOperationTargetView.java)에 과거 응답 이름을 보존하는 `@Schema` 존재 | 명세 이름 보존 의도가 있어 일반 naming inconsistency로 판정하지 않음. 내부 쓰기에서 사용되는 비용은 CQ-001 |
| 작은 Finder/Support | [InboundRecordFinder.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordFinder.java)는 조회/잠금 및 not-found 처리, [WorkOperationSupport.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationSupport.java)는 시점/actor 등 공통 값 제공 | 코드가 짧다는 이유만으로 wrapper로 판정하지 않음 |
| PrintQueryService | [PrintQueryService.java](../backend/src/main/java/com/greenhouse/backend/print/application/PrintQueryService.java)의 전표 단건/출력 목록 조회 | 출력 목록 필터 기본값과 read-only 경계를 제공. 두 메서드 위임만으로 불필요하다고 단정하지 않음 |
| handler/strategy/registry | [WorkEffectProcessor.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java), [StructureChangeExecutor.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeExecutor.java), [StructureChangeStrategyRegistry.java](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistry.java) | effect 저장/dispatch, 구조 변경 전략 실행, 지원 타입 등록 검증의 역할이 다름. 전부 과도한 abstraction으로 판정하지 않음. 호출되지 않는 옵션은 CQ-007 |

### 책임·응집도와 필요한 복잡성

- [AuthService.java](../backend/src/main/java/com/greenhouse/backend/auth/AuthService.java)의 `login/logout/currentUser`와 [SessionCookieWriter.java](../backend/src/main/java/com/greenhouse/backend/auth/SessionCookieWriter.java)의 `refresh/expire`는 세션 인증 흐름과 쿠키 정책을 구분한다. 쿠키 갱신을 로그인과 [SessionCookieRefreshFilter.java](../backend/src/main/java/com/greenhouse/backend/auth/SessionCookieRefreshFilter.java)가 재사용하므로 불필요한 wrapper로 보지 않았다.
- [AuctionTrackingService.java](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)의 `getLots`는 검색 인자가 12개지만 `AuctionLotSearchCriteria`로 정규화한 뒤 조회한다. `confirmReturn/adjust/addResult/changeStatus`는 각각 Entity의 명명된 동작을 호출한다. 파라미터 개수만으로 CQ-010과 같은 반복 전달 문제라고 보지 않았다. [AuctionSettlementService.java](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java)의 `rebuildExistingResults`도 잠금 대기 뒤 이미 연결된 결과를 재확인하므로 단순 중복 검사 제거 대상이 아니다.
- [OrchidGroupLedgerReconciliationService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerReconciliationService.java)의 `reconcile`은 여러 불변식을 모으는 긴 메서드지만 `inspectCurrentState/inspectPlacement/inspectFarmReferences/inspectWorkReferences/inspectLedger`로 검사 책임을 드러낸다. issue 수집과 fingerprint 보고라는 하나의 목적이 있어 길이 자체를 결함으로 판정하지 않았다.
- [InboundRecordService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java)는 14개 주입 필드를 갖지만 입고 생성·상세 변경·취소·포트 취소라는 lifecycle을 조정한다. 의존 수 자체로 분리 대상으로 판정하지 않았다. 일반 입고/배치 묶음 생성/작업 기록이 어떻게 달라지는지 읽는 비용은 있으나, 중간 응답·JSON 변환처럼 구체적인 비용만 CQ-001/003으로 기록했다.
- [OrchidGroupMutationEngine.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java)의 823줄은 생성·수량·배치·예약·보정·복원을 한 원장 규칙 아래 처리하는 폭을 반영한다. 길이만으로 낮은 응집도라고 판정하지 않았다. 잠금 후 replay 재확인, effective head, snapshot, 복원 순서는 유지해야 하는 복잡성이다. 변경 비용은 유스케이스별 해당 메서드와 정책을 기준으로 봐야 한다.
- [WorkOperationResponseAssembler.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationResponseAssembler.java)의 batch 조회와 [SalesSlipAllocationBatch.java](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationBatch.java)의 snapshot 집계는 단순 복사 wrapper보다 실질적인 데이터 조립 책임이 있다. 반면 CQ-002의 생성 요청 DTO 경유와 CQ-010의 record 재분해는 의미가 늘지 않는 변환/전달이다.
- [WorkEffectResults.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java)의 typed 결과와 `toMap`은 영속 효과 형식을 유지하는 역할이 있다. typed 결과 자체를 없애기보다 실행 중 type loss와 다중 reader의 key 지식을 줄이는 것이 조사 근거에 맞다.

### Transitional code와 제거 판단의 한계

- [StructureChangeRecordService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java) 단건 등록에는 `@Deprecated(since = "2026-08", forRemoval = false)`와 배치 등록 안내가 있다. 실제 사용되는 호환 경로이므로 dead code로 판정하지 않았다.
- [WorkOrchidGroupStateChainMigrationService.java](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkOrchidGroupStateChainMigrationService.java), [OrchidGroupStateChainMigrationService.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupStateChainMigrationService.java), [OrchidGroupStateChainMigrationCli.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupStateChainMigrationCli.java)는 legacy 효과/기존 데이터를 새 원장에 연결하는 운영·복구 코드다. migration 전용 책임과 제거 조건 주석이 있는 코드의 길이는 일반 업무 서비스와 같은 척도로 평가하지 않았다.
- [InboundPottingOperationService.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java) `keys`의 두 효과 key 형식, [WorkEffectDetailCodec.java](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkEffectDetailCodec.java) fallback, [RelatedOrchidGroupMutations.java](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/RelatedOrchidGroupMutations.java)의 legacy 참조는 과거 데이터 호환 지점을 드러낸다. 종료 시점·운영 데이터 잔존 여부를 조사하지 않고 제거를 제안하지 않는다.
- 확인 가능한 미사용 항목은 CQ-007의 주입 필드와 전략 메서드다. 전체 클래스가 사용되지 않는다는 결론은 내리지 않았다. Spring/직렬화/CLI 같은 동적 진입점은 단순 문자열 참조 개수로 배제할 수 없다.

## 5. 평가 항목별 확인 결과

| 요청 항목 | 결과/근거 |
| --- | --- |
| class responsibility / cohesion | CQ-001/004. 입고 lifecycle·원장 writer의 큰 책임은 업무 의미와 함께 평가; 의존 수만으로 분리 판정하지 않음 |
| method complexity / 긴 method / nesting | CQ-002/004/005. source 전후 상태·취소 분기·그래프 누적 context의 동시 이해 비용 |
| 많은 dependency | CQ-004, CQ-007 및 책임 판정. 10/14개 의존이라는 수치 자체는 결함 근거가 아님 |
| 많은 parameter | CQ-005/010. 가변 출력 누적과 같은 타입 값의 반복 전달이 구체적인 비용 |
| naming consistency / 역할 suffix | 4절 용어 표. 일괄 rename이 필요한 증거 없음; void/delete 호환 의미 차이 기록 |
| duplicate validation / condition | CQ-006. DTO validation과 application 방어, 잠금 전후 재검사는 구분 |
| duplicate mapping | CQ-002/003. 생성 요청→mutation details, 위치 Map, 분산 효과 JSON 해석 |
| 불필요한 wrapper / 과도한 abstraction | CQ-002/007/010. Finder/Support/registry/PrintQuery는 실제 의미 확인 후 유지 판단 |
| 과도한 DTO/Command/Result 변환 | CQ-001/002/003/010. snapshot·감사·명령·최종 API DTO처럼 의미가 다른 변환은 제외 |
| generic Map/JSON 남용 | CQ-003. 저장 형식의 필요성과 실행 중 type loss를 구분 |
| exception / validation 일관성 | CQ-006/009. 공통 HTTP 변환은 존재하며 세부 충돌 관례는 다름 |
| dead code | CQ-007. 저장소 참조 검색으로 한정하여 확인 |
| transitional code | 4절 호환·migration 코드. 사용·운영 데이터 제거 조건 미확인 상태에서 삭제 판정하지 않음 |
| test code readability | CQ-008. 정규식 ID 추출·복합 시나리오; 작은 계약 테스트·JSON helper·fixture는 비교 근거 |
| 기능 이해에 필요한 class/file 수 | 2절 U1–U10. 실제 추적 목록·고유 파일 수·집계 범위 명시 |

## 6. 검증과 한계

이번 평가는 정적 파일/참조 조사, 주요 메서드 직접 추적, 관련 테스트 읽기와 다음 집중 테스트 실행에 근거한다.

```powershell
.\gradlew.bat test --tests 'com.greenhouse.backend.work.application.effect.WorkEffectResultsTest' --tests 'com.greenhouse.backend.work.application.effect.WorkEffectProcessorTest' --tests 'com.greenhouse.backend.sales.application.SalesSlipOutboundServiceTest' --tests 'com.greenhouse.backend.farm.application.transformation.StructureChangeStrategyRegistryTest'
```

`backend/`에서 실행. **18개 성공, 실패/오류 0개**: 결과 계약 6개, effect processor 9개, 출하 서비스 1개, 전략 registry 2개. 이 실행으로 전체 유스케이스나 PostgreSQL 동시성 동작을 검증했다는 뜻은 아니다.

전체 `test`, `workE2eTest`, frontend 검증은 이번 문서 작성에서 실행하지 않았다. 코드·DB·API 계약 변경이 없으며, 쿼리 수·성능·테스트 전체 통과를 평가 결과로 주장하지 않는다. 개선 방향은 향후 변경 후보이고 이번 작업에서는 적용하지 않았다.

확인 항목 **10개: Medium 6개, Low 4개**. 실제 비용이 확인된 흐름에 한정한 결과이며, 파일 수가 적은 경로나 이번에 문제를 제기하지 않은 클래스의 품질을 보증하는 목록은 아니다.
