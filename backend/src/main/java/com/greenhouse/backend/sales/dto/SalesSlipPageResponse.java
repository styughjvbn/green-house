package com.greenhouse.backend.sales.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record SalesSlipPageResponse(
		List<SalesSlipListItemResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static SalesSlipPageResponse from(Page<SalesSlipListItemResponse> page) {
		return new SalesSlipPageResponse(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}
}
