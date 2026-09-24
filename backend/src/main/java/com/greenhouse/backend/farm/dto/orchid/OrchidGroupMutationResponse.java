package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrchidGroupMutationResponse(Long id, OrchidGroupMutationType mutationType,
		OrchidGroupMutationSourceDomain sourceDomain, String sourceType, String sourceReferenceId,
		String sourceOperationKey, UUID correlationId, String commandFingerprint, Instant occurredAt,
		Instant recordedAt, LocalDate effectiveBusinessDate, String reason, Integer schemaVersion,
		OrchidGroupMutationWorkOperationResponse workOperation, List<OrchidGroupMutationEntryResponse> entries,
		List<OrchidGroupMutationRelationResponse> relations) {

	public static OrchidGroupMutationResponse from(OrchidGroupMutation mutation,
			OrchidGroupMutationWorkOperationResponse workOperation, List<OrchidGroupMutationEntryResponse> entries,
			List<OrchidGroupMutationRelationResponse> relations) {
		return new OrchidGroupMutationResponse(mutation.getId(), mutation.getMutationType(), mutation.getSourceDomain(),
				mutation.getSourceType(), mutation.getSourceReferenceId(), mutation.getSourceOperationKey(),
				mutation.getCorrelationId(), mutation.getCommandFingerprint(), mutation.getOccurredAt(),
				mutation.getRecordedAt(), mutation.getEffectiveBusinessDate(), mutation.getReason(),
				mutation.getSchemaVersion(), workOperation, entries, relations);
	}
}
