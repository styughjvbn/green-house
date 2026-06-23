package com.greenhouse.backend.farm.dto;

public record HouseStatusSummaryResponse(
	Long houseId,
	Integer houseNumber,
	String houseName,
	long orchidGroupCount,
	long warningCount,
	long repotDueCount,
	String latestWorkDate
) {
}
