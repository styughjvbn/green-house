package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import com.greenhouse.backend.settlement.application.PaymentLedgerService;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesPaymentService {
	private final SalesSlipRepository salesSlipRepository;
	private final PaymentLedgerService paymentLedgerService;
	private final PartnerBalanceService partnerBalanceService;

	public SalesPaymentService(
		SalesSlipRepository salesSlipRepository,
		PaymentLedgerService paymentLedgerService,
		PartnerBalanceService partnerBalanceService
	) {
		this.salesSlipRepository = salesSlipRepository;
		this.paymentLedgerService = paymentLedgerService;
		this.partnerBalanceService = partnerBalanceService;
	}

	public SalesSlipResponse confirmPayment(Long salesSlipId, ManualPaymentRequest request) {
		var salesSlip = salesSlipRepository.findWithDetailsById(salesSlipId)
			.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		if (salesSlip.getSalesType() != SalesType.DIRECT) {
			throw new IllegalArgumentException("경매 판매전표는 경매장 정산에서 입금을 확인해야 합니다.");
		}

		salesSlip.recordPayment(request.amount());
		var saved = salesSlipRepository.save(salesSlip);
		var received = paymentLedgerService.recordManualPayment(
			salesSlip.getPartner(), PaymentTargetType.SALES_SLIP, salesSlipId, request);
		partnerBalanceService.updateReceivable(
			salesSlip.getPartner().getId(),
			salesSlipRepository.sumDirectReceivableByPartnerId(salesSlip.getPartner().getId()),
			received);
		return SalesSlipResponse.from(saved);
	}
}
