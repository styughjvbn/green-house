package com.greenhouse.backend.farm.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record MaterialPageResponse(
		List<MaterialResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
	public static MaterialPageResponse from(Page<MaterialResponse> result) {
		return new MaterialPageResponse(
				result.getContent(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}
}
