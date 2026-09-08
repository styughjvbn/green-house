package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.sales.domain.SalesOrchidGroupSnapshot;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotSource;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "SalesOrchidGroupSnapshotResponse")
public record SalesOrchidGroupSnapshotData(
		SalesOrchidSnapshotType snapshotType,
		SalesOrchidSnapshotSource captureSource,
		LocalDateTime capturedAt,
		Long orchidGroupId,
		Long varietyId,
		String varietyName,
		String genus,
		Integer ageYear,
		String potSizeCode,
		String potSize,
		Integer quantity,
		Integer reservedQuantity,
		String status,
		Integer allocatedQuantity,
		Long houseId,
		Integer houseNumber,
		Long physicalBedId,
		Integer physicalBedNumber,
		Long bedZoneId,
		String bedZoneName,
		BigDecimal startPosition,
		BigDecimal endPosition) {

	public static SalesOrchidGroupSnapshotData from(SalesOrchidGroupSnapshot snapshot) {
		if (snapshot == null) {
			return null;
		}
		return new SalesOrchidGroupSnapshotData(
				snapshot.getSnapshotType(),
				snapshot.getCaptureSource(),
				TimeConfig.toFarmTime(snapshot.getCapturedAt()),
				snapshot.getOrchidGroupId(),
				snapshot.getVarietyId(),
				snapshot.getVarietyName(),
				snapshot.getGenus(),
				snapshot.getAgeYear(),
				snapshot.getPotSizeCode(),
				snapshot.getPotSize(),
				snapshot.getQuantity(),
				snapshot.getReservedQuantity(),
				snapshot.getStatus(),
				snapshot.getAllocatedQuantity(),
				snapshot.getHouseId(),
				snapshot.getHouseNumber(),
				snapshot.getPhysicalBedId(),
				snapshot.getPhysicalBedNumber(),
				snapshot.getBedZoneId(),
				snapshot.getBedZoneName(),
				snapshot.getStartPosition(),
				snapshot.getEndPosition());
	}
}
