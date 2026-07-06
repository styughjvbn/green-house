package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.dto.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.settlement.dto.PartnerPaymentEventResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {
	private final PartnerPaymentEventRepository eventRepository;
	private final PartnerBalanceSummaryRepository balanceRepository;
	private final BusinessPartnerRepository partnerRepository;
	private final SalesSlipRepository salesSlipRepository;
	private final AuctionSettlementRepository auctionSettlementRepository;

	public PaymentService(
		PartnerPaymentEventRepository eventRepository,
		PartnerBalanceSummaryRepository balanceRepository,
		BusinessPartnerRepository partnerRepository,
		SalesSlipRepository salesSlipRepository,
		AuctionSettlementRepository auctionSettlementRepository
	) {
		this.eventRepository = eventRepository;
		this.balanceRepository = balanceRepository;
		this.partnerRepository = partnerRepository;
		this.salesSlipRepository = salesSlipRepository;
		this.auctionSettlementRepository = auctionSettlementRepository;
	}

	public AuctionSettlementResponse confirmAuctionPayment(Long settlementId, ManualPaymentRequest request) {
		var settlement = auctionSettlementRepository.findWithDetailsById(settlementId)
			.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
		settlement.recordPayment(request.amount(), worker(request.worker()));
		var received = recordEvents(
			settlement.getAuctionHouse(), PaymentTargetType.AUCTION_SETTLEMENT, settlementId, request);
		refreshBalance(settlement.getAuctionHouse().getId(), received);
		return AuctionSettlementResponse.from(auctionSettlementRepository.save(settlement));
	}

	public SalesSlipResponse confirmSalesSlipPayment(Long salesSlipId, ManualPaymentRequest request) {
		var salesSlip = salesSlipRepository.findWithDetailsById(salesSlipId)
			.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		if (salesSlip.getSalesType() != SalesType.DIRECT) {
			throw new IllegalArgumentException("경매 판매전표는 경매장 정산에서 입금을 확인해야 합니다.");
		}
		salesSlip.recordPayment(request.amount());
		var received = recordEvents(salesSlip.getPartner(), PaymentTargetType.SALES_SLIP, salesSlipId, request);
		var saved = salesSlipRepository.save(salesSlip);
		refreshBalance(salesSlip.getPartner().getId(), received);
		return SalesSlipResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<PartnerPaymentEventResponse> getEvents(
		Long partnerId,
		PaymentTargetType targetType,
		Long targetId
	) {
		return eventRepository.search(partnerId, targetType, targetId).stream()
			.map(PartnerPaymentEventResponse::from)
			.toList();
	}

	public PartnerBalanceSummaryResponse getBalance(Long partnerId) {
		partnerRepository.findById(partnerId)
			.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
		return PartnerBalanceSummaryResponse.from(refreshBalance(partnerId, null));
	}

	private PartnerPaymentEvent recordEvents(
		com.greenhouse.backend.partner.domain.BusinessPartner partner,
		PaymentTargetType targetType,
		Long targetId,
		ManualPaymentRequest request
	) {
		var received = eventRepository.save(PartnerPaymentEvent.received(
			partner, request.paymentDate(), request.amount(), targetType, targetId,
			normalize(request.paymentMethod()), normalize(request.depositorName()), normalize(request.memo()),
			worker(request.worker())));
		eventRepository.save(PartnerPaymentEvent.manualMatch(received));
		return received;
	}

	private PartnerBalanceSummary refreshBalance(Long partnerId, PartnerPaymentEvent lastPaymentEvent) {
		var summary = balanceRepository.findByPartnerId(partnerId).orElseGet(() -> {
			var partner = partnerRepository.findById(partnerId)
				.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
			return new PartnerBalanceSummary(partner);
		});
		summary.updateReceivableBalance(
			salesSlipRepository.sumDirectReceivableByPartnerId(partnerId), lastPaymentEvent);
		return balanceRepository.save(summary);
	}

	private String worker(String value) {
		String normalized = normalize(value);
		return normalized == null ? "관리자" : normalized;
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
