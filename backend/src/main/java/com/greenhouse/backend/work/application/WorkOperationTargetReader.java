package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WorkOperationTargetReader {

	private final WorkOperationTargetRepository workOperationTargetRepository;

	public WorkOperationTargetReader(WorkOperationTargetRepository workOperationTargetRepository) {
		this.workOperationTargetRepository = workOperationTargetRepository;
	}

	public Set<Long> getActiveOrchidGroupIds(Long workOperationId) {
		return workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(workOperationId).stream()
				.map(WorkOperationTarget::getOrchidGroupId)
				.collect(Collectors.toSet());
	}
}
