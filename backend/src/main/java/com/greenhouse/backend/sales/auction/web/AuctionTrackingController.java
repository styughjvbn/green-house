package com.greenhouse.backend.sales.auction.web;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.auction.application.AuctionArrivalCommand;
import com.greenhouse.backend.sales.auction.application.AuctionFollowUpCommand;
import com.greenhouse.backend.sales.auction.application.AuctionFollowUpService;
import com.greenhouse.backend.sales.auction.application.AuctionTrackingService;
import com.greenhouse.backend.sales.auction.application.CancelAuctionArrivalCommand;
import com.greenhouse.backend.sales.auction.application.RecordAuctionResultCommand;
import com.greenhouse.backend.sales.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.sales.auction.web.dto.AuctionArrivalResponse;
import com.greenhouse.backend.sales.auction.web.dto.AuctionFollowUpResponse;
import com.greenhouse.backend.sales.auction.web.dto.AuctionFollowUpResult;
import com.greenhouse.backend.sales.auction.web.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.sales.auction.web.dto.AuctionLotResponse;
import com.greenhouse.backend.sales.auction.web.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.sales.auction.web.dto.AuctionLotStatusRequest;
import com.greenhouse.backend.sales.auction.web.dto.AuctionTrackingSummaryResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuctionTrackingController {

  private final AuctionTrackingService trackingService;
  private final AuctionFollowUpService followUpService;

  @GetMapping("/auction-lots/{id}/follow-up")
  public ApiResponse<AuctionFollowUpResponse> getFollowUp(@PathVariable Long id) {
    return ApiResponse.ok(followUpService.getFollowUp(id));
  }

  @PostMapping("/auction-lots/{id}/follow-up")
  public ApiResponse<AuctionFollowUpResult> decideFollowUp(
      @PathVariable Long id, @Valid @RequestBody AuctionFollowUpCommand request) {
    return ApiResponse.ok(followUpService.decide(id, request));
  }

  @GetMapping("/auction-lots/{id}/arrivals")
  public ApiResponse<PageResponse<AuctionArrivalResponse>> getArrivals(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(followUpService.getArrivals(id, page, size));
  }

  @PostMapping("/auction-lots/{id}/arrivals")
  public ApiResponse<AuctionFollowUpResult> recordArrival(
      @PathVariable Long id, @Valid @RequestBody AuctionArrivalCommand request) {
    return ApiResponse.ok(followUpService.arrive(id, request));
  }

  @PostMapping("/auction-lots/{id}/arrivals/{arrivalId}/cancel")
  public ApiResponse<AuctionFollowUpResult> cancelArrival(
      @PathVariable Long id,
      @PathVariable Long arrivalId,
      @Valid @RequestBody CancelAuctionArrivalCommand request) {
    return ApiResponse.ok(followUpService.cancelArrival(id, arrivalId, request));
  }

  @GetMapping("/auction-lots")
  public ApiResponse<PageResponse<AuctionLotResponse>> getLots(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String market,
      @RequestParam(required = false) String variety,
      @RequestParam(required = false) String grade,
      @RequestParam(required = false) AuctionLotStatus status,
      @RequestParam(required = false) Boolean reviewOnly,
      @RequestParam(required = false) Boolean returnOnly,
      @RequestParam(required = false) Boolean waitingOnly,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        trackingService.getLots(
            from,
            to,
            market,
            variety,
            grade,
            status,
            reviewOnly,
            returnOnly,
            waitingOnly,
            keyword,
            page,
            size));
  }

  @GetMapping("/auction-lots/{id}")
  public ApiResponse<AuctionLotResponse> getLot(@PathVariable Long id) {
    return ApiResponse.ok(trackingService.getLot(id));
  }

  @GetMapping("/auction-lots/{id}/timeline")
  public ApiResponse<AuctionLotResponse> getTimeline(@PathVariable Long id) {
    return ApiResponse.ok(trackingService.getLot(id));
  }

  @GetMapping("/auction-tracking/summary")
  public ApiResponse<AuctionTrackingSummaryResponse> getSummary() {
    return ApiResponse.ok(trackingService.getSummary());
  }

  @PostMapping("/auction-lots/{id}/confirm-return")
  public ApiResponse<AuctionLotResponse> confirmReturn(
      @PathVariable Long id, @Valid @RequestBody AuctionLotReturnRequest request) {
    return ApiResponse.ok(trackingService.confirmReturn(id, request));
  }

  @PostMapping("/auction-lots/{id}/adjust-quantity")
  public ApiResponse<AuctionLotResponse> adjust(
      @PathVariable Long id, @Valid @RequestBody AuctionLotAdjustmentRequest request) {
    return ApiResponse.ok(trackingService.adjust(id, request));
  }

  @PostMapping("/auction-lots/{id}/results")
  public ApiResponse<AuctionLotResponse> addResult(
      @PathVariable Long id, @Valid @RequestBody RecordAuctionResultCommand request) {
    return ApiResponse.ok(trackingService.addResult(id, request));
  }

  @PatchMapping("/auction-lots/{id}/status")
  public ApiResponse<AuctionLotResponse> changeStatus(
      @PathVariable Long id, @Valid @RequestBody AuctionLotStatusRequest request) {
    return ApiResponse.ok(trackingService.changeStatus(id, request));
  }
}
