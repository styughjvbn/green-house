package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.dto.InboundWorkOperationCreateRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundWorkOperationRecorder {

	private final WorkTypeService workTypeService;
	private final WorkOperationRepository workOperationRepository;
	private final WorkOperationTargetRepository workOperationTargetRepository;
	private final WorkTargetExecutionRepository workTargetExecutionRepository;
	private final WorkAppliedEffectRepository workAppliedEffectRepository;
	private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

	public void record(InboundWorkOperationCreateRequest request) {
		WorkType workType = workTypeService.getByCode(WorkType.INBOUND_CODE);
		if (!workType.isActive()) {
			throw new IllegalArgumentException("입고 작업 유형이 비활성화되어 있습니다.");
		}
		Map<String, Object> details = new LinkedHashMap<>(request.details());
		if (request.createdOrchidGroupId() != null) {
			details.put("orchidGroupId", request.createdOrchidGroupId());
		}
		WorkOperation operation = workOperationRepository.save(new WorkOperation(
				workType,
				request.varietyName().trim() + " 입고",
				request.workDate(),
				request.workDate(),
				WorkSourceScopeType.INBOUND_RECORD_SELECTION,
				null,
				Map.of("inboundRecordIds", List.of(request.inboundRecordId())),
				details,
				normalize(request.worker()),
				normalize(request.memo())));
		WorkOperationTarget target = workOperationTargetRepository.save(
				WorkOperationTarget.inboundRecord(
						operation,
						request.inboundRecordId(),
						request.varietyId(),
						request.varietyName(),
						request.quantity(),
						request.potSize(),
						request.locationSnapshot()));
		WorkTargetExecution execution = workTargetExecutionRepository.save(new WorkTargetExecution(target));
		LocalDateTime executedAt = LocalDateTime.now();
		String worker = normalize(request.worker());
		operation.start(executedAt);
		WorkAppliedEffect effect = workAppliedEffectRepository.save(new WorkAppliedEffect(
				operation,
				target,
				"TARGET:" + target.getId(),
				WorkEffectKind.RECORD_ONLY,
				workType.handlerCode(),
				executedAt,
				worker,
				details,
				details));
		if (request.createdOrchidGroupId() != null) {
			workEffectOrchidGroupRepository.save(new WorkEffectOrchidGroup(
					effect,
					request.createdOrchidGroupId(),
					WorkEffectOrchidGroupRelationType.RESULT));
		}
		execution.completeWithEffect(executedAt, worker, details);
		operation.complete(executedAt);
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
