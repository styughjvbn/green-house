package com.greenhouse.backend.farm.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record InboundRecordPageResponse(
		List<InboundRecordResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
	public static InboundRecordPageResponse from(Page<InboundRecordResponse> result) {
		return new InboundRecordPageResponse(
				result.getContent(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}
}
