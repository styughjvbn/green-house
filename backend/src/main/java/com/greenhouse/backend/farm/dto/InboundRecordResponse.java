package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.InboundRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InboundRecordResponse(
		Long id,
		LocalDate inboundDate,
		String inboundType,
		Long varietyId,
		String genus,
		String varietyName,
		String status,
		Integer bottleCount,
		Integer estimatedQuantity,
		Integer actualQuantity,
		String tempLocation,
		LocalDate pottingDueDate,
		LocalDate pottingDate,
		String potSize,
		Integer ageYear,
		String growthStage,
		String placementType,
		Integer trayCount,
		Long bedZoneId,
		String currentLocation,
		Long createdOrchidGroupId,
		String worker,
		String memo,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static InboundRecordResponse from(InboundRecord record) {
		return new InboundRecordResponse(
				record.getId(),
				record.getInboundDate(),
				record.getInboundType().name(),
				record.getVariety().getId(),
				record.getVariety().getGenus(),
				record.getVariety().getName(),
				record.getStatus().name(),
				record.getBottleCount(),
				record.getEstimatedQuantity(),
				record.getActualQuantity(),
				record.getTempLocation(),
				record.getPottingDueDate(),
				record.getPottingDate(),
				record.getPotSize(),
				record.getAgeYear(),
				record.getGrowthStage(),
				record.getPlacementType(),
				record.getTrayCount(),
				record.getBedZone() == null ? null : record.getBedZone().getId(),
				formatLocation(record.getBedZone(), record.getTempLocation()),
				record.getCreatedOrchidGroup() == null ? null : record.getCreatedOrchidGroup().getId(),
				record.getWorker(),
				record.getMemo(),
				record.getCreatedAt(),
				record.getUpdatedAt());
	}

	private static String formatLocation(BedZone bedZone, String tempLocation) {
		if (bedZone != null) {
			return "%d동-%d배드 %s".formatted(
					bedZone.getPhysicalBed().getHouse().getNumber(),
					bedZone.getPhysicalBed().getNumber(),
					bedZone.getName());
		}
		return tempLocation;
	}
}
