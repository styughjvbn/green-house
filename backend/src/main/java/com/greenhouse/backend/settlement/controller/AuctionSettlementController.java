package com.greenhouse.backend.settlement.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.dto.AuctionSettlementListItemResponse;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.dto.AuctionSettlementSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auction-settlements")
@RequiredArgsConstructor
public class AuctionSettlementController {
	private final AuctionSettlementService settlementService;

	@GetMapping
	@Operation(deprecated = true, description = "호환용 목록. 최신 500건만 반환합니다. 운영 목록은 /page, 전체 합계는 /summary를 사용합니다.")
	public ApiResponse<List<AuctionSettlementResponse>> getSettlements(
			@RequestParam(required = false) Long auctionHouseId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) AuctionSettlementStatus status) {
		return ApiResponse.ok(settlementService.getSettlements(auctionHouseId, from, to, status));
	}

	@GetMapping("/page")
	@Operation(operationId = "getAuctionSettlementPage", description = "정산 요약의 서버 페이지 목록. page는 0 이상, size는 1~100으로 보정합니다. 상세 행은 단건 API에서 조회합니다.")
	public ApiResponse<PageResponse<AuctionSettlementListItemResponse>> getSettlementPage(
			@RequestParam(required = false) Long auctionHouseId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) AuctionSettlementStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(settlementService.getSettlementPage(auctionHouseId, from, to, status, page, size));
	}

	@GetMapping("/summary")
	@Operation(operationId = "getAuctionSettlementSummary", description = "조회 조건에 해당하는 전체 정산의 합계. 페이지 크기와 무관하게 집계합니다.")
	public ApiResponse<AuctionSettlementSummaryResponse> getSummary(
			@RequestParam(required = false) Long auctionHouseId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) AuctionSettlementStatus status) {
		return ApiResponse.ok(settlementService.getSummary(auctionHouseId, from, to, status));
	}

	@GetMapping("/{settlementId}")
	public ApiResponse<AuctionSettlementResponse> getSettlement(@PathVariable Long settlementId) {
		return ApiResponse.ok(settlementService.getSettlement(settlementId));
	}

	@PostMapping("/rebuild")
	public ApiResponse<AuctionSettlementResponse> rebuild(
			@RequestParam Long auctionHouseId,
			@RequestParam LocalDate auctionDate) {
		return ApiResponse.ok(settlementService.rebuild(auctionHouseId, auctionDate));
	}
}
