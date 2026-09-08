package com.greenhouse.backend.work.repository;

public record WorkOperationProgressProjection(Long workOperationId, int total, int pending, int inProgress, int partial,
		int completed, int skipped, int canceled, int failed, int totalQuantity, int processedQuantity,
		int skippedQuantity) {
}
