package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphDetail;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphEdgeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphNodeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationOriginType;
import com.greenhouse.backend.work.dto.operation.WorkOperationRelationSummaryResponse;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOperationGraphQueryServiceTest {

	@Mock
	WorkOperationRepository operationRepository;

	@Mock
	WorkOperationTargetRepository targetRepository;

	@Mock
	WorkAppliedEffectRepository effectRepository;

	@Mock
	WorkCommandReceiptRepository receiptRepository;

	@Mock
	WorkCommandReceiptMembershipRepository membershipRepository;

	@Mock
	WorkOperationCorrectionRepository correctionRepository;

	@Mock
	WorkOperationRelationSummaryAssembler relationSummaryAssembler;

	@Mock
	WorkOperationMutationGraphPort mutationGraphPort;

	WorkOperationGraphQueryService service;

	@BeforeEach
	void setUp() {
		service = new WorkOperationGraphQueryService(operationRepository, targetRepository, effectRepository,
				receiptRepository, membershipRepository, correctionRepository, relationSummaryAssembler,
				mutationGraphPort);
		when(targetRepository.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(anyCollection()))
			.thenReturn(List.of());
		when(correctionRepository.findByOriginalWorkOperationIdInOrCorrectionWorkOperationIdIn(anyCollection(),
				anyCollection()))
			.thenReturn(List.of());
	}

	@Test
	void restoresExactSiblingsOnlyFromMultiResultReceipt() {
		WorkOperation selected = operation(51L, null, null);
		WorkOperation sibling = operation(52L, null, null);
		WorkCommandReceipt receipt = mock(WorkCommandReceipt.class);
		when(receipt.getResultOperationIds()).thenReturn(List.of(51L, 52L));
		when(operationRepository.findWithWorkTypeById(51L)).thenReturn(Optional.of(selected));
		when(operationRepository.findWithWorkTypeByIdIn(List.of(51L, 52L))).thenReturn(List.of(selected, sibling));
		when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(51L,
				WorkOperationRelationType.MOVEMENT_DISCARD))
			.thenReturn(List.of());
		when(membershipRepository.findReceiptKeysByOperationId(51L)).thenReturn(List.of("receipt"));
		when(receiptRepository.findByReceiptKeyIn(List.of("receipt"))).thenReturn(List.of(receipt));
		when(relationSummaryAssembler.assemble(anyCollection()))
			.thenReturn(Map.of(51L, summary(WorkOperationOriginType.WORK_MANAGEMENT, 2), 52L,
					summary(WorkOperationOriginType.WORK_MANAGEMENT, 2)));

		var graph = service.get(51L, WorkOperationGraphDetail.WORK, 1, 120);

		assertThat(graph.nodes()).filteredOn(node -> node.nodeType() == WorkOperationGraphNodeType.CREATION_BATCH)
			.singleElement()
			.extracting(node -> node.creationBatchSize())
			.isEqualTo(2);
		assertThat(graph.edges()).filteredOn(edge -> edge.edgeType() == WorkOperationGraphEdgeType.SAME_COMMAND)
			.hasSize(2);
	}

	@Test
	void keepsReceiptLessHistoricalOperationIndependent() {
		WorkOperation selected = operation(70L, null, null);
		when(operationRepository.findWithWorkTypeById(70L)).thenReturn(Optional.of(selected));
		when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(70L,
				WorkOperationRelationType.MOVEMENT_DISCARD))
			.thenReturn(List.of());
		when(membershipRepository.findReceiptKeysByOperationId(70L)).thenReturn(List.of());
		when(relationSummaryAssembler.assemble(anyCollection()))
			.thenReturn(Map.of(70L, summary(WorkOperationOriginType.WORK_MANAGEMENT, 1)));

		var graph = service.get(70L, WorkOperationGraphDetail.WORK, 1, 120);

		assertThat(graph.nodes()).noneMatch(node -> node.nodeType() == WorkOperationGraphNodeType.CREATION_BATCH);
		assertThat(graph.nodes()).filteredOn(node -> node.nodeType() == WorkOperationGraphNodeType.WORK_OPERATION)
			.singleElement();
	}

	@Test
	void keepsSingleResultReceiptOperationIndependent() {
		WorkOperation selected = operation(71L, null, null);
		WorkCommandReceipt receipt = mock(WorkCommandReceipt.class);
		when(receipt.getResultOperationIds()).thenReturn(List.of(71L));
		when(operationRepository.findWithWorkTypeById(71L)).thenReturn(Optional.of(selected));
		when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(71L,
				WorkOperationRelationType.MOVEMENT_DISCARD))
			.thenReturn(List.of());
		when(membershipRepository.findReceiptKeysByOperationId(71L)).thenReturn(List.of("single-receipt"));
		when(receiptRepository.findByReceiptKeyIn(List.of("single-receipt"))).thenReturn(List.of(receipt));
		when(relationSummaryAssembler.assemble(anyCollection()))
			.thenReturn(Map.of(71L, summary(WorkOperationOriginType.WORK_MANAGEMENT, 1)));

		var graph = service.get(71L, WorkOperationGraphDetail.WORK, 1, 120);

		assertThat(graph.nodes()).noneMatch(node -> node.nodeType() == WorkOperationGraphNodeType.CREATION_BATCH);
		assertThat(graph.edges()).noneMatch(edge -> edge.edgeType() == WorkOperationGraphEdgeType.SAME_COMMAND);
	}

	@Test
	void rendersMovementBeforeDiscardAndExpandsLineageThroughFarmPort() {
		WorkOperation movement = operation(62L, null, null);
		WorkOperation discard = operation(61L, movement, WorkOperationRelationType.MOVEMENT_DISCARD);
		when(operationRepository.findWithWorkTypeById(62L)).thenReturn(Optional.of(movement));
		when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(62L,
				WorkOperationRelationType.MOVEMENT_DISCARD))
			.thenReturn(List.of(discard));
		when(membershipRepository.findReceiptKeysByOperationId(62L)).thenReturn(List.of());
		when(relationSummaryAssembler.assemble(anyCollection())).thenReturn(Map.of(62L,
				summary(WorkOperationOriginType.WORK_MANAGEMENT, 1), 61L, summary(WorkOperationOriginType.SYSTEM, 1)));
		when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(anyCollection()))
			.thenReturn(List.of());
		when(movement.getVoidMutationId()).thenReturn(null);
		when(discard.getVoidMutationId()).thenReturn(null);
		when(mutationGraphPort.load(anyCollection(), eq(true), anyInt(), anyInt()))
			.thenReturn(WorkOperationMutationGraphPort.Fragment.empty());

		var graph = service.get(62L, WorkOperationGraphDetail.LINEAGE, 2, 120);

		assertThat(graph.edges()).filteredOn(edge -> edge.edgeType() == WorkOperationGraphEdgeType.PRECEDES)
			.singleElement()
			.satisfies(edge -> {
				assertThat(edge.sourceNodeId()).isEqualTo("work-operation-62");
				assertThat(edge.targetNodeId()).isEqualTo("work-operation-61");
			});
		verify(mutationGraphPort).load(eq(List.of()), eq(true), eq(2), anyInt());
	}

	@Test
	void loadsMutationEffectsForBothOriginalAndCorrectionOperations() {
		WorkOperation original = operation(80L, null, null);
		WorkOperation correction = operation(81L, null, null);
		WorkOperationCorrection relation = mock(WorkOperationCorrection.class);
		WorkAppliedEffect originalEffect = effect(original, 111L);
		WorkAppliedEffect correctionEffect = effect(correction, 112L);
		when(operationRepository.findWithWorkTypeById(80L)).thenReturn(Optional.of(original));
		when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(80L,
				WorkOperationRelationType.MOVEMENT_DISCARD))
			.thenReturn(List.of());
		when(membershipRepository.findReceiptKeysByOperationId(80L)).thenReturn(List.of());
		when(correctionRepository.findByOriginalWorkOperationIdInOrCorrectionWorkOperationIdIn(anyCollection(),
				anyCollection()))
			.thenReturn(List.of(relation));
		when(relation.getOriginalWorkOperation()).thenReturn(original);
		when(relation.getCorrectionWorkOperation()).thenReturn(correction);
		when(relationSummaryAssembler.assemble(anyCollection())).thenReturn(Map.of(80L,
				summary(WorkOperationOriginType.WORK_MANAGEMENT, 1), 81L, summary(WorkOperationOriginType.SYSTEM, 1)));
		when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(anyCollection()))
			.thenReturn(List.of(originalEffect, correctionEffect));
		when(original.getVoidMutationId()).thenReturn(null);
		when(correction.getVoidMutationId()).thenReturn(null);
		when(mutationGraphPort.load(eq(List.of(111L, 112L)), eq(false), eq(1), anyInt()))
			.thenReturn(fragment(111L, 112L));

		var graph = service.get(80L, WorkOperationGraphDetail.MUTATION, 1, 120);

		assertThat(graph.nodes()).filteredOn(node -> node.nodeType() == WorkOperationGraphNodeType.WORK_OPERATION)
			.hasSize(2);
		assertThat(graph.edges()).filteredOn(edge -> edge.edgeType() == WorkOperationGraphEdgeType.EFFECT)
			.extracting(edge -> Map.entry(edge.sourceNodeId(), edge.targetNodeId()))
			.containsExactlyInAnyOrder(Map.entry("work-operation-80", "mutation-111"),
					Map.entry("work-operation-81", "mutation-112"));
		verify(mutationGraphPort).load(eq(List.of(111L, 112L)), eq(false), eq(1), anyInt());
	}

	@Test
	void truncatesLargeCreationBatchWithoutLeavingDanglingEdges() {
		List<WorkOperation> operations = LongStream.rangeClosed(1, 12)
			.mapToObj(id -> operation(id, null, null))
			.toList();
		List<Long> operationIds = operations.stream().map(WorkOperation::getId).toList();
		WorkCommandReceipt receipt = mock(WorkCommandReceipt.class);
		when(receipt.getResultOperationIds()).thenReturn(operationIds);
		when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(operations.getFirst()));
		when(operationRepository.findWithWorkTypeByIdIn(operationIds)).thenReturn(operations);
		when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(1L,
				WorkOperationRelationType.MOVEMENT_DISCARD))
			.thenReturn(List.of());
		when(membershipRepository.findReceiptKeysByOperationId(1L)).thenReturn(List.of("large-receipt"));
		when(receiptRepository.findByReceiptKeyIn(List.of("large-receipt"))).thenReturn(List.of(receipt));
		Map<Long, WorkOperationRelationSummaryResponse> summaries = new LinkedHashMap<>();
		operationIds.forEach(id -> summaries.put(id, summary(WorkOperationOriginType.WORK_MANAGEMENT, 12)));
		when(relationSummaryAssembler.assemble(anyCollection())).thenReturn(summaries);

		var graph = service.get(1L, WorkOperationGraphDetail.WORK, 1, 10);

		assertThat(graph.truncated()).isTrue();
		assertThat(graph.nodes()).hasSize(10);
		var visibleNodeIds = graph.nodes().stream().map(node -> node.id()).toList();
		assertThat(graph.edges()).allSatisfy(edge -> {
			assertThat(visibleNodeIds).contains(edge.sourceNodeId());
			assertThat(visibleNodeIds).contains(edge.targetNodeId());
		});
	}

	private WorkOperation operation(Long id, WorkOperation parent, WorkOperationRelationType relationType) {
		WorkOperation operation = mock(WorkOperation.class);
		WorkType type = mock(WorkType.class);
		when(operation.getId()).thenReturn(id);
		when(operation.getWorkType()).thenReturn(type);
		when(operation.getTitle()).thenReturn("작업 #" + id);
		when(operation.getStatus()).thenReturn(WorkOperationStatus.COMPLETED);
		when(operation.getPlannedStartDate()).thenReturn(LocalDate.of(2026, 9, 1));
		when(operation.getParentOperation()).thenReturn(parent);
		if (relationType != null)
			when(operation.getRelationType()).thenReturn(relationType);
		when(type.getCode()).thenReturn("TEST");
		when(type.getName()).thenReturn("테스트 작업");
		return operation;
	}

	private WorkOperationRelationSummaryResponse summary(WorkOperationOriginType origin, int batchSize) {
		return new WorkOperationRelationSummaryResponse(origin, List.of(), batchSize, false);
	}

	private WorkAppliedEffect effect(WorkOperation operation, Long mutationId) {
		WorkAppliedEffect effect = mock(WorkAppliedEffect.class);
		when(effect.getWorkOperation()).thenReturn(operation);
		when(effect.getMutationId()).thenReturn(mutationId);
		return effect;
	}

	private WorkOperationMutationGraphPort.Fragment fragment(Long... mutationIds) {
		return new WorkOperationMutationGraphPort.Fragment(false,
				List.of(mutationIds)
					.stream()
					.map(id -> new WorkOperationMutationGraphPort.MutationNode(id, "TEST", LocalDate.of(2026, 9, 1),
							Instant.parse("2026-09-01T00:00:00Z")))
					.toList(),
				List.of(), List.of());
	}

}
