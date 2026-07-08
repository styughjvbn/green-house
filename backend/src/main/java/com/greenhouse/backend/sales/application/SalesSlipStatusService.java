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

@Service
@Transactional
@RequiredArgsConstructor
public class SalesSlipStatusService {

	private final SalesSlipRepository salesSlipRepository;
	private final AuctionShipmentMaterializer auctionShipmentMaterializer;
	private final AuctionSalesSlipCancellationPolicy auctionSalesSlipCancellationPolicy;
	private final SalesSlipInventoryService salesSlipInventoryService;
	private final PaymentEventReader paymentEventReader;
	private final PartnerBalanceService partnerBalanceService;

	public SalesSlipResponse updateStatus(Long salesSlipId, SalesSlipStatusUpdateRequest request) {
		var salesSlip = salesSlipRepository.findWithDetailsById(salesSlipId)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		if (salesSlip.isCanceled()) {
			throw new IllegalArgumentException("취소된 전표는 상태를 변경할 수 없습니다.");
		}
		if (request.salesStatus().equals(salesSlip.getSalesStatus())) {
			return SalesSlipResponse.from(salesSlip);
		}
		if ("취소".equals(request.salesStatus())) {
			cancel(salesSlip);
			return SalesSlipResponse.from(salesSlip);
		}
		if (salesSlip.isOutboundCompleted()) {
			throw new IllegalArgumentException("출고 완료된 전표는 판매 상태를 변경할 수 없습니다.");
		}

		salesSlip.updateSalesStatus(request.salesStatus());
		if (salesSlip.isOutboundCompleted()) {
			auctionShipmentMaterializer.materialize(salesSlip);
			salesSlipInventoryService.outbound(salesSlip);
		}
		return SalesSlipResponse.from(salesSlip);
	}

	private void cancel(com.greenhouse.backend.sales.domain.SalesSlip salesSlip) {
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

		salesSlip.updateSalesStatus("취소");
		if (salesSlip.getSalesType() == SalesType.DIRECT) {
			partnerBalanceService.updateReceivable(
					salesSlip.getPartner().getId(),
					salesSlipRepository.sumDirectReceivableByPartnerId(salesSlip.getPartner().getId()),
					null);
		}
	}
}
