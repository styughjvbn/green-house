package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.api.effect.WorkReconciliationCommand;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import com.greenhouse.backend.work.spi.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.spi.target.WorkTargetResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ImmediateWorkExecutionServiceTest {

  @ParameterizedTest
  @MethodSource("actorsAndTargets")
  void preservesTheLegacyReceiptAndTheActorAcrossOperationAndEffect(
      boolean withTarget, boolean demo, String requestedActor, String expectedActor)
      throws Exception {
    var types = mock(WorkTypeService.class);
    var effects = mock(WorkEffectProcessor.class);
    var operations = mock(WorkOperationRepository.class);
    var targets = mock(WorkTargetResolver.class);
    var aggregates = mock(WorkOperationAggregateCreator.class);
    var executions = mock(WorkTargetExecutionRepository.class);
    var queries = mock(WorkOperationQueryService.class);
    var receipts = mock(WorkCommandReceipts.class);
    var support =
        new WorkOperationSupport(
            Clock.fixed(Instant.parse("2026-07-15T01:02:03Z"), ZoneOffset.UTC),
            new RequestActorProvider(demo, "farm-demo"));
    var service =
        new ImmediateWorkExecutionService(
            types,
            effects,
            operations,
            mock(WorkEffectOrchidGroupRepository.class),
            targets,
            aggregates,
            executions,
            queries,
            support,
            receipts);
    var date = LocalDate.of(2026, 7, 15);
    var payload =
        new WorkReconciliationCommand(
            " raw-key ",
            "payload title",
            date,
            " raw actor ",
            null,
            " reason ",
            90,
            "관리",
            null,
            null,
            null);
    var details = Map.<String, Object>of("reason", "reason");
    var type = mock(WorkType.class);
    when(types.getByCode("RECONCILIATION")).thenReturn(type);
    when(operations.save(any()))
        .thenAnswer(
            invocation -> {
              WorkOperation operation = invocation.getArgument(0);
              ReflectionTestUtils.setField(operation, "id", 11L);
              return operation;
            });
    var target = mock(WorkOperationTarget.class);
    when(target.getQuantitySnapshot()).thenReturn(100);
    var execution = new WorkTargetExecution(target);
    if (withTarget) {
      var resolved = new ResolvedWorkTarget(31L, 7L, "청금", 100, 2, "3.5치", "P35", Map.of());
      when(targets.getCurrent(31L)).thenReturn(resolved);
      when(aggregates.createForOrchidGroups(any(), eq(List.of(resolved)), any(), any()))
          .thenAnswer(invocation -> operations.save(invocation.getArgument(0)));
      when(executions.findByTargetWorkOperationIdOrderByIdAsc(11L)).thenReturn(List.of(execution));
    }
    when(effects.apply(any(), any(), any()))
        .thenReturn(
            WorkExecutionResult.fromStored(
                "RECONCILIATION", Map.of("quantity", 90), List.of(), null));
    var view = mock(WorkOperationView.class);
    when(queries.get(11L)).thenReturn(view);
    // This is the pre-refactor receipt envelope, including nulls and raw payload text.
    var legacy =
        (ObjectNode)
            JsonMapper.builder()
                .build()
                .readTree(
                    """
                {"workTypeCode":"RECONCILIATION","title":" requested title ",
                 "workDate":[2026,7,15],"worker":null,"memo":" memo ","orchidGroupId":null,
                 "details":{"reason":"reason"},"payload":{"idempotencyKey":" raw-key ",
                 "title":"payload title","workDate":[2026,7,15],"worker":" raw actor ",
                 "memo":null,"reason":" reason ","actualQuantity":90,"actualStatus":"관리",
                 "actualBedZoneId":null,"actualStartPosition":null,"actualEndPosition":null}}
                """);
    legacy.put("worker", expectedActor);
    if (withTarget) {
      legacy.put("title", "청금 · 현장 상태 조정");
      legacy.put("orchidGroupId", 31L);
    }
    var fingerprints = new WorkRequestFingerprint();
    when(receipts.execute(eq("IMMEDIATE"), eq("key"), any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(fingerprints.calculate(invocation.getArgument(2)))
                  .isEqualTo(fingerprints.calculate(legacy));
              Supplier<List<Long>> action = invocation.getArgument(3);
              return action.get();
            });

    var result =
        withTarget
            ? service.executeVarietyHistoryForTarget(
                " key ",
                "RECONCILIATION",
                " 청금 ",
                date,
                requestedActor,
                " memo ",
                31L,
                details,
                payload)
            : service.execute(
                " key ",
                "RECONCILIATION",
                " requested title ",
                date,
                requestedActor,
                " memo ",
                details,
                payload);

    assertThat(result).isSameAs(view);
    var operationCaptor = ArgumentCaptor.forClass(WorkOperation.class);
    var commandCaptor = ArgumentCaptor.forClass(WorkEffectCommand.class);
    verify(effects)
        .apply(operationCaptor.capture(), eq(withTarget ? target : null), commandCaptor.capture());
    var operation = operationCaptor.getValue();
    assertThat(operation.getRequestKey()).isEqualTo("key");
    assertThat(operation.getWorkType()).isSameAs(type);
    assertThat(operation.getTitle()).isEqualTo(legacy.path("title").asText());
    assertThat(operation.getPlannedStartDate()).isEqualTo(date);
    assertThat(operation.getPlannedEndDate()).isEqualTo(date);
    assertThat(operation.getWorker()).isEqualTo(expectedActor);
    assertThat(operation.getMemo()).isEqualTo(" memo ");
    assertThat(operation.getDetails()).isEqualTo(details);
    assertThat(operation.getSourceScopeType())
        .isEqualTo(withTarget ? WorkSourceScopeType.ORCHID_GROUP : WorkSourceScopeType.NONE);
    assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.COMPLETED);
    var command = commandCaptor.getValue();
    assertThat(command.worker()).isEqualTo(expectedActor);
    assertThat(command.executedAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 1, 2, 3));
    assertThat(command.resultDetails()).isEqualTo(details);
    assertThat(command.payload()).isEqualTo(payload);
    if (withTarget) {
      assertThat(execution.getWorker()).isEqualTo(expectedActor);
      assertThat(execution.getStatus()).isEqualTo(WorkTargetExecutionStatus.COMPLETED);
      assertThat(execution.getResultDetails()).containsEntry("quantity", 90);
    }
  }

  private static Stream<Arguments> actorsAndTargets() {
    return Stream.of(false, true)
        .flatMap(
            target ->
                Stream.of(
                    Arguments.of(target, false, null, null),
                    Arguments.of(target, false, "   ", null),
                    Arguments.of(target, false, " worker ", "worker"),
                    Arguments.of(target, true, null, "farm-demo"),
                    Arguments.of(target, true, "forged-worker", "farm-demo")));
  }
}
