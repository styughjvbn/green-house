package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.dto.PartnerPaymentEventResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

	private static final int LEGACY_LIST_LIMIT = 500;

	private final PartnerPaymentEventRepository eventRepository;

	private final AuctionSettlementRepository auctionSettlementRepository;

	private final PaymentLedgerService paymentLedgerService;

	private final PartnerBalanceService partnerBalanceService;

	private final RequestActorProvider requestActorProvider;

	private final SettlementAuditSupport auditSupport;

	private final BusinessPartnerReader partnerReader;

	private final AuctionSettlementResponseAssembler settlementResponseAssembler;

	private final Clock clock;

	public AuctionSettlementResponse confirmAuctionPayment(Long settlementId, ManualPaymentCommand payment) {
		Long auctionHouseId = auctionSettlementRepository.findAuctionHouseId(settlementId)
			.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
		partnerBalanceService.lockPartners(List.of(auctionHouseId));
		var settlement = auctionSettlementRepository.findForUpdateById(settlementId)
			.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
		if (paymentLedgerService.findManualPayment(PaymentTargetType.AUCTION_SETTLEMENT, settlementId, payment)
			.isPresent()) {
			return settlementResponseAssembler.assemble(settlement);
		}
		var before = auditSupport.auctionPaymentSnapshot(settlement);
		settlement.recordPayment(payment.amount(), defaultWorker(requestActorProvider.resolve(payment.worker())),
				TimeConfig.utcNow(clock));
		var receivedEventId = paymentLedgerService.recordManualPayment(settlement.getAuctionHouseId(),
				PaymentTargetType.AUCTION_SETTLEMENT, settlementId, payment);
		partnerBalanceService.recordActivity(settlement.getAuctionHouseId(), receivedEventId);
		var saved = auctionSettlementRepository.save(settlement);
		auditSupport.recordTargetPayment("AUCTION_SETTLEMENT", saved.getId(), saved.getAuctionHouseId(),
				PaymentTargetType.AUCTION_SETTLEMENT, before, auditSupport.auctionPaymentSnapshot(saved));
		return settlementResponseAssembler.assemble(saved);
	}

	@Transactional(readOnly = true)
	public List<PartnerPaymentEventResponse> getEvents(Long partnerId, PaymentTargetType targetType, Long targetId) {
		return eventResponses(
				eventRepository.search(partnerId, targetType, targetId, null, PageRequest.of(0, LEGACY_LIST_LIMIT)))
			.getContent();
	}

	@Transactional(readOnly = true)
	public PageResponse<PartnerPaymentEventResponse> getEventPage(Long partnerId, PaymentTargetType targetType,
			Long targetId, PaymentEventType eventType, int page, int size) {
		return PageResponse.from(eventResponses(
				eventRepository.search(partnerId, targetType, targetId, eventType, PageRequests.clamped(page, size))));
	}

	private Page<PartnerPaymentEventResponse> eventResponses(Page<PartnerPaymentEvent> events) {
		var partners = partnerReader.getAllInfo(events.stream().map(PartnerPaymentEvent::getPartnerId).toList());
		return events.map(event -> PartnerPaymentEventResponse.from(event, partners.get(event.getPartnerId()).name()));
	}

	private String defaultWorker(String value) {
		return value == null ? "관리자" : value;
	}

}
