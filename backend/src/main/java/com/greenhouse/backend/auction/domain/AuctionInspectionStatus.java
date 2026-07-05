package com.greenhouse.backend.auction.domain;

public enum AuctionInspectionStatus {
	NORMAL,
	AUTO_MATCHED,
	CORRECTED_MATCH,
	MANUAL_REVIEW,
	MATCH_FAILED,
	QUANTITY_MISMATCH,
	RETURN_INFERRED,
	SOURCE_ERROR
}
