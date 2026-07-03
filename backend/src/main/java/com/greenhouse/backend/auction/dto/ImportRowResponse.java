package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.ImportRow;

public record ImportRowResponse(Long id, Integer rowNumber, String rawDataJson, String normalizedDataJson, AuctionInspectionStatus validationStatus, String matchedEntityType, Long matchedEntityId, String errorMessage) {
	public static ImportRowResponse from(ImportRow row) { return new ImportRowResponse(row.getId(), row.getRowNumber(), row.getRawDataJson(), row.getNormalizedDataJson(), row.getValidationStatus(), row.getMatchedEntityType(), row.getMatchedEntityId(), row.getErrorMessage()); }
}
