package com.greenhouse.backend.sales.controller.auction;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsPaymentService;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsReader;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.dto.auction.AuctionProceedsResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auction-proceeds")
@RequiredArgsConstructor
public class AuctionProceedsController {
  private final AuctionProceedsReader reader;
  private final AuctionProceedsPaymentService payments;

  @GetMapping("/page")
  public ApiResponse<PageResponse<AuctionProceedsResponse>> page(
      @RequestParam(required = false) Long auctionHouseId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(reader.page(auctionHouseId, page, size));
  }

  @GetMapping("/{id}")
  public ApiResponse<AuctionProceedsResponse> get(@PathVariable Long id) {
    return ApiResponse.ok(reader.get(id));
  }

  @PostMapping("/{id}/confirm-payment")
  @Operation(
      description = "자료 연결과 받을 금액이 확인된 경매 대금에 입금을 배분합니다. 같은 키·금액·입금일은 중복 수납 없이 현재 조회를 반환합니다.")
  public ApiResponse<AuctionProceedsResponse> confirmPayment(
      @PathVariable Long id, @Valid @RequestBody ManualPaymentCommand command) {
    return ApiResponse.ok(payments.confirm(id, command));
  }
}
