package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkOperationRelationKind;
import com.greenhouse.backend.work.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOperationRelationQueryServiceTest {

	@Mock
	WorkOperationRepository operationRepository;

	@Mock
	WorkCommandReceiptMembershipRepository membershipRepository;

	@Mock
	WorkCommandReceiptRepository receiptRepository;

	@Mock
	WorkOperationCorrectionRepository correctionRepository;

	@Mock
	WorkOperationSummaryAssembler summaryAssembler;

	WorkOperationRelationQueryService service;

	@BeforeEach
	void setUp() {
		service = new WorkOperationRelationQueryService(operationRepository, membershipRepository, receiptRepository,
				correctionRepository, summaryAssembler);
		when(summaryAssembler.assembleAll(anyList())).thenReturn(List.of());
	}

	@Test
	void returnsAllOperationsFromTheSameCreationReceiptInReceiptOrder() {
		WorkOperation first = operation(1L, null);
		WorkOperation second = operation(2L, null);
		WorkCommandReceipt receipt = mock(WorkCommandReceipt.class);
		when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(first));
		when(membershipRepository.findReceiptKeysByOperationId(1L)).thenReturn(List.of("receipt"));
		when(receiptRepository.findByReceiptKeyIn(List.of("receipt"))).thenReturn(List.of(receipt));
		when(receipt.getResultOperationIds()).thenReturn(List.of(1L, 2L));
		when(operationRepository.findWithWorkTypeByIdIn(anyCollection())).thenReturn(List.of(second, first));

		service.get(1L, WorkOperationRelationKind.CREATION_BATCH);

		assertThat(assembledOperations()).containsExactly(first, second);
	}

	@Test
	void returnsLinkedParentBeforeItsChild() {
		WorkOperation movement = operation(10L, null);
		WorkOperation discard = operation(11L, movement);
		when(operationRepository.findWithWorkTypeById(10L)).thenReturn(Optional.of(movement));
		when(operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(List.of(10L)))
			.thenReturn(List.of(discard));
		when(correctionRepository.findByOriginalWorkOperationIdInOrCorrectionWorkOperationIdIn(List.of(10L),
				List.of(10L)))
			.thenReturn(List.of());

		service.get(10L, WorkOperationRelationKind.LINKED);

		assertThat(assembledOperations()).containsExactly(movement, discard);
	}

	@Test
	void returnsTheWholeLinkedGroupWhenOpenedFromAChild() {
		WorkOperation movement = operation(10L, null);
		WorkOperation firstDiscard = operation(11L, movement);
		WorkOperation secondDiscard = operation(12L, movement);
		when(operationRepository.findWithWorkTypeById(11L)).thenReturn(Optional.of(firstDiscard));
		when(operationRepository.findWithWorkTypeByIdIn(List.of(10L))).thenReturn(List.of(movement));
		when(operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(List.of(11L)))
			.thenReturn(List.of());
		when(operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(List.of(10L)))
			.thenReturn(List.of(firstDiscard, secondDiscard));
		when(operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(List.of(12L)))
			.thenReturn(List.of());
		when(correctionRepository.findByOriginalWorkOperationIdInOrCorrectionWorkOperationIdIn(anyCollection(),
				anyCollection()))
			.thenReturn(List.of());

		service.get(11L, WorkOperationRelationKind.LINKED);

		assertThat(assembledOperations()).containsExactly(movement, firstDiscard, secondDiscard);
	}

	@Test
	void returnsCorrectionOriginalBeforeTheCorrectionOperation() {
		WorkOperation original = operation(20L, null);
		WorkOperation correction = operation(21L, null);
		WorkOperationCorrection relation = mock(WorkOperationCorrection.class);
		when(relation.getOriginalWorkOperation()).thenReturn(original);
		when(relation.getCorrectionWorkOperation()).thenReturn(correction);
		when(operationRepository.findWithWorkTypeById(21L)).thenReturn(Optional.of(correction));
		when(operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(List.of(21L)))
			.thenReturn(List.of());
		when(correctionRepository.findByOriginalWorkOperationIdInOrCorrectionWorkOperationIdIn(List.of(21L),
				List.of(21L)))
			.thenReturn(List.of(relation));

		service.get(21L, WorkOperationRelationKind.LINKED);

		assertThat(assembledOperations()).containsExactly(original, correction);
	}

	@SuppressWarnings("unchecked")
	private List<WorkOperation> assembledOperations() {
		ArgumentCaptor<List<WorkOperation>> captor = ArgumentCaptor.forClass(List.class);
		verify(summaryAssembler).assembleAll(captor.capture());
		return captor.getValue();
	}

	private WorkOperation operation(Long id, WorkOperation parent) {
		WorkOperation operation = mock(WorkOperation.class);
		when(operation.getId()).thenReturn(id);
		if (parent != null) {
			when(operation.getParentOperation()).thenReturn(parent);
		}
		return operation;
	}

}
