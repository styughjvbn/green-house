package com.greenhouse.backend.farm.dto.orchid;

import java.util.List;

public record OrchidGroupMutationGraphResponse(
    Long rootOrchidGroupId,
    int depth,
    int maxNodes,
    boolean truncated,
    List<OrchidGroupMutationGraphNodeResponse> nodes,
    List<OrchidGroupMutationGraphEdgeResponse> edges) {}
