package com.greenhouse.backend.sales.controller.document;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.ErrorResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.application.direct.SalesPaymentService;
import com.greenhouse.backend.sales.application.document.SalesOrchidGroupQueryService;
import com.greenhouse.backend.sales.application.document.SalesQueryService;
import com.greenhouse.backend.sales.application.document.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.application.document.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.document.SalesSlipSummary;
import com.greenhouse.backend.sales.application.document.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.dto.document.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.document.SalesOrchidGroupSearchResponse;
import com.greenhouse.backend.sales.dto.document.SalesSlipStatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SalesController {

  private final SalesQueryService salesQueryService;

  private final SalesSlipCreationService salesSlipCreationService;

  private final SalesSlipUpdateService salesSlipUpdateService;

  private final SalesPaymentService salesPaymentService;

  private final SalesSlipStatusService salesSlipStatusService;

  private final SalesOrchidGroupQueryService salesOrchidGroupQueryService;

  /**
   * @deprecated Use {@code GET /api/sales-slips/page}.
   */
  @Deprecated(since = "2026-08", forRemoval = false)
  @Operation(
      description = "일반 판매 금액은 Direct 거래·가격과 실제 유효 배분에서 조회합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 결과",
            useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "DIRECT_AMOUNT_SOURCE_MISSING: 일반 판매 금액 자료 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
      })
  @GetMapping("/sales-slips")
  public ApiResponse<List<SalesSlipDocument>> getSalesSlips(
      @RequestParam(required = false) Long partnerId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return ApiResponse.ok(salesQueryService.getSalesSlips(partnerId, from, to));
  }

  @Operation(
      description = "일반 판매 금액은 Direct 거래·가격과 실제 유효 배분에서 조회합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 결과",
            useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "DIRECT_AMOUNT_SOURCE_MISSING: 일반 판매 금액 자료 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
      })
  @GetMapping("/sales-slips/page")
  public ApiResponse<PageResponse<SalesSlipSummary>> getSalesSlipPage(
      @RequestParam(required = false) Long partnerId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String paymentStatus,
      @RequestParam(required = false) String salesStatus,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(
        salesQueryService.getSalesSlipPage(
            partnerId, from, to, paymentStatus, salesStatus, keyword, page, size));
  }

  @Operation(
      description = "일반 판매 금액은 Direct 거래·가격과 실제 유효 배분에서 조회합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 결과",
            useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "DIRECT_AMOUNT_SOURCE_MISSING: 일반 판매 금액 자료 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
      })
  @GetMapping("/sales-slips/{salesSlipId}")
  public ApiResponse<SalesSlipDocument> getSalesSlip(@PathVariable Long salesSlipId) {
    return ApiResponse.ok(salesQueryService.getSalesSlip(salesSlipId));
  }

  @GetMapping("/sales-slips/auction-shipments")
  public ApiResponse<List<AuctionShipmentOptionResponse>> getAuctionShipmentOptions() {
    return ApiResponse.ok(salesQueryService.getAuctionShipmentOptions());
  }

  @GetMapping("/sales/orchid-groups/search")
  public ApiResponse<List<SalesOrchidGroupSearchResponse>> searchSalesOrchidGroups(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long varietyId,
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(salesOrchidGroupQueryService.search(keyword, varietyId, status));
  }

  @PostMapping("/sales-slips")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SalesSlipDocument> createSalesSlip(
      @Valid @RequestBody SalesSlipCommand request,
      @Parameter(
              description = "생성 재전송은 같은 키와 입력을 유지합니다. 키 생략 시 별도 신규 전표를 생성합니다.",
              schema = @Schema(minLength = 1, maxLength = 100))
          @RequestHeader(name = "Idempotency-Key", required = false)
          String idempotencyKey) {
    return ApiResponse.ok(salesSlipCreationService.create(request, idempotencyKey));
  }

  @Operation(
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "수정 결과",
            useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description =
                "DIRECT_AMOUNT_REVIEW_REQUIRED: 금액·배분 검토 필요. DIRECT_AMOUNT_SOURCE_MISSING: 금액 자료 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
      })
  @PutMapping("/sales-slips/{salesSlipId}")
  public ApiResponse<SalesSlipDocument> updateSalesSlip(
      @PathVariable Long salesSlipId, @Valid @RequestBody SalesSlipCommand request) {
    return ApiResponse.ok(salesSlipUpdateService.update(salesSlipId, request));
  }

  @PostMapping("/sales-slips/{salesSlipId}/confirm-payment")
  @Operation(
      description =
          "대상별 같은 키·금액·입금일의 재요청은 기존 반영 결과를 반환합니다. 같은 키의 금액/입금일 변경은 409 IDEMPOTENCY_KEY_REUSED입니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "입금 확인 또는 재요청 결과",
            useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR: 입력 오류 또는 초과입금 등 기존 업무 검증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description =
                "IDEMPOTENCY_KEY_REUSED: 같은 대상·키의 금액 또는 입금일 변경. DIRECT_AMOUNT_REVIEW_REQUIRED: 금액·배분 검토 필요. DIRECT_AMOUNT_SOURCE_MISSING: 금액 자료 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
      })
  public ApiResponse<SalesSlipDocument> confirmPayment(
      @PathVariable Long salesSlipId, @Valid @RequestBody ManualPaymentCommand request) {
    return ApiResponse.ok(salesPaymentService.confirmPayment(salesSlipId, request));
  }

  @PatchMapping("/sales-slips/{salesSlipId}/sales-status")
  public ApiResponse<SalesSlipDocument> updateSalesStatus(
      @PathVariable Long salesSlipId, @Valid @RequestBody SalesSlipStatusUpdateRequest request) {
    return ApiResponse.ok(salesSlipStatusService.updateStatus(salesSlipId, request));
  }
}
