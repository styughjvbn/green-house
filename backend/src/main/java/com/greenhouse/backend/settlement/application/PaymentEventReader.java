package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentEventReader {

	private final PartnerPaymentEventRepository partnerPaymentEventRepository;

	public boolean existsByTarget(PaymentTargetType targetType, Long targetId) {
		return partnerPaymentEventRepository.existsByTargetTypeAndTargetId(targetType, targetId);
	}

	public Set<Long> findExistingTargetIds(PaymentTargetType targetType, List<Long> targetIds) {
		if (targetIds.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(partnerPaymentEventRepository.findExistingTargetIds(targetType, targetIds));
	}
}
