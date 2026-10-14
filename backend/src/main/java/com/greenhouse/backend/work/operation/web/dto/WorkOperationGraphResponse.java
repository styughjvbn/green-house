package com.greenhouse.backend.work.operation.web.dto;

import java.util.List;

public record WorkOperationGraphResponse(
    Long rootWorkOperationId,
    WorkOperationGraphDetail detail,
    int depth,
    int maxNodes,
    boolean truncated,
    List<WorkOperationGraphNodeResponse> nodes,
    List<WorkOperationGraphEdgeResponse> edges) {}
