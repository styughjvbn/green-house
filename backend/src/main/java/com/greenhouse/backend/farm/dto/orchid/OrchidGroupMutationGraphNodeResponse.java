package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import java.time.Instant;
import java.time.LocalDate;

public record OrchidGroupMutationGraphNodeResponse(String id, OrchidGroupMutationGraphNodeType nodeType,
		Long orchidGroupId, Long stateRevision, OrchidGroupMutationStateResponse state,
		OrchidGroupMutationGraphLocationResponse location, Long mutationId, OrchidGroupMutationType mutationType,
		OrchidGroupMutationSourceDomain sourceDomain, String sourceType, String sourceReferenceId,
		LocalDate effectiveBusinessDate, Instant occurredAt, OrchidGroupMutationEntryKind entryKind,
		OrchidGroupMutationEntryRole entryRole) {
}
