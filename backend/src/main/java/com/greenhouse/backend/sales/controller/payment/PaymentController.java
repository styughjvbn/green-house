package com.greenhouse.backend.sales.controller.payment;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.application.payment.PartnerBalanceService;
import com.greenhouse.backend.sales.application.payment.PaymentService;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.dto.payment.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.sales.dto.payment.PartnerPaymentEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

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
