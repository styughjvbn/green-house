package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.application.target.WorkOperationTargetView;
import com.greenhouse.backend.work.dto.effect.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.DiscardRecordResultRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscardRecordServiceTest {

	@Mock
	WorkOperationPlanService planService;

	@Mock
	WorkOperationProgressService progressService;

	@Mock
	WorkTypeService workTypeService;

	@Mock
	WorkOperationRepository operationRepository;

	@Mock
	WorkOperationQueryService queryService;

	DiscardRecordService service;

	@BeforeEach
	void setUp() {
		service = new DiscardRecordService(planService, progressService, workTypeService, operationRepository,
				queryService);
	}

	@Test
	void completesEachVarietySpecificDiscardOperationWithOnlyItsTargets() {
		WorkOperationCreateRequest operationRequest = mock(WorkOperationCreateRequest.class);
		WorkOperationTargetView firstTarget = target(101L, 1L);
		WorkOperationTargetView secondTarget = target(202L, 2L);
		WorkOperationView firstPlan = operation(10L, List.of(firstTarget));
		WorkOperationView secondPlan = operation(20L, List.of(secondTarget));
		WorkOperationView firstCompleted = mock(WorkOperationView.class);
		WorkOperationView secondCompleted = mock(WorkOperationView.class);
		when(planService.createBatch(argThat(batch -> batch.operation() == operationRequest)))
			.thenReturn(List.of(firstPlan, secondPlan));
		when(progressService.start(10L)).thenReturn(firstPlan);
		when(progressService.start(20L)).thenReturn(secondPlan);
		when(progressService.completeTarget(eq(10L), eq(101L), argThat(request ->
				Integer.valueOf(3).equals(request.resultDetails().get("discardQuantity"))
						&& "상태 불량".equals(request.resultDetails().get("reason")))))
			.thenReturn(firstCompleted);
		when(progressService.completeTarget(eq(20L), eq(202L), argThat(request ->
				Integer.valueOf(4).equals(request.resultDetails().get("discardQuantity"))
						&& request.resultDetails().get("reason") == null)))
			.thenReturn(secondCompleted);

		var result = service.create(new DiscardRecordCreateRequest(operationRequest, LocalDate.of(2026, 10, 1),
				"작업자", List.of(new DiscardRecordResultRequest(1L, 3, " 상태 불량 "),
						new DiscardRecordResultRequest(2L, 4, " "))));

		assertThat(result).containsExactly(firstCompleted, secondCompleted);
		verify(progressService).start(10L);
		verify(progressService).start(20L);
	}

	private WorkOperationView operation(Long id, List<WorkOperationTargetView> targets) {
		WorkOperationView operation = mock(WorkOperationView.class);
		when(operation.id()).thenReturn(id);
		when(operation.workTypeCode()).thenReturn("DISCARD");
		when(operation.targets()).thenReturn(targets);
		return operation;
	}

	private WorkOperationTargetView target(Long id, Long orchidGroupId) {
		WorkOperationTargetView target = mock(WorkOperationTargetView.class);
		when(target.id()).thenReturn(id);
		when(target.orchidGroupId()).thenReturn(orchidGroupId);
		return target;
	}

}
