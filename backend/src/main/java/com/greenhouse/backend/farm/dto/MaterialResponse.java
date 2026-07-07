package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.Material;
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
				material.getCreatedAt(),
				material.getUpdatedAt());
	}
}
