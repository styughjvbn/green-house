package com.greenhouse.backend.work.application.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkEffectStoreTest {

	private final WorkAppliedEffectRepository appliedEffectRepository =
			mock(WorkAppliedEffectRepository.class);
	private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository =
			mock(WorkEffectOrchidGroupRepository.class);
	private final WorkEffectStore store =
			new WorkEffectStore(appliedEffectRepository, effectOrchidGroupRepository);

	@Test
	void persistsAndReplaysTheMutationLinkWithoutFarmDomainDependency() {
		WorkOperation operation = mock(WorkOperation.class);
		UUID correlationId = UUID.randomUUID();
		WorkMutationLink mutationLink = new WorkMutationLink(91L, correlationId);
		WorkExecutionResult executionResult = new WorkExecutionResult(
				"REPOT", Map.of("result", "ok"), List.of(201L), mutationLink);
		WorkEffectCommand command = new WorkEffectCommand(
				LocalDateTime.of(2026, 8, 20, 9, 0),
				"worker",
				Map.of("command", "value"),
				null);
		when(appliedEffectRepository.save(any(WorkAppliedEffect.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		store.save(
				operation,
				null,
				command,
				"EXECUTION:round-1",
				List.of(101L),
				WorkEffectKind.STRUCTURE_CHANGE,
				executionResult);

		ArgumentCaptor<WorkAppliedEffect> effectCaptor = ArgumentCaptor.forClass(WorkAppliedEffect.class);
		verify(appliedEffectRepository).save(effectCaptor.capture());
		WorkAppliedEffect savedEffect = effectCaptor.getValue();
		assertThat(savedEffect.getMutationId()).isEqualTo(91L);
		assertThat(savedEffect.getCorrelationId()).isEqualTo(correlationId);

		when(appliedEffectRepository.findByWorkOperationIdAndEffectKey(11L, "EXECUTION:round-1"))
				.thenReturn(Optional.of(savedEffect));
		when(effectOrchidGroupRepository.findByWorkAppliedEffectIdOrderByIdAsc(savedEffect.getId()))
				.thenReturn(List.of(
						new WorkEffectOrchidGroup(
								savedEffect, 101L, WorkEffectOrchidGroupRelationType.SOURCE),
						new WorkEffectOrchidGroup(
								savedEffect, 201L, WorkEffectOrchidGroupRelationType.RESULT)));

		WorkExecutionResult replayed = store.find(11L, "EXECUTION:round-1").orElseThrow();

		assertThat(replayed.resultOrchidGroupIds()).containsExactly(201L);
		assertThat(replayed.mutationLink()).isEqualTo(mutationLink);
	}
}
