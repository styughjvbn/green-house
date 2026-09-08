package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.operation.WorkExecutionLocation;
import com.greenhouse.backend.work.application.target.WorkExecutionReferenceGateway;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationDetailSummaryResponse;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationDetailService {

	private final WorkOperationRepository operationRepository;

	private final WorkAppliedEffectRepository effectRepository;

	private final WorkEffectOrchidGroupRepository effectGroupRepository;

	private final WorkOperationCorrectionRepository correctionRepository;

	private final WorkExecutionReferenceGateway executionReferenceGateway;

	public WorkOperationDetailResponse get(Long operationId) {
		WorkOperation operation = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		List<WorkAppliedEffect> effects = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
		List<WorkEffectOrchidGroup> effectGroupLinks = effectGroupRepository
			.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId);
		Map<Long, List<WorkEffectOrchidGroup>> linksByEffectId = effectGroupLinks.stream()
			.collect(Collectors.groupingBy(link -> link.getWorkAppliedEffect().getId(), LinkedHashMap::new,
					Collectors.toList()));
		Map<Long, String> resultVarietyNames = resultVarietyNames(effects, linksByEffectId);
		Map<Long, WorkExecutionLocation> resultLocations = resultLocations(effects);
		return new WorkOperationDetailResponse(WorkOperationDetailSummaryResponse.from(operation),
				WorkOperationDetailAssembler.fields(operation),
				effects.stream()
					.map(effect -> WorkEffectDetailCodec.execution(effect,
							linksByEffectId.getOrDefault(effect.getId(), List.of()), resultVarietyNames,
							resultLocations))
					.toList(),
				corrections(operationId));
	}

	private Map<Long, String> resultVarietyNames(List<WorkAppliedEffect> effects,
			Map<Long, List<WorkEffectOrchidGroup>> linksByEffectId) {
		var ids = effects.stream()
			.flatMap(effect -> WorkEffectDetailCodec
				.resultIds(WorkEffectDetailCodec.map(effect.getResultDetails()),
						linksByEffectId.getOrDefault(effect.getId(), List.of()))
				.stream())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return executionReferenceGateway.varietyNames(ids);
	}

	private Map<Long, WorkExecutionLocation> resultLocations(List<WorkAppliedEffect> effects) {
		var ids = effects.stream()
			.flatMap(effect -> WorkEffectDetailCodec.locationIds(effect).stream())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return executionReferenceGateway.locations(ids);
	}

	private List<WorkCorrectionDetailResponse> corrections(Long operationId) {
		var corrections = correctionRepository.findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(operationId);
		if (corrections.isEmpty())
			return List.of();
		var operationIds = corrections.stream().map(row -> row.getCorrectionWorkOperation().getId()).toList();
		var effectsByOperationId = effectRepository.findByWorkOperationIdInAndEffectKey(operationIds, "OPERATION")
			.stream()
			.collect(Collectors.toMap(effect -> effect.getWorkOperation().getId(),
					effect -> WorkEffectDetailCodec.map(effect.getResultDetails()), (left, right) -> {
						throw new org.springframework.dao.IncorrectResultSizeDataAccessException(1);
					}));
		return corrections.stream()
			.map(correction -> WorkOperationDetailAssembler.correction(correction,
					effectsByOperationId.getOrDefault(correction.getCorrectionWorkOperation().getId(), Map.of())))
			.toList();
	}

}
