package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.ImportBatch;
import com.greenhouse.backend.auction.domain.ImportBatchStatus;
import java.time.LocalDateTime;

public record ImportBatchResponse(Long id, String fileName, Integer rowCount, ImportBatchStatus status, String memo, LocalDateTime importedAt) {
	public static ImportBatchResponse from(ImportBatch batch) { return new ImportBatchResponse(batch.getId(), batch.getFileName(), batch.getRowCount(), batch.getStatus(), batch.getMemo(), batch.getCreatedAt()); }
}
