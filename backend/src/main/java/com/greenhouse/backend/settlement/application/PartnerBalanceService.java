package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.dto.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.settlement.repository.PartnerBalanceSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PartnerBalanceService {
	private final PartnerBalanceSummaryRepository balanceRepository;
	private final BusinessPartnerRepository partnerRepository;

	public PartnerBalanceService(
		PartnerBalanceSummaryRepository balanceRepository,
		BusinessPartnerRepository partnerRepository
	) {
		this.balanceRepository = balanceRepository;
		this.partnerRepository = partnerRepository;
	}

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
			var partner = partnerRepository.findById(partnerId)
				.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
			return new PartnerBalanceSummary(partner);
		});
	}
}
