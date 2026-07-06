package com.greenhouse.backend.settlement.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auction-settlements")
public class AuctionSettlementController {
	private final AuctionSettlementService settlementService;

	public AuctionSettlementController(AuctionSettlementService settlementService) {
		this.settlementService = settlementService;
	}

	@GetMapping
	public ApiResponse<List<AuctionSettlementResponse>> getSettlements(
		@RequestParam(required = false) Long auctionHouseId,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to,
		@RequestParam(required = false) AuctionSettlementStatus status
	) {
		return ApiResponse.ok(settlementService.getSettlements(auctionHouseId, from, to, status));
	}

	@GetMapping("/{settlementId}")
	public ApiResponse<AuctionSettlementResponse> getSettlement(@PathVariable Long settlementId) {
		return ApiResponse.ok(settlementService.getSettlement(settlementId));
	}

	@PostMapping("/rebuild")
	public ApiResponse<AuctionSettlementResponse> rebuild(
		@RequestParam Long auctionHouseId,
		@RequestParam LocalDate auctionDate
	) {
		return ApiResponse.ok(settlementService.rebuild(auctionHouseId, auctionDate));
	}
}
