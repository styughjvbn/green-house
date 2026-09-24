package com.greenhouse.backend.work.application.operation;

import java.time.LocalDate;
import java.util.List;

public interface StructureChangeVoidPort {

	Inspection inspect(Long workOperationId, List<Long> mutationIds);

	Long compensate(Long workOperationId, String requestKey, List<Long> mutationIds, LocalDate businessDate,
			String reason);

	record Inspection(List<Long> sourceOrchidGroupIds, List<Long> resultOrchidGroupIds, List<Blocker> blockers) {

		public Inspection {
			sourceOrchidGroupIds = List.copyOf(sourceOrchidGroupIds);
			resultOrchidGroupIds = List.copyOf(resultOrchidGroupIds);
			blockers = List.copyOf(blockers);
		}
	}

	record Blocker(String code, String message, long count) {
	}
}
