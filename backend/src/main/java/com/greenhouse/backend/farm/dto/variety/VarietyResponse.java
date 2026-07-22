package com.greenhouse.backend.farm.dto.variety;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.variety.Variety;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record VarietyResponse(
		Long id,
		String code,
		String genus,
		String name,
		String alias,
		String defaultPotSize,
		boolean saleEnabled,
		boolean active,
		String description,
		String memo,
		long connectedGroupCount,
		long totalQuantity,
		long saleableQuantity,
		LocalDate recentInboundDate,
		LocalDate recentWorkDate,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	public static VarietyResponse from(
			Variety variety,
			long connectedGroupCount,
			long totalQuantity,
			long saleableQuantity,
			LocalDate recentInboundDate,
			LocalDate recentWorkDate) {
		return new VarietyResponse(
				variety.getId(),
				variety.getCode(),
				variety.getGenus(),
				variety.getName(),
				variety.getAlias(),
				variety.getDefaultPotSize(),
				variety.isSaleEnabled(),
				variety.isActive(),
				variety.getDescription(),
				variety.getMemo(),
				connectedGroupCount,
				totalQuantity,
				saleableQuantity,
				recentInboundDate,
				recentWorkDate,
				TimeConfig.toFarmTime(variety.getCreatedAt()),
				TimeConfig.toFarmTime(variety.getUpdatedAt()));
	}
}
