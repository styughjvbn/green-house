package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.application.PaymentEventReader;
import com.greenhouse.backend.settlement.application.PartnerBalanceService;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.greenhouse.backend.audit.domain.AuditAction;
import java.util.Map;
import com.greenhouse.backend.sales.domain.SalesSlip;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesSlipStatusService {

	private final SalesSlipRepository salesSlipRepository;
	private final AuctionSalesSlipCancellationPolicy auctionSalesSlipCancellationPolicy;
	private final SalesSlipInventoryService salesSlipInventoryService;
	private final SalesSlipOutboundService salesSlipOutboundService;
	private final PaymentEventReader paymentEventReader;
	private final PartnerBalanceService partnerBalanceService;
	private final SalesSlipAuditSupport auditSupport;
	private final SalesSlipResponseAssembler responseAssembler;

	public SalesSlipResponse updateStatus(Long salesSlipId, SalesSlipStatusUpdateRequest request) {
		var salesSlip = salesSlipRepository.findForUpdateById(salesSlipId)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		String nextStatus = request.salesStatus().trim();
		if (salesSlip.isCanceled()) {
			throw new IllegalArgumentException("취소된 전표는 상태를 변경할 수 없습니다.");
		}
		if (nextStatus.equals(salesSlip.getSalesStatus())) {
			return responseAssembler.assemble(salesSlip);
		}
		Map<String, Object> before = auditSupport.snapshot(salesSlip);
		if (SalesSlip.STATUS_CANCELED.equals(nextStatus)) {
			cancel(salesSlip);
			auditSupport.record(AuditAction.DEACTIVATED, salesSlip, before, auditSupport.snapshot(salesSlip));
			return responseAssembler.assemble(salesSlip);
		}
		if (salesSlip.isOutboundCompleted()) {
			throw new IllegalArgumentException("출고 완료된 전표는 판매 상태를 변경할 수 없습니다.");
		}

		salesSlip.updateSalesStatus(nextStatus);
		if (salesSlip.isOutboundCompleted()) {
			salesSlipOutboundService.complete(salesSlip);
		}
		auditSupport.record(AuditAction.UPDATED, salesSlip, before, auditSupport.snapshot(salesSlip));
		return responseAssembler.assemble(salesSlip);
	}

	private void cancel(com.greenhouse.backend.sales.domain.SalesSlip salesSlip) {
		if (salesSlip.getSalesType() == SalesType.DIRECT) {
			partnerBalanceService.lockPartners(List.of(salesSlip.getPartner().getId()));
		}
		if (salesSlip.getSalesType() == SalesType.DIRECT
				&& paymentEventReader.existsByTarget(PaymentTargetType.SALES_SLIP, salesSlip.getId())) {
			throw new IllegalArgumentException("입금 이력이 있는 판매 전표는 취소할 수 없습니다.");
		}

		if (salesSlip.isOutboundCompleted()) {
			salesSlipInventoryService.cancelOutbound(salesSlip);
		} else {
			salesSlipInventoryService.cancelReserve(salesSlip);
		}

		if (salesSlip.getSalesType() == SalesType.AUCTION) {
			auctionSalesSlipCancellationPolicy.cancelShipmentIfPossible(salesSlip);
		}

		salesSlip.updateSalesStatus(SalesSlip.STATUS_CANCELED);
		if (salesSlip.getSalesType() == SalesType.DIRECT) {
			partnerBalanceService.updateReceivable(
					salesSlip.getPartner().getId(),
					salesSlipRepository.sumDirectReceivableByPartnerId(salesSlip.getPartner().getId()),
					null);
		}
	}
}
