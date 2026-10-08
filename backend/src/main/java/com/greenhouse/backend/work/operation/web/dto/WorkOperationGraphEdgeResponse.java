package com.greenhouse.backend.work.operation.web.dto;

public record WorkOperationGraphEdgeResponse(
    String id,
    String sourceNodeId,
    String targetNodeId,
    WorkOperationGraphEdgeType edgeType,
    String relationType) {}
