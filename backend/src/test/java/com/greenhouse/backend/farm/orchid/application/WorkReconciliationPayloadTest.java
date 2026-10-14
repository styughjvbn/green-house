package com.greenhouse.backend.farm.orchid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationResult;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.api.orchid.mutation.ReconcileOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.mutation.engine.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupReconciliationRequest;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectContext;
import com.greenhouse.backend.work.api.effect.WorkEffectPayload;
import com.greenhouse.backend.work.api.effect.WorkReconciliationCommand;
import com.greenhouse.backend.work.api.operation.ImmediateWorkExecutionApi;
import com.greenhouse.backend.work.api.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.operation.application.WorkRequestFingerprint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkReconciliationPayloadTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void servicePassesApplicationInputWithTheSameLegacyJsonAndFingerprint() throws Exception {
    var oldDto = request();
    var typed = capturedCommand(oldDto);
    assertThat(json.readTree(json.writeValueAsBytes(typed)))
        .isEqualTo(json.readTree(json.writeValueAsBytes(oldDto)));
    var fingerprints = new WorkRequestFingerprint();
    String golden = "952d12dc639dfc85860241d454d6af4dbf1048ab4e31d6dc40723294ba5bba60";
    assertThat(fingerprints.calculate(oldDto)).isEqualTo(golden);
    assertThat(fingerprints.calculate(typed)).isEqualTo(golden);
    assertThat(typed.idempotencyKey()).isEqualTo(" recon-key ");
    assertThat(typed.reason()).isEqualTo(" 현장 실사 ");
  }

  @Test
  void handlerUsesTypedObservationAndKeepsBeforeAfterSnapshots() throws Exception {
    var engine = mock(OrchidGroupMutationEngine.class);
    var groups = mock(OrchidGroupRepository.class);
    var group = mock(OrchidGroup.class);
    when(group.getQuantity()).thenReturn(100, 13);
    when(group.getMemo()).thenReturn("보존 메모");
    when(groups.findById(31L)).thenReturn(Optional.of(group));
    UUID correlation = UUID.fromString("00000000-0000-0000-0000-000000000091");
    when(engine.reconcile(any()))
        .thenReturn(
            new OrchidGroupMutationResult(
                91L, OrchidGroupMutationType.RECONCILIATION, correlation, List.of(), false));
    var typed = capturedCommand(request());
    var result = new ReconciliationWorkHandler(engine, groups).execute(context(), command(typed));
    var mutation = ArgumentCaptor.forClass(ReconcileOrchidGroupMutationCommand.class);
    verify(engine).reconcile(mutation.capture());
    var applied = mutation.getValue();
    assertThat(applied.actualQuantity()).isEqualTo(13);
    assertThat(applied.actualStatus()).isEqualTo("관리");
    assertThat(applied.actualBedZoneId()).isEqualTo(7L);
    assertThat(applied.actualStartPosition()).isEqualByComparingTo("12.25");
    assertThat(applied.actualEndPosition()).isEqualByComparingTo("14.75");
    assertThat(applied.effectiveBusinessDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    assertThat(applied.reason()).isEqualTo("현장 실사");
    var details = result.storedDetails();
    assertThat(details).containsEntry("reason", "현장 실사");
    assertThat((OrchidGroupStateSnapshot) details.get("before"))
        .extracting(OrchidGroupStateSnapshot::quantity, OrchidGroupStateSnapshot::memo)
        .containsExactly(100, "보존 메모");
    assertThat((OrchidGroupStateSnapshot) details.get("after"))
        .extracting(OrchidGroupStateSnapshot::quantity, OrchidGroupStateSnapshot::memo)
        .containsExactly(13, "보존 메모");
    assertThat(result.mutationLink().mutationId()).isEqualTo(91L);
    assertThat(result.mutationLink().correlationId()).isEqualTo(correlation);
  }

  @Test
  void mismatchedApplicationInputIsRejectedBeforeLoadingOrMutatingGroups() {
    var engine = mock(OrchidGroupMutationEngine.class);
    var groups = mock(OrchidGroupRepository.class);
    assertThatThrownBy(
            () ->
                new ReconciliationWorkHandler(engine, groups)
                    .execute(
                        context(),
                        command(
                            new StructureChangeCommand(
                                "wrong", null, null, null, List.of(), List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("명령 형식");
    verifyNoInteractions(engine, groups);
  }

  private WorkReconciliationCommand capturedCommand(OrchidGroupReconciliationRequest request) {
    var immediate = mock(ImmediateWorkExecutionApi.class);
    var groups = mock(OrchidGroupRepository.class);
    var group = mock(OrchidGroup.class);
    when(group.getVarietyName()).thenReturn("기존 품종");
    when(groups.findDetailById(31L)).thenReturn(Optional.of(group));
    new OrchidGroupReconciliationService(immediate, groups).reconcile(31L, request);
    var payload = ArgumentCaptor.forClass(WorkEffectPayload.class);
    verify(immediate)
        .executeVarietyHistoryForTarget(
            eq(request.idempotencyKey()),
            eq("RECONCILIATION"),
            eq("기존 품종"),
            eq(request.workDate()),
            eq(request.worker()),
            eq(request.memo()),
            eq(31L),
            eq(Map.of("reason", "현장 실사")),
            payload.capture());
    return (WorkReconciliationCommand) payload.getValue();
  }

  private OrchidGroupReconciliationRequest request() throws Exception {
    return json.readValue(
        """
        {"idempotencyKey":" recon-key ","title":"요청 제목","workDate":"2026-07-15",
         "worker":" 작업자 ","memo":" 원문 ","reason":" 현장 실사 ","actualQuantity":13,
         "actualStatus":"관리","actualBedZoneId":7,"actualStartPosition":12.25,"actualEndPosition":14.75}
        """,
        OrchidGroupReconciliationRequest.class);
  }

  private WorkEffectContext context() {
    return new WorkEffectContext(
        11L,
        "RECONCILIATION",
        LocalDate.of(2026, 7, 15),
        null,
        new WorkEffectContext.Target(WorkTargetReferenceType.ORCHID_GROUP, 31L, null, null));
  }

  private WorkEffectCommand command(WorkEffectPayload payload) {
    return new WorkEffectCommand(
            LocalDateTime.of(2026, 7, 15, 0, 0),
            "작업자",
            Map.of("unknownPersistenceField", true),
            payload)
        .withEffectKey("TARGET:21");
  }
}
