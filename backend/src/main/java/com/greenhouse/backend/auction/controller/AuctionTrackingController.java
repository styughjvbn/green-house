package com.greenhouse.backend.auction.controller;

import com.greenhouse.backend.auction.application.AuctionImportService;
import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.auction.dto.AuctionLotResponse;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.dto.AuctionLotStatusRequest;
import com.greenhouse.backend.auction.dto.AuctionTrackingSummaryResponse;
import com.greenhouse.backend.auction.dto.ImportBatchResponse;
import com.greenhouse.backend.auction.dto.ImportRowResponse;
import com.greenhouse.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AuctionTrackingController {
	private final AuctionImportService importService;
	private final AuctionTrackingService trackingService;

	public AuctionTrackingController(AuctionImportService importService, AuctionTrackingService trackingService) { this.importService = importService; this.trackingService = trackingService; }

	@PostMapping(value = "/auction-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ImportBatchResponse> importCsv(@RequestPart("file") MultipartFile file) { return ApiResponse.ok(importService.importCsv(file)); }

	@GetMapping("/auction-imports/{id}")
	public ApiResponse<ImportBatchResponse> getImport(@PathVariable Long id) { return ApiResponse.ok(importService.getBatch(id)); }

	@GetMapping("/auction-imports/{id}/rows")
	public ApiResponse<List<ImportRowResponse>> getImportRows(@PathVariable Long id) { return ApiResponse.ok(importService.getRows(id)); }

	@GetMapping("/auction-lots")
	public ApiResponse<List<AuctionLotResponse>> getLots(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String market, @RequestParam(required = false) String variety, @RequestParam(required = false) String grade, @RequestParam(required = false) AuctionLotStatus status, @RequestParam(required = false) Boolean reviewOnly, @RequestParam(required = false) Boolean returnOnly, @RequestParam(required = false) Boolean waitingOnly, @RequestParam(required = false) String keyword) {
		return ApiResponse.ok(trackingService.getLots(from, to, market, variety, grade, status, reviewOnly, returnOnly, waitingOnly, keyword));
	}

	@GetMapping("/auction-lots/{id}")
	public ApiResponse<AuctionLotResponse> getLot(@PathVariable Long id) { return ApiResponse.ok(trackingService.getLot(id)); }

	@GetMapping("/auction-lots/{id}/timeline")
	public ApiResponse<AuctionLotResponse> getTimeline(@PathVariable Long id) { return ApiResponse.ok(trackingService.getLot(id)); }

	@GetMapping("/auction-tracking/summary")
	public ApiResponse<AuctionTrackingSummaryResponse> getSummary() { return ApiResponse.ok(trackingService.getSummary()); }

	@PostMapping("/auction-lots/{id}/confirm-return")
	public ApiResponse<AuctionLotResponse> confirmReturn(@PathVariable Long id, @Valid @RequestBody AuctionLotReturnRequest request) { return ApiResponse.ok(trackingService.confirmReturn(id, request)); }

	@PostMapping("/auction-lots/{id}/adjust-quantity")
	public ApiResponse<AuctionLotResponse> adjust(@PathVariable Long id, @Valid @RequestBody AuctionLotAdjustmentRequest request) { return ApiResponse.ok(trackingService.adjust(id, request)); }

	@PatchMapping("/auction-lots/{id}/status")
	public ApiResponse<AuctionLotResponse> changeStatus(@PathVariable Long id, @Valid @RequestBody AuctionLotStatusRequest request) { return ApiResponse.ok(trackingService.changeStatus(id, request)); }
}
