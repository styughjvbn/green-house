package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
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
	private final BusinessPartnerReader partnerReader;
	private final AuctionSettlementResponseAssembler settlementResponseAssembler;

	public AuctionSettlementResponse confirmAuctionPayment(Long settlementId, ManualPaymentRequest request) {
		var payment = request.toCommand();
		var settlement = auctionSettlementRepository.findForUpdateById(settlementId)
				.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
		if (paymentLedgerService.findManualPayment(
				PaymentTargetType.AUCTION_SETTLEMENT, settlementId, payment).isPresent()) {
			return settlementResponseAssembler.assemble(settlement);
		}
		var before = auditSupport.auctionPaymentSnapshot(settlement);
		settlement.recordPayment(request.amount(), defaultWorker(requestActorProvider.resolve(request.worker())));
		var received = paymentLedgerService.recordManualPayment(
				settlement.getAuctionHouseId(), PaymentTargetType.AUCTION_SETTLEMENT, settlementId, payment);
		partnerBalanceService.recordActivity(settlement.getAuctionHouseId(), received.eventId());
		var saved = auctionSettlementRepository.save(settlement);
		auditSupport.recordTargetPayment("AUCTION_SETTLEMENT", saved.getId(),
				saved.getAuctionHouseId(), PaymentTargetType.AUCTION_SETTLEMENT,
				before, auditSupport.auctionPaymentSnapshot(saved));
		return settlementResponseAssembler.assemble(saved);
	}

	@Transactional(readOnly = true)
	public List<PartnerPaymentEventResponse> getEvents(
			Long partnerId,
			PaymentTargetType targetType,
			Long targetId) {
		var events = eventRepository.search(partnerId, targetType, targetId);
		var partners = partnerReader.getAllInfo(events.stream().map(PartnerPaymentEvent::getPartnerId).toList());
		return events.stream()
				.map(event -> PartnerPaymentEventResponse.from(event, partners.get(event.getPartnerId()).name()))
				.toList();
	}

	private String defaultWorker(String value) {
		return value == null ? "관리자" : value;
	}
}
