package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StructureChangeResultReader {

	private final WorkOperationRepository workOperationRepository;
	private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

	public List<Long> getCorrectableResultOrchidGroupIds(Long operationId) {
		var operation = workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		if (operation.getWorkType().effectKind() != WorkEffectKind.STRUCTURE_CHANGE
				|| operation.getStatus() != WorkOperationStatus.COMPLETED
				&& operation.getStatus() != WorkOperationStatus.CORRECTED) {
			throw new IllegalArgumentException("완료된 구조 변경 작업의 결과만 보정할 수 있습니다.");
		}
		return workEffectOrchidGroupRepository
				.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId).stream()
				.filter(link -> link.getRelationType() == WorkEffectOrchidGroupRelationType.CREATED
						|| link.getRelationType() == WorkEffectOrchidGroupRelationType.RESULT)
				.map(link -> link.getOrchidGroupId())
				.distinct()
				.toList();
	}
}
