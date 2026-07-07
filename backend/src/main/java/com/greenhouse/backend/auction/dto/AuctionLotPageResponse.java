package com.greenhouse.backend.auction.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record AuctionLotPageResponse(
		List<AuctionLotResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
	public static AuctionLotPageResponse from(Page<AuctionLotResponse> result) {
		return new AuctionLotPageResponse(
				result.getContent(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}
}
