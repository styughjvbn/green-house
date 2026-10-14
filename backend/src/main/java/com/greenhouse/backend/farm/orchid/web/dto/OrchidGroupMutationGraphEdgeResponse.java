package com.greenhouse.backend.farm.orchid.web.dto;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.transformation.domain.OrchidGroupLineageRelationType;

public record OrchidGroupMutationGraphEdgeResponse(
    String id,
    String sourceNodeId,
    String targetNodeId,
    OrchidGroupMutationGraphEdgeType edgeType,
    OrchidGroupMutationEntryRole entryRole,
    OrchidGroupMutationRelationType mutationRelationType,
    OrchidGroupLineageRelationType lineageRelationType) {}
