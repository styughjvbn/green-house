package com.greenhouse.backend.work.application.operation;

import java.time.LocalDate;
import java.util.List;

public interface StructureChangeVoidPort {

	Inspection inspect(Long workOperationId, List<Long> mutationIds);

	Long compensate(Long workOperationId, String requestKey, List<Long> mutationIds, LocalDate businessDate,
			String reason);

	record Inspection(List<OrchidGroupSummary> sourceOrchidGroups, List<OrchidGroupSummary> resultOrchidGroups,
			List<Blocker> blockers) {

		public Inspection {
			sourceOrchidGroups = List.copyOf(sourceOrchidGroups);
			resultOrchidGroups = List.copyOf(resultOrchidGroups);
			blockers = List.copyOf(blockers);
		}
	}

	record OrchidGroupSummary(Long orchidGroupId, String varietyName, Integer quantity) {
	}

	record Blocker(String code, String message, long count) {
	}

}
