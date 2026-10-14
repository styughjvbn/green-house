package com.greenhouse.backend.work.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.api.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.effect.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkType;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationCancellationRequest;
import com.greenhouse.backend.work.spi.operation.PottingVoidPort;
import com.greenhouse.backend.work.spi.operation.StructureChangeVoidPort;
import com.greenhouse.backend.work.spi.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import com.greenhouse.backend.work.target.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.target.repository.WorkTargetExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WorkOperationCancellationServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0);

  @Mock private WorkOperationRepository operationRepository;

  @Mock private WorkAppliedEffectRepository effectRepository;

  @Mock private WorkTargetExecutionRepository executionRepository;

  @Mock private WorkOperationTargetRepository targetRepository;

  @Mock private StructureChangeVoidPort structureChangeVoidPort;

  @Mock private PottingVoidPort pottingVoidPort;

  @Mock private InboundPottingPlanGateway inboundPottingPlanGateway;

  @Mock private WorkOperationQueryService queryService;

  @Mock private WorkOperationSupport support;

  @Mock private WorkOperationLockService operationLocks;

  @InjectMocks private WorkOperationVoidService service;

  @Mock private WorkAppliedEffect effect;

  @Mock private WorkOperationView response;

  @Test
  void cancelsACompletedRecordOnlyWorkWithoutMutationCompensation() {
    WorkType type =
        new WorkType("PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 1);
    WorkOperation operation =
        new WorkOperation(
            type,
            "농약 기록",
            LocalDate.from(NOW),
            null,
            WorkSourceScopeType.ORCHID_GROUP,
            1L,
            Map.of(),
            Map.of(),
            null,
            null,
            NOW.minusMinutes(1));
    operation.complete(NOW.minusSeconds(1));

    when(operationLocks.lock(1L)).thenReturn(operation);
    when(effectRepository.findByWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of(effect));
    when(effect.getMutationId()).thenReturn(null);
    when(executionRepository.findByTargetWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of());
    when(support.normalizeRequired("cancel-record")).thenReturn("cancel-record");
    when(support.normalizeRequired("오등록")).thenReturn("오등록");
    when(support.now()).thenReturn(NOW);
    when(queryService.get(1L)).thenReturn(response);

    assertThat(
            service.cancelOperation(
                1L, new WorkOperationCancellationRequest("cancel-record", "오등록")))
        .isSameAs(response);
    assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.CANCELED);
    assertThat(operation.getVoidReason()).isEqualTo("오등록");
    verify(effect).cancel(NOW);
  }

  @ParameterizedTest
  @CsvSource({
    "STOPPED, ALREADY_CLOSED",
    "CANCELED, ALREADY_CLOSED",
    "VOIDED, ALREADY_CLOSED",
    "PLANNED, VOID_WITH_PARENT_MOVEMENT"
  })
  void closedStatusTakesPriorityOverRelatedDiscardAndUnsupportedType(
      WorkOperationStatus status, String blockerCode) {
    var operation = operation(1L, "INBOUND");
    operation.linkToParent(operation(2L, "MOVEMENT"), WorkOperationRelationType.MOVEMENT_DISCARD);
    ReflectionTestUtils.setField(operation, "status", status);
    when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));

    var eligibility = service.eligibility(1L);

    assertThat(eligibility.cancellable()).isFalse();
    assertThat(eligibility.blockers()).extracting(item -> item.code()).containsExactly(blockerCode);
    assertThat(eligibility.affectedOperations()).hasSize(1);
    assertThat(eligibility.affectedOrchidGroups()).isEmpty();
    verifyNoInteractions(
        effectRepository, targetRepository, structureChangeVoidPort, pottingVoidPort);
  }

  @Test
  void unsupportedOperationIsRejectedBeforeEffectInspection() {
    var operation = operation(1L, "INBOUND");
    when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));

    var eligibility = service.eligibility(1L);

    assertThat(eligibility.blockers())
        .extracting(item -> item.code())
        .containsExactly("UNSUPPORTED_OPERATION");
    verifyNoInteractions(
        effectRepository, targetRepository, structureChangeVoidPort, pottingVoidPort);
  }

  @ParameterizedTest
  @ValueSource(strings = {"REPOT", "POTTING"})
  void cancellationRechecksThePreviewUnderLocksAndPreservesTheWorkOnRejection(String code) {
    var operation = operation(1L, code);
    operation.complete(NOW.minusSeconds(1));
    when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));
    when(effectRepository.findByWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of(effect));
    when(effect.getMutationId()).thenReturn(20L);
    var blocker = new StructureChangeVoidPort.Blocker("LATER_MUTATION", "후속 변경이 있습니다.", 2);
    if (code.equals("POTTING")) {
      var portEffects = List.of(new PottingVoidPort.Effect(null, 20L));
      when(pottingVoidPort.inspect(1L, portEffects))
          .thenReturn(new PottingVoidPort.Inspection(List.of(), List.of()));
      when(pottingVoidPort.inspectForUpdate(1L, portEffects))
          .thenReturn(new PottingVoidPort.Inspection(List.of(), List.of(blocker)));
    } else {
      when(structureChangeVoidPort.inspect(1L, List.of(20L)))
          .thenReturn(new StructureChangeVoidPort.Inspection(List.of(), List.of(), List.of()));
      when(structureChangeVoidPort.inspectForUpdate(1L, List.of(20L)))
          .thenReturn(
              new StructureChangeVoidPort.Inspection(List.of(), List.of(), List.of(blocker)));
    }
    assertThat(service.eligibility(1L).cancellable()).isTrue();
    when(operationLocks.lock(1L)).thenReturn(operation);
    when(support.normalizeRequired("cancel")).thenReturn("cancel");
    when(support.normalizeRequired("오등록")).thenReturn("오등록");

    assertThatThrownBy(
            () ->
                service.cancelOperation(1L, new WorkOperationCancellationRequest("cancel", "오등록")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("후속 변경이 있습니다.");

    assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.COMPLETED);
    verify(effect, never()).cancel(any());
    verify(executionRepository).findForUpdateByTargetWorkOperationIdOrderByIdAsc(1L);
    verifyNoInteractions(queryService, inboundPottingPlanGateway);
  }

  @Test
  void relatedDiscardBlockerPrecedesFarmBlockersAndImpactOrderIsPreserved() {
    var operation = operation(1L, "MOVEMENT");
    operation.complete(NOW.minusSeconds(1));
    var discard = operation(2L, "DISCARD");
    discard.linkToParent(operation, WorkOperationRelationType.MOVEMENT_DISCARD);
    when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));
    when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
            1L, WorkOperationRelationType.MOVEMENT_DISCARD))
        .thenReturn(List.of(discard));
    var discardEffect = mock(WorkAppliedEffect.class);
    when(effectRepository.findByWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of(effect));
    when(effectRepository.findByWorkOperationIdOrderByIdAsc(2L)).thenReturn(List.of(discardEffect));
    when(effect.getMutationId()).thenReturn(20L);
    when(discardEffect.getMutationId()).thenReturn(10L);
    var farmInspection =
        new StructureChangeVoidPort.Inspection(
            List.of(new StructureChangeVoidPort.OrchidGroupSummary(30L, "원본", 100)),
            List.of(new StructureChangeVoidPort.OrchidGroupSummary(40L, "결과", 80)),
            List.of(new StructureChangeVoidPort.Blocker("LATER_MUTATION", "후속 변경", 2)));
    when(structureChangeVoidPort.inspect(1L, List.of(10L, 20L))).thenReturn(farmInspection);

    var eligibility = service.eligibility(1L);

    assertThat(eligibility.blockers())
        .extracting(item -> item.code())
        .containsExactly("RELATED_DISCARD_NOT_COMPLETED", "LATER_MUTATION");
    assertThat(eligibility.blockers()).extracting(item -> item.count()).containsExactly(1L, 2L);
    assertThat(eligibility.affectedOperations())
        .extracting(item -> item.workOperationId())
        .containsExactly(1L, 2L);
    assertThat(eligibility.affectedOperations())
        .extracting(item -> item.primary())
        .containsExactly(true, false);
    assertThat(eligibility.affectedOrchidGroups())
        .extracting(item -> item.orchidGroupId())
        .containsExactly(30L, 40L);
    assertThat(eligibility.affectedOrchidGroups())
        .extracting(item -> item.impactType().name())
        .containsExactly("RESTORED", "CREATION_CANCELED");

    when(operationLocks.lock(1L)).thenReturn(operation);
    when(support.normalizeRequired("cancel")).thenReturn("cancel");
    when(support.normalizeRequired("오등록")).thenReturn("오등록");
    when(structureChangeVoidPort.inspectForUpdate(1L, List.of(10L, 20L)))
        .thenReturn(farmInspection);
    assertThatThrownBy(
            () ->
                service.cancelOperation(1L, new WorkOperationCancellationRequest("cancel", "오등록")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("연관된 이동 후 잔여 난 폐기 작업의 상태가 완료가 아닙니다.");
    verify(effect, never()).cancel(any());
    verify(discardEffect, never()).cancel(any());
  }

  @Test
  void recordOnlyImpactPreservesTheFirstTargetSnapshotAndOmitsInboundTargets() {
    var operation = operation(1L, "PESTICIDE");
    when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));
    var first = target(30L, "등록 당시 품종", 100);
    var duplicate = target(30L, "다른 snapshot", 50);
    var second = target(40L, "두 번째 품종", 20);
    var inbound = mock(WorkOperationTarget.class);
    when(inbound.getTargetReferenceType()).thenReturn(WorkTargetReferenceType.INBOUND_RECORD);
    when(targetRepository.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
            List.of(1L)))
        .thenReturn(List.of(first, duplicate, second, inbound));

    var eligibility = service.eligibility(1L);

    assertThat(eligibility.cancellable()).isTrue();
    assertThat(eligibility.affectedOrchidGroups())
        .extracting(item -> item.orchidGroupId())
        .containsExactly(30L, 40L);
    assertThat(eligibility.affectedOrchidGroups())
        .extracting(item -> item.varietyName())
        .containsExactly("등록 당시 품종", "두 번째 품종");
    assertThat(eligibility.affectedOrchidGroups())
        .extracting(item -> item.quantity())
        .containsExactly(100, 20);
    assertThat(eligibility.affectedOrchidGroups())
        .extracting(item -> item.impactType().name())
        .containsOnly("RECORD_CANCELED");
    verifyNoInteractions(structureChangeVoidPort, pottingVoidPort);
  }

  private WorkOperationTarget target(Long groupId, String varietyName, int quantity) {
    var target = mock(WorkOperationTarget.class);
    when(target.getTargetReferenceType()).thenReturn(WorkTargetReferenceType.ORCHID_GROUP);
    when(target.getOrchidGroupId()).thenReturn(groupId);
    when(target.getVarietyNameSnapshot()).thenReturn(varietyName);
    when(target.getQuantitySnapshot()).thenReturn(quantity);
    return target;
  }

  private WorkOperation operation(Long id, String code) {
    var template =
        switch (code) {
          case "REPOT", "POTTING" -> WorkTypeTemplate.REPOT;
          case "MOVEMENT" -> WorkTypeTemplate.MOVEMENT;
          case "DISCARD" -> WorkTypeTemplate.DISCARD;
          case "PESTICIDE" -> WorkTypeTemplate.PESTICIDE;
          default -> WorkTypeTemplate.MEMO;
        };
    var type = new WorkType(code, code, template, true, false, true, 1);
    var operation =
        new WorkOperation(
            type,
            code + " 기록",
            LocalDate.from(NOW),
            null,
            WorkSourceScopeType.ORCHID_GROUP,
            1L,
            Map.of(),
            Map.of(),
            null,
            null,
            NOW.minusMinutes(1));
    ReflectionTestUtils.setField(operation, "id", id);
    return operation;
  }
}
