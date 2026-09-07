package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;

public record SalesSlipItemAllocationResponse(
		Long id,
		Long orchidGroupId,
		String varietyName,
		Integer allocatedQuantity,
		Integer availableQuantity,
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName,
		SalesOrchidGroupSnapshotResponse creationSnapshot,
		SalesOrchidGroupSnapshotResponse outboundSnapshot) {

	public static SalesSlipItemAllocationResponse from(SalesSlipItemAllocation allocation, OrchidGroupState group) {
		return new SalesSlipItemAllocationResponse(
				allocation.getId(),
				group.id(),
				group.varietyName(),
				allocation.getAllocatedQuantity(),
				group.availableQuantity(),
				group.houseNumber(),
				group.physicalBedNumber(),
				group.bedZoneName(),
				SalesOrchidGroupSnapshotResponse.from(allocation.findSnapshot(SalesOrchidSnapshotType.CREATION)),
				SalesOrchidGroupSnapshotResponse.from(allocation.findSnapshot(SalesOrchidSnapshotType.OUTBOUND)));
	}
}
