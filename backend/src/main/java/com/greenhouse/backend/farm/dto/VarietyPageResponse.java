package com.greenhouse.backend.farm.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record VarietyPageResponse(
		List<VarietyResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
	public static VarietyPageResponse from(Page<VarietyResponse> result) {
		return new VarietyPageResponse(
				result.getContent(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}
}
