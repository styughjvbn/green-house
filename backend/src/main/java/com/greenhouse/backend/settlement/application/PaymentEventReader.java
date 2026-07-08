package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentEventReader {

	private final PartnerPaymentEventRepository partnerPaymentEventRepository;

	public boolean existsByTarget(PaymentTargetType targetType, Long targetId) {
		return partnerPaymentEventRepository.existsByTargetTypeAndTargetId(targetType, targetId);
	}
}
