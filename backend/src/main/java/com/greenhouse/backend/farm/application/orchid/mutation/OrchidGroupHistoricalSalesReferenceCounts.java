package com.greenhouse.backend.farm.application.orchid.mutation;

public record OrchidGroupHistoricalSalesReferenceCounts(
		long salesSlips,
		long salesItems,
		long groupAllocations,
		long inventoryMovements,
		long groupSnapshots) {
}
