package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.dto.PartnerPaymentEventResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {
	private final PartnerPaymentEventRepository eventRepository;
	private final AuctionSettlementRepository auctionSettlementRepository;
	private final PaymentLedgerService paymentLedgerService;
	private final PartnerBalanceService partnerBalanceService;
	private final RequestActorProvider requestActorProvider;
	private final SettlementAuditSupport auditSupport;

	public AuctionSettlementResponse confirmAuctionPayment(Long settlementId, ManualPaymentRequest request) {
		var settlement = auctionSettlementRepository.findWithDetailsById(settlementId)
				.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
		var before = auditSupport.auctionPaymentSnapshot(settlement);
		settlement.recordPayment(request.amount(), defaultWorker(requestActorProvider.resolve(request.worker())));
		var received = paymentLedgerService.recordManualPayment(
				settlement.getAuctionHouse(), PaymentTargetType.AUCTION_SETTLEMENT, settlementId, request);
		partnerBalanceService.recordActivity(settlement.getAuctionHouse().getId(), received);
		var saved = auctionSettlementRepository.save(settlement);
		auditSupport.recordTargetPayment("AUCTION_SETTLEMENT", saved.getId(),
				saved.getAuctionHouse().getId(), PaymentTargetType.AUCTION_SETTLEMENT,
				before, auditSupport.auctionPaymentSnapshot(saved));
		return AuctionSettlementResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<PartnerPaymentEventResponse> getEvents(
			Long partnerId,
			PaymentTargetType targetType,
			Long targetId) {
		return eventRepository.search(partnerId, targetType, targetId).stream()
				.map(PartnerPaymentEventResponse::from)
				.toList();
	}

	private String defaultWorker(String value) {
		return value == null ? "관리자" : value;
	}
}
