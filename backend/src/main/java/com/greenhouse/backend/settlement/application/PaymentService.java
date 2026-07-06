package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.dto.PartnerPaymentEventResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {
	private final PartnerPaymentEventRepository eventRepository;
	private final AuctionSettlementRepository auctionSettlementRepository;
	private final PaymentLedgerService paymentLedgerService;
	private final PartnerBalanceService partnerBalanceService;

	public PaymentService(
		PartnerPaymentEventRepository eventRepository,
		AuctionSettlementRepository auctionSettlementRepository,
		PaymentLedgerService paymentLedgerService,
		PartnerBalanceService partnerBalanceService
	) {
		this.eventRepository = eventRepository;
		this.auctionSettlementRepository = auctionSettlementRepository;
		this.paymentLedgerService = paymentLedgerService;
		this.partnerBalanceService = partnerBalanceService;
	}

	public AuctionSettlementResponse confirmAuctionPayment(Long settlementId, ManualPaymentRequest request) {
		var settlement = auctionSettlementRepository.findWithDetailsById(settlementId)
			.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
		settlement.recordPayment(request.amount(), worker(request.worker()));
		var received = paymentLedgerService.recordManualPayment(
			settlement.getAuctionHouse(), PaymentTargetType.AUCTION_SETTLEMENT, settlementId, request);
		partnerBalanceService.recordActivity(settlement.getAuctionHouse().getId(), received);
		return AuctionSettlementResponse.from(auctionSettlementRepository.save(settlement));
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

	private String worker(String value) {
		return value == null || value.isBlank() ? "관리자" : value.trim();
	}
}
