package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;

public record SalesSlipItemAllocationResponse(
		Long id,
		Long orchidGroupId,
		String varietyName,
		Integer allocatedQuantity,
		Integer availableQuantity,
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName) {

	public static SalesSlipItemAllocationResponse from(SalesSlipItemAllocation allocation) {
		var group = allocation.getOrchidGroup();
		var zone = group.getBedZone();
		var bed = zone.getPhysicalBed();
		return new SalesSlipItemAllocationResponse(
				allocation.getId(),
				group.getId(),
				group.getVarietyName(),
				allocation.getAllocatedQuantity(),
				group.getAvailableQuantity(),
				bed.getHouse().getNumber(),
				bed.getNumber(),
				zone.getName());
	}
}
