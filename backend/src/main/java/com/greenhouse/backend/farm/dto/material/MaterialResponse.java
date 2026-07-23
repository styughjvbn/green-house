package com.greenhouse.backend.farm.dto.material;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.material.Material;
import java.time.LocalDateTime;

public record MaterialResponse(
		Long id,
		String code,
		String category,
		String name,
		String manufacturer,
		String specification,
		String stockQuantity,
		String storageLocation,
		String usage,
		boolean active,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	public static MaterialResponse from(Material material) {
		return new MaterialResponse(
				material.getId(),
				material.getCode(),
				material.getCategory(),
				material.getName(),
				material.getManufacturer(),
				material.getSpecification(),
				material.getStockQuantity(),
				material.getStorageLocation(),
				material.getUsage(),
				material.isActive(),
				TimeConfig.toFarmTime(material.getCreatedAt()),
				TimeConfig.toFarmTime(material.getUpdatedAt()));
	}
}
