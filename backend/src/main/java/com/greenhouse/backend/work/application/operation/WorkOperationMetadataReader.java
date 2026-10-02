package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationMetadataReader {

	private final WorkOperationRepository workOperationRepository;

	private final com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository correctionRepository;

	public java.util.Map<Long, WorkOperationMetadata> findOriginalsByCorrectionMutationIds(
			Collection<Long> mutationIds) {
		if (mutationIds.isEmpty())
			return java.util.Map.of();
		return correctionRepository.findByMutationIdIn(mutationIds)
			.stream()
			.collect(java.util.stream.Collectors.toMap(correction -> correction.getMutationId(), correction -> {
				var operation = correction.getOriginalWorkOperation();
				return new WorkOperationMetadata(operation.getId(), operation.getWorkType().getCode(),
						operation.getWorkType().getName(), operation.getTitle());
			}));
	}

	public List<WorkOperationMetadata> findByIds(Collection<Long> workOperationIds) {
		return workOperationRepository.findByIdIn(workOperationIds)
			.stream()
			.map(operation -> new WorkOperationMetadata(operation.getId(), operation.getWorkType().getCode(),
					operation.getWorkType().getName(), operation.getTitle()))
			.toList();
	}

	public record WorkOperationMetadata(Long id, String workTypeCode, String workType, String title) {
	}

}
