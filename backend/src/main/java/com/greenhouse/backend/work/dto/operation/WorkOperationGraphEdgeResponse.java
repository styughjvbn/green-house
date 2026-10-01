package com.greenhouse.backend.work.dto.operation;

public record WorkOperationGraphEdgeResponse(String id, String sourceNodeId, String targetNodeId,
		WorkOperationGraphEdgeType edgeType, String relationType) {
}
