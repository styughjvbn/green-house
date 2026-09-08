package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class WorkEffectProcessorTest {

	private final WorkEffectStore store = mock(WorkEffectStore.class);

	private final WorkEffectHandler handler = mock(WorkEffectHandler.class);

	private final WorkOperation operation = mock(WorkOperation.class);

	private final WorkType workType = mock(WorkType.class);

	private final WorkOperationTarget target = mock(WorkOperationTarget.class);

	private final WorkEffectCommand command = new WorkEffectCommand(LocalDateTime.of(2026, 8, 20, 9, 0), "담당자",
			Map.of("quantity", 30), "typed payload", Set.of(31L));

	private final WorkExecutionResult result = new WorkExecutionResult("TEST_EFFECT", Map.of("remainingQuantity", 70),
			List.of(31L), new WorkMutationLink(91L, UUID.randomUUID()));

	private WorkEffectProcessor processor;

	@BeforeEach
	void setUp() {
		when(handler.supports()).thenReturn("TEST_EFFECT");
		when(handler.effectKind()).thenReturn(WorkEffectKind.ATTRIBUTE_CHANGE);
		when(handler.execute(any(), any())).thenReturn(result);
		when(operation.getId()).thenReturn(11L);
		when(operation.getWorkType()).thenReturn(workType);
		when(operation.getPlannedStartDate()).thenReturn(LocalDate.of(2026, 8, 19));
		when(operation.getMemo()).thenReturn("작업 메모");
		when(workType.getCode()).thenReturn("TEST_WORK_TYPE");
		when(workType.handlerCode()).thenReturn("TEST_EFFECT");
		when(target.getId()).thenReturn(21L);
		when(target.getTargetReferenceType()).thenReturn(WorkTargetReferenceType.ORCHID_GROUP);
		when(target.getOrchidGroupId()).thenReturn(31L);
		when(target.getInboundRecordId()).thenReturn(null);
		when(target.getLocationSnapshot()).thenReturn(null);
		when(store.save(any(), any(), any(), anyString(), anyList(), any(), any()))
			.thenAnswer(invocation -> invocation.getArgument(6));
		processor = new WorkEffectProcessor(List.of(handler), store);
	}

	@Test
	void missingDeclaredHandlerIsRejectedAtStartup() {
		var registered = WorkTypeDefinition.requiredHandlerCodes()
			.stream()
			.filter(code -> !code.equals("POTTING"))
			.map(code -> {
				var implementation = mock(WorkEffectHandler.class);
				when(implementation.supports()).thenReturn(code);
				return implementation;
			})
			.toList();
		var incomplete = new WorkEffectProcessor(registered, store);
		assertThatThrownBy(incomplete::validateDefinitions).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("POTTING");
	}

	@Test
	void registeredHandlerReceivesValuesAndCannotRewriteTheSavedLocationSnapshot() {
		var location = new LinkedHashMap<String, Object>();
		location.put("bedZoneId", 41L);
		location.put("startPosition", null);
		when(target.getLocationSnapshot()).thenReturn(location);

		assertThat(processor.apply(operation, target, command)).isSameAs(result);

		var contextCaptor = ArgumentCaptor.forClass(WorkEffectContext.class);
		verify(handler).execute(contextCaptor.capture(), eq(command.withEffectKey("TARGET:21")));
		var context = contextCaptor.getValue();
		assertThat(context.operationId()).isEqualTo(11L);
		assertThat(context.workTypeCode()).isEqualTo("TEST_WORK_TYPE");
		assertThat(context.plannedStartDate()).isEqualTo(LocalDate.of(2026, 8, 19));
		assertThat(context.memo()).isEqualTo("작업 메모");
		assertThat(context.target().referenceType()).isEqualTo(WorkTargetReferenceType.ORCHID_GROUP);
		assertThat(context.target().orchidGroupId()).isEqualTo(31L);
		assertThat(context.target().locationSnapshot()).containsEntry("startPosition", null);
		assertThatThrownBy(() -> context.target().locationSnapshot().put("bedZoneId", 99L))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThat(location).containsEntry("bedZoneId", 41L);
		location.put("bedZoneId", 42L);
		assertThat(context.target().locationSnapshot()).containsEntry("bedZoneId", 41L);
		verify(store).save(operation, target, command.withEffectKey("TARGET:21"), "TARGET:21", List.of(31L),
				WorkEffectKind.ATTRIBUTE_CHANGE, result);
		assertThat(command.effectKey()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "OPERATION", "EXECUTION:round-1", "POTTING:round-1" })
	void preservesOperationBatchAndInboundExecutionKeys(String effectKey) {
		WorkOperationTarget expectedTarget = null;
		List<Long> expectedSources = List.of();
		WorkExecutionResult executed;
		switch (effectKey) {
			case "OPERATION" -> executed = processor.apply(operation, null, command);
			case "EXECUTION:round-1" -> {
				expectedSources = List.of(31L, 32L);
				executed = processor.applyBatch(operation, "round-1", expectedSources, command);
			}
			case "POTTING:round-1" -> {
				when(target.getTargetReferenceType()).thenReturn(WorkTargetReferenceType.INBOUND_RECORD);
				when(target.getOrchidGroupId()).thenReturn(null);
				when(target.getInboundRecordId()).thenReturn(51L);
				expectedTarget = target;
				executed = processor.applyTargetExecution(operation, target, "round-1", command);
			}
			default -> throw new AssertionError(effectKey);
		}

		assertThat(executed).isSameAs(result);
		verify(store).find(11L, effectKey);
		verify(store).save(operation, expectedTarget, command.withEffectKey(effectKey), effectKey, expectedSources,
				WorkEffectKind.ATTRIBUTE_CHANGE, result);
		var contextCaptor = ArgumentCaptor.forClass(WorkEffectContext.class);
		verify(handler).execute(contextCaptor.capture(), eq(command.withEffectKey(effectKey)));
		if (expectedTarget == null) {
			assertThat(contextCaptor.getValue().target()).isNull();
		}
		else {
			assertThat(contextCaptor.getValue().target())
				.isEqualTo(new WorkEffectContext.Target(WorkTargetReferenceType.INBOUND_RECORD, null, 51L, null));
		}
	}

	@Test
	void newCompletedRecordDoesNotQueryForAnExistingEffectPerTarget() {
		assertThat(processor.applyNew(operation, target, command)).isSameAs(result);

		verify(store).save(operation, target, command.withEffectKey("TARGET:21"), "TARGET:21", List.of(31L),
				WorkEffectKind.ATTRIBUTE_CHANGE, result);
		verifyNoMoreInteractions(store);
	}

	@Test
	void replayReturnsStoredResultWithoutResolvingHandlerOrCreatingContext() {
		when(store.find(11L, "EXECUTION:round-1")).thenReturn(Optional.of(result));

		assertThat(processor.applyBatch(operation, "round-1", List.of(31L), command)).isSameAs(result);

		verify(store).find(11L, "EXECUTION:round-1");
		verifyNoMoreInteractions(store);
		verify(operation, never()).getWorkType();
		verify(operation, never()).getPlannedStartDate();
		verify(handler, never()).execute(any(), any());
	}

	@Test
	void rejectsDuplicateAndUnknownHandlers() {
		assertThatThrownBy(() -> new WorkEffectProcessor(List.of(handler, handler), store))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("중복");
		when(workType.handlerCode()).thenReturn("UNKNOWN");

		assertThatThrownBy(() -> processor.apply(operation, null, command)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("UNKNOWN");
		verify(store).find(11L, "OPERATION");
		verifyNoMoreInteractions(store);
	}

	@Test
	void failedHandlerDoesNotPersistAnAppliedEffect() {
		when(handler.execute(any(), any())).thenThrow(new IllegalArgumentException("효과 적용 실패"));

		assertThatThrownBy(() -> processor.apply(operation, null, command)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("효과 적용 실패");
		verify(handler).execute(any(WorkEffectContext.class), eq(command.withEffectKey("OPERATION")));
		verify(store).find(11L, "OPERATION");
		verifyNoMoreInteractions(store);
	}

}
