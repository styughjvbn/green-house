package com.greenhouse.backend.work.application.operation;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface StructureChangeVoidPort {

	Inspection inspect(Long workOperationId, List<Long> mutationIds);

	Inspection inspectForUpdate(Long workOperationId, List<Long> mutationIds);

	Long compensate(Long workOperationId, String requestKey, List<Long> mutationIds, LocalDate businessDate,
			String reason);

	Long compensateBatch(Set<Long> workOperationIds, String requestKey, List<Long> mutationIds,
			Set<Long> creationCancellationOrchidGroupIds, LocalDate businessDate, String reason, boolean replayOnly);

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
