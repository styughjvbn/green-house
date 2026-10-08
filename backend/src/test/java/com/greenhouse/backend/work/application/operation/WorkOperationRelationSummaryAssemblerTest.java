package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
import com.greenhouse.backend.work.domain.operation.WorkCommandReceiptMembership;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkOperationOriginType;
import com.greenhouse.backend.work.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.repository.WorkOperationChildCount;
import com.greenhouse.backend.work.repository.WorkOperationInboundReference;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOperationRelationSummaryAssemblerTest {

  @Mock WorkCommandReceiptRepository receiptRepository;

  @Mock WorkCommandReceiptMembershipRepository membershipRepository;

  @Mock WorkOperationRepository operationRepository;

  @Mock WorkOperationTargetRepository targetRepository;

  WorkOperationRelationSummaryAssembler assembler;

  @BeforeEach
  void setUp() {
    assembler =
        new WorkOperationRelationSummaryAssembler(
            receiptRepository, membershipRepository, operationRepository, targetRepository);
  }

  @Test
  void separatesInboundSystemAndWorkManagementOriginsAndUsesExactReceiptBatch() {
    WorkOperation inbound = operation(1L, null);
    WorkOperation movement = operation(2L, null);
    WorkOperation discard = operation(3L, movement);
    WorkOperation direct = operation(4L, null);
    when(targetRepository.findInboundReferences(List.of(1L, 2L, 3L, 4L)))
        .thenReturn(List.of(new WorkOperationInboundReference(1L, 123L)));
    when(membershipRepository.findByOperationIdIn(List.of(1L, 2L, 3L, 4L)))
        .thenReturn(
            List.of(
                new WorkCommandReceiptMembership("receipt", 1L),
                new WorkCommandReceiptMembership("receipt", 4L)));
    WorkCommandReceipt receipt = mock(WorkCommandReceipt.class);
    when(receipt.getResultOperationIds()).thenReturn(List.of(1L, 4L));
    when(receiptRepository.findByReceiptKeyIn(List.of("receipt"))).thenReturn(List.of(receipt));
    when(operationRepository.countChildrenByParentIds(new LinkedHashSet<>(List.of(1L, 2L, 4L))))
        .thenReturn(List.of(new WorkOperationChildCount(2L, 1L)));

    var summaries = assembler.assemble(List.of(inbound, movement, discard, direct));

    assertThat(summaries.get(1L).originType()).isEqualTo(WorkOperationOriginType.INBOUND);
    assertThat(summaries.get(1L).inboundRecordIds()).containsExactly(123L);
    assertThat(summaries.get(1L).creationBatchSize()).isEqualTo(2);
    assertThat(summaries.get(2L).hasLinkedOperations()).isTrue();
    assertThat(summaries.get(2L).linkedOperationCount()).isEqualTo(1);
    assertThat(summaries.get(3L).originType()).isEqualTo(WorkOperationOriginType.SYSTEM);
    assertThat(summaries.get(3L).hasLinkedOperations()).isTrue();
    assertThat(summaries.get(3L).linkedOperationCount()).isEqualTo(1);
    assertThat(summaries.get(4L).originType()).isEqualTo(WorkOperationOriginType.WORK_MANAGEMENT);
    assertThat(summaries.get(4L).creationBatchSize()).isEqualTo(2);
  }

  @Test
  void keepsOperationWithoutReceiptOrExplicitRelationIndependent() {
    WorkOperation operation = operation(9L, null);
    when(targetRepository.findInboundReferences(List.of(9L))).thenReturn(List.of());
    when(membershipRepository.findByOperationIdIn(List.of(9L))).thenReturn(List.of());
    when(operationRepository.countChildrenByParentIds(new LinkedHashSet<>(List.of(9L))))
        .thenReturn(List.of());

    var summary = assembler.assemble(List.of(operation)).get(9L);

    assertThat(summary.originType()).isEqualTo(WorkOperationOriginType.WORK_MANAGEMENT);
    assertThat(summary.creationBatchSize()).isEqualTo(1);
    assertThat(summary.hasLinkedOperations()).isFalse();
    assertThat(summary.linkedOperationCount()).isZero();
  }

  @Test
  void givesParentAndEveryChildTheSameRelatedOperationCount() {
    WorkOperation parent = operation(10L, null);
    WorkOperation firstChild = operation(11L, parent);
    WorkOperation secondChild = operation(12L, parent);
    when(targetRepository.findInboundReferences(List.of(10L, 11L, 12L))).thenReturn(List.of());
    when(membershipRepository.findByOperationIdIn(List.of(10L, 11L, 12L))).thenReturn(List.of());
    when(operationRepository.countChildrenByParentIds(new LinkedHashSet<>(List.of(10L))))
        .thenReturn(List.of(new WorkOperationChildCount(10L, 2L)));

    var summaries = assembler.assemble(List.of(parent, firstChild, secondChild));

    assertThat(summaries.values())
        .extracting(summary -> summary.linkedOperationCount())
        .containsOnly(2);
  }

  private WorkOperation operation(Long id, WorkOperation parent) {
    WorkOperation operation = mock(WorkOperation.class);
    when(operation.getId()).thenReturn(id);
    when(operation.getParentOperation()).thenReturn(parent);
    if (parent != null) {
      when(operation.getRelationType()).thenReturn(WorkOperationRelationType.MOVEMENT_DISCARD);
    }
    return operation;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesExistingFamilyPrecedenceForNestedParents(boolean reversed) {
    var parent = operation(1L, null);
    var child = operation(2L, parent);
    var grandchild = operation(3L, child);
    var page = reversed ? List.of(grandchild, child, parent) : List.of(parent, child, grandchild);
    var ids = page.stream().map(WorkOperation::getId).toList();
    when(targetRepository.findInboundReferences(ids)).thenReturn(List.of());
    when(membershipRepository.findByOperationIdIn(ids)).thenReturn(List.of());
    when(operationRepository.countChildrenByParentIds(new LinkedHashSet<>(List.of(1L, 2L))))
        .thenReturn(
            List.of(new WorkOperationChildCount(1L, 5L), new WorkOperationChildCount(2L, 2L)));
    var summaries = assembler.assemble(page);
    assertThat(summaries.get(1L).linkedOperationCount()).isEqualTo(5);
    assertThat(summaries.get(2L).linkedOperationCount()).isEqualTo(reversed ? 5 : 2);
    assertThat(summaries.get(3L).linkedOperationCount()).isEqualTo(2);
  }
}
