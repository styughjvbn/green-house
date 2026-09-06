package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.application.BusinessPartnerLock;
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.dto.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;

import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PartnerBalanceService {
	private final PartnerBalanceSummaryRepository balanceRepository;
	private final PartnerPaymentEventRepository eventRepository;
	private final BusinessPartnerLock partnerLock;

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockPartners(Collection<Long> partnerIds) {
		partnerLock.lockAll(partnerIds);
	}

	public void updateReceivable(Long partnerId, Long receivableBalance, Long lastPaymentEventId) {
		partnerLock.lockAll(List.of(partnerId));
		var summary = findOrCreateForUpdate(partnerId);
		summary.updateReceivableBalance(receivableBalance, paymentEventReference(lastPaymentEventId));
		balanceRepository.save(summary);
	}

	public void recordActivity(Long partnerId, Long lastPaymentEventId) {
		partnerLock.lockAll(List.of(partnerId));
		var summary = findOrCreateForUpdate(partnerId);
		summary.updateReceivableBalance(summary.getReceivableBalance(), paymentEventReference(lastPaymentEventId));
		balanceRepository.save(summary);
	}

	public PartnerBalanceSummaryResponse getBalance(Long partnerId) {
		var partner = partnerLock.lockAll(List.of(partnerId)).getFirst();
		return PartnerBalanceSummaryResponse.from(findOrCreateForUpdate(partnerId), partner.name());
	}

	private PartnerBalanceSummary findOrCreateForUpdate(Long partnerId) {
		return balanceRepository.findForUpdateByPartnerId(partnerId)
				.orElseGet(() -> balanceRepository.save(new PartnerBalanceSummary(partnerId)));
	}

	private PartnerPaymentEvent paymentEventReference(Long eventId) {
		return eventId == null ? null : eventRepository.getReferenceById(eventId);
	}
}
