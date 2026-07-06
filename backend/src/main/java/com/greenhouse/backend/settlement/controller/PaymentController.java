package com.greenhouse.backend.settlement.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.settlement.application.PaymentService;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.dto.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.settlement.dto.PartnerPaymentEventResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {
	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/auction-settlements/{settlementId}/confirm-payment")
	public ApiResponse<AuctionSettlementResponse> confirmAuctionPayment(
		@PathVariable Long settlementId,
		@Valid @RequestBody ManualPaymentRequest request
	) {
		return ApiResponse.ok(paymentService.confirmAuctionPayment(settlementId, request));
	}

	@PostMapping("/sales-slips/{salesSlipId}/confirm-payment")
	public ApiResponse<SalesSlipResponse> confirmSalesSlipPayment(
		@PathVariable Long salesSlipId,
		@Valid @RequestBody ManualPaymentRequest request
	) {
		return ApiResponse.ok(paymentService.confirmSalesSlipPayment(salesSlipId, request));
	}

	@GetMapping("/partner-payment-events")
	public ApiResponse<List<PartnerPaymentEventResponse>> getEvents(
		@RequestParam(required = false) Long partnerId,
		@RequestParam(required = false) PaymentTargetType targetType,
		@RequestParam(required = false) Long targetId
	) {
		return ApiResponse.ok(paymentService.getEvents(partnerId, targetType, targetId));
	}

	@GetMapping("/business-partners/{partnerId}/balance-summary")
	public ApiResponse<PartnerBalanceSummaryResponse> getBalance(@PathVariable Long partnerId) {
		return ApiResponse.ok(paymentService.getBalance(partnerId));
	}
}
