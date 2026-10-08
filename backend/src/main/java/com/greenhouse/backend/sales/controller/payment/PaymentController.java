package com.greenhouse.backend.sales.controller.payment;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.application.payment.PartnerBalanceService;
import com.greenhouse.backend.sales.application.payment.PaymentAllocationService;
import com.greenhouse.backend.sales.application.payment.PaymentReceiptReader;
import com.greenhouse.backend.sales.application.payment.PaymentService;
import com.greenhouse.backend.sales.application.payment.UnassignedReceiptService;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.dto.payment.CancelUnassignedReceiptRequest;
import com.greenhouse.backend.sales.dto.payment.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.sales.dto.payment.PartnerPaymentEventResponse;
import com.greenhouse.backend.sales.dto.payment.PaymentAllocationChangeResponse;
import com.greenhouse.backend.sales.dto.payment.PaymentAllocationCorrectionRequest;
import com.greenhouse.backend.sales.dto.payment.PaymentAllocationMetadata;
import com.greenhouse.backend.sales.dto.payment.PaymentAllocationRequest;
import com.greenhouse.backend.sales.dto.payment.PaymentAllocationResponse;
import com.greenhouse.backend.sales.dto.payment.PaymentReceiptResponse;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentAllocationTargetOption;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentAllocationService allocations;
  private final PaymentReceiptReader receipts;

  @GetMapping("/payment-allocation-metadata")
  @Operation(operationId = "getPaymentAllocationMetadata")
  public ApiResponse<PaymentAllocationMetadata> allocationMetadata() {
    return ApiResponse.ok(receipts.metadata());
  }

  @PostMapping("/business-partners/{partnerId}/payment-allocations")
  public ApiResponse<PaymentAllocationChangeResponse> allocate(
      @PathVariable Long partnerId, @Valid @RequestBody PaymentAllocationRequest request) {
    return ApiResponse.ok(allocations.allocate(partnerId, request));
  }

  @PostMapping("/business-partners/{partnerId}/payment-allocation-corrections")
  public ApiResponse<PaymentAllocationChangeResponse> correctAllocations(
      @PathVariable Long partnerId,
      @Valid @RequestBody PaymentAllocationCorrectionRequest request) {
    return ApiResponse.ok(allocations.correct(partnerId, request));
  }

  @GetMapping("/business-partners/{partnerId}/payment-receipts")
  public ApiResponse<PageResponse<PaymentReceiptResponse>> receiptPage(
      @PathVariable Long partnerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(receipts.page(partnerId, page, size));
  }

  @GetMapping("/business-partners/{partnerId}/payment-receipts/{receiptId}")
  public ApiResponse<PaymentReceiptResponse> receipt(
      @PathVariable Long partnerId, @PathVariable Long receiptId) {
    return ApiResponse.ok(receipts.get(partnerId, receiptId));
  }

  @GetMapping("/business-partners/{partnerId}/payment-receipts/{receiptId}/allocations")
  public ApiResponse<PageResponse<PaymentAllocationResponse>> receiptAllocations(
      @PathVariable Long partnerId,
      @PathVariable Long receiptId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(receipts.allocations(partnerId, receiptId, page, size));
  }

  @GetMapping("/business-partners/{partnerId}/payment-allocation-targets")
  public ApiResponse<PageResponse<PaymentAllocationTargetOption>> allocationTargets(
      @PathVariable Long partnerId,
      @RequestParam PaymentTargetType targetType,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(receipts.options(partnerId, targetType, keyword, page, size));
  }

  private final PaymentService paymentService;
  private final UnassignedReceiptService unassignedReceiptService;

  @PostMapping("/business-partners/{partnerId}/payment-receipts")
  @Operation(
      description = "대상 미지정 수동 수납. 유효 배분과 전표 입금액은 변경하지 않습니다.",
      operationId = "receiveUnassignedPayment")
  public ApiResponse<PartnerPaymentEventResponse> receiveUnassigned(
      @PathVariable Long partnerId, @Valid @RequestBody ManualPaymentCommand request) {
    return ApiResponse.ok(unassignedReceiptService.receive(partnerId, request));
  }

  @PostMapping("/business-partners/{partnerId}/payment-receipts/{receiptId}/cancel")
  @Operation(
      description = "미배분 수납의 오입력 취소. 실제 환불을 기록하지 않습니다.",
      operationId = "cancelUnassignedPayment")
  public ApiResponse<PartnerPaymentEventResponse> cancelUnassigned(
      @PathVariable Long partnerId,
      @PathVariable Long receiptId,
      @Valid @RequestBody CancelUnassignedReceiptRequest request) {
    return ApiResponse.ok(unassignedReceiptService.cancel(partnerId, receiptId, request));
  }

  private final PartnerBalanceService partnerBalanceService;

  @GetMapping("/partner-payment-events")
  @Operation(
      deprecated = true,
      description =
          "호환용 목록. 입금일·ID 역순으로 최신 500건까지 반환합니다. 운영 화면은 /partner-payment-events/page를 사용합니다.")
  public ApiResponse<List<PartnerPaymentEventResponse>> getEvents(
      @RequestParam(required = false) Long partnerId,
      @RequestParam(required = false) PaymentTargetType targetType,
      @RequestParam(required = false) Long targetId) {
    return ApiResponse.ok(paymentService.getEvents(partnerId, targetType, targetId));
  }

  @GetMapping("/partner-payment-events/page")
  @Operation(
      operationId = "getPaymentEventPage",
      description =
          "조건에 맞는 입금 이벤트를 입금일·ID 역순으로 조회합니다. 유형 필터는 페이지 조회 전 적용됩니다. page는 0 이상, size는 1~100으로 보정합니다.")
  public ApiResponse<PageResponse<PartnerPaymentEventResponse>> getEventPage(
      @RequestParam(required = false) Long partnerId,
      @RequestParam(required = false) PaymentTargetType targetType,
      @RequestParam(required = false) Long targetId,
      @RequestParam(required = false) PaymentEventType eventType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.ok(
        paymentService.getEventPage(partnerId, targetType, targetId, eventType, page, size));
  }

  @GetMapping("/business-partners/{partnerId}/balance-summary")
  public ApiResponse<PartnerBalanceSummaryResponse> getBalance(@PathVariable Long partnerId) {
    return ApiResponse.ok(partnerBalanceService.getBalance(partnerId));
  }
}
