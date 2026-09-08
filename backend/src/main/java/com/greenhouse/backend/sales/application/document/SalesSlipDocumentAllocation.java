package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SalesSlipItemAllocationResponse")
public record SalesSlipDocumentAllocation(
		Long id,
		Long orchidGroupId,
		String varietyName,
		Integer allocatedQuantity,
		Integer availableQuantity,
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName,
		SalesOrchidGroupSnapshotData creationSnapshot,
		SalesOrchidGroupSnapshotData outboundSnapshot) {

	public static SalesSlipDocumentAllocation from(SalesSlipItemAllocation allocation, OrchidGroupState group) {
		return new SalesSlipDocumentAllocation(
				allocation.getId(),
				group.id(),
				group.varietyName(),
				allocation.getAllocatedQuantity(),
				group.availableQuantity(),
				group.houseNumber(),
				group.physicalBedNumber(),
				group.bedZoneName(),
				SalesOrchidGroupSnapshotData.from(allocation.findSnapshot(SalesOrchidSnapshotType.CREATION)),
				SalesOrchidGroupSnapshotData.from(allocation.findSnapshot(SalesOrchidSnapshotType.OUTBOUND)));
	}
}
