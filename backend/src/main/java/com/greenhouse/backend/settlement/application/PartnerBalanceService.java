package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.dto.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;

import lombok.RequiredArgsConstructor;

import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PartnerBalanceService {
	private final PartnerBalanceSummaryRepository balanceRepository;
	private final BusinessPartnerReader partnerReader;

	public void lockPartners(Collection<Long> partnerIds) {
		partnerReader.getAllForUpdate(partnerIds);
	}

	public void updateReceivable(Long partnerId, Long receivableBalance, PartnerPaymentEvent lastPaymentEvent) {
		var summary = findOrCreateForUpdate(partnerId);
		summary.updateReceivableBalance(receivableBalance, lastPaymentEvent);
		balanceRepository.save(summary);
	}

	public void recordActivity(Long partnerId, PartnerPaymentEvent lastPaymentEvent) {
		var summary = findOrCreateForUpdate(partnerId);
		summary.updateReceivableBalance(summary.getReceivableBalance(), lastPaymentEvent);
		balanceRepository.save(summary);
	}

	public PartnerBalanceSummaryResponse getBalance(Long partnerId) {
		return PartnerBalanceSummaryResponse.from(findOrCreateForUpdate(partnerId));
	}

	private PartnerBalanceSummary findOrCreateForUpdate(Long partnerId) {
		var partner = partnerReader.getAllForUpdate(java.util.List.of(partnerId)).getFirst();
		return balanceRepository.findForUpdateByPartnerId(partnerId).orElseGet(() -> {
			return balanceRepository.save(new PartnerBalanceSummary(partner));
		});
	}
}
