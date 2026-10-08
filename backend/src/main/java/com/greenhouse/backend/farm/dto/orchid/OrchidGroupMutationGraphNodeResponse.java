package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import java.time.Instant;
import java.time.LocalDate;

public record OrchidGroupMutationGraphNodeResponse(
    String id,
    OrchidGroupMutationGraphNodeType nodeType,
    Long orchidGroupId,
    Long stateRevision,
    OrchidGroupMutationStateResponse state,
    OrchidGroupMutationGraphLocationResponse location,
    Long mutationId,
    OrchidGroupMutationType mutationType,
    OrchidGroupMutationSourceDomain sourceDomain,
    String sourceType,
    String sourceReferenceId,
    OrchidGroupMutationWorkOperationResponse workOperation,
    LocalDate effectiveBusinessDate,
    Instant occurredAt,
    OrchidGroupMutationEntryKind entryKind,
    OrchidGroupMutationEntryRole entryRole) {}
