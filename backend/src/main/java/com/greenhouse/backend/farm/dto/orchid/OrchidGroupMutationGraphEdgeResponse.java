package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;

public record OrchidGroupMutationGraphEdgeResponse(
    String id,
    String sourceNodeId,
    String targetNodeId,
    OrchidGroupMutationGraphEdgeType edgeType,
    OrchidGroupMutationEntryRole entryRole,
    OrchidGroupMutationRelationType mutationRelationType,
    OrchidGroupLineageRelationType lineageRelationType) {}
