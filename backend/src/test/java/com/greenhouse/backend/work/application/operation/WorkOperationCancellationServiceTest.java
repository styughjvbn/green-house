package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.dto.operation.WorkOperationCancellationRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOperationCancellationServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0);

	@Mock
	private WorkOperationRepository operationRepository;

	@Mock
	private WorkAppliedEffectRepository effectRepository;

	@Mock
	private WorkTargetExecutionRepository executionRepository;

	@Mock
	private WorkOperationTargetRepository targetRepository;

	@Mock
	private StructureChangeVoidPort structureChangeVoidPort;

	@Mock
	private PottingVoidPort pottingVoidPort;

	@Mock
	private InboundPottingPlanGateway inboundPottingPlanGateway;

	@Mock
	private WorkOperationQueryService queryService;

	@Mock
	private WorkOperationSupport support;

	@InjectMocks
	private WorkOperationVoidService service;

	@Mock
	private WorkAppliedEffect effect;

	@Mock
	private WorkOperationView response;

	@Test
	void cancelsACompletedRecordOnlyWorkWithoutMutationCompensation() {
		WorkType type = new WorkType("PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 1);
		WorkOperation operation = new WorkOperation(type, "농약 기록", LocalDate.from(NOW), null,
				WorkSourceScopeType.ORCHID_GROUP, 1L, Map.of(), Map.of(), null, null, NOW.minusMinutes(1));
		operation.complete(NOW.minusSeconds(1));

		when(operationRepository.findWithWorkTypeById(1L)).thenReturn(Optional.of(operation));
		when(effectRepository.findByWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of(effect));
		when(effect.getMutationId()).thenReturn(null);
		when(executionRepository.findByTargetWorkOperationIdOrderByIdAsc(1L)).thenReturn(List.of());
		when(support.normalizeRequired("cancel-record")).thenReturn("cancel-record");
		when(support.normalizeRequired("오등록")).thenReturn("오등록");
		when(support.now()).thenReturn(NOW);
		when(queryService.get(1L)).thenReturn(response);

		assertThat(service.cancelOperation(1L, new WorkOperationCancellationRequest("cancel-record", "오등록")))
			.isSameAs(response);
		assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.CANCELED);
		assertThat(operation.getVoidReason()).isEqualTo("오등록");
		verify(effect).cancel(NOW);
	}

}
