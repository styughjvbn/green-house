package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.dto.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PartnerBalanceService {
	private final PartnerBalanceSummaryRepository balanceRepository;
	private final BusinessPartnerReader partnerReader;

	public void updateReceivable(Long partnerId, Long receivableBalance, PartnerPaymentEvent lastPaymentEvent) {
		var summary = findOrCreate(partnerId);
		summary.updateReceivableBalance(receivableBalance, lastPaymentEvent);
		balanceRepository.save(summary);
	}

	public void recordActivity(Long partnerId, PartnerPaymentEvent lastPaymentEvent) {
		var summary = findOrCreate(partnerId);
		summary.updateReceivableBalance(summary.getReceivableBalance(), lastPaymentEvent);
		balanceRepository.save(summary);
	}

	public PartnerBalanceSummaryResponse getBalance(Long partnerId) {
		return PartnerBalanceSummaryResponse.from(findOrCreate(partnerId));
	}

	private PartnerBalanceSummary findOrCreate(Long partnerId) {
		return balanceRepository.findByPartnerId(partnerId).orElseGet(() -> {
			var partner = partnerReader.get(partnerId);
			return balanceRepository.save(new PartnerBalanceSummary(partner));
		});
	}
}
