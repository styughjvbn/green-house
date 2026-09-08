package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.ManualPaymentCommand;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import com.greenhouse.backend.settlement.application.PaymentLedgerService;
import com.greenhouse.backend.settlement.application.SettlementAuditSupport;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesPaymentService {

	private final SalesSlipRepository salesSlipRepository;

	private final PaymentLedgerService paymentLedgerService;

	private final PartnerBalanceService partnerBalanceService;

	private final SettlementAuditSupport auditSupport;

	private final SalesSlipDocumentAssembler responseAssembler;

	public SalesSlipDocument confirmPayment(Long salesSlipId, ManualPaymentCommand payment) {
		var salesSlip = salesSlipRepository.findForUpdateById(salesSlipId)
			.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		salesSlip.validatePaymentTarget();
		partnerBalanceService.lockPartners(List.of(salesSlip.getPartnerId()));
		if (paymentLedgerService.findManualPayment(PaymentTargetType.SALES_SLIP, salesSlipId, payment).isPresent()) {
			return responseAssembler.assemble(salesSlip);
		}

		var before = auditSupport.paymentSnapshot(salesSlip.getPaidAmount(), salesSlip.getRemainingAmount(),
				salesSlip.getPaymentStatus());
		salesSlip.recordPayment(payment.amount());
		var saved = salesSlipRepository.save(salesSlip);
		var receivedEventId = paymentLedgerService.recordManualPayment(salesSlip.getPartnerId(),
				PaymentTargetType.SALES_SLIP, salesSlipId, payment);
		partnerBalanceService.updateReceivable(salesSlip.getPartnerId(),
				salesSlipRepository.sumDirectReceivableByPartnerId(salesSlip.getPartnerId()), receivedEventId);
		auditSupport.recordTargetPayment("SALES_SLIP", saved.getId(), saved.getPartnerId(),
				PaymentTargetType.SALES_SLIP, before, auditSupport.paymentSnapshot(saved.getPaidAmount(),
						saved.getRemainingAmount(), saved.getPaymentStatus()));
		return responseAssembler.assemble(saved);
	}

}
