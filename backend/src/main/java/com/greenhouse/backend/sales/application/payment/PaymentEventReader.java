package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.sales.payment.api.PaymentEventQueryApi;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentEventReader implements PaymentEventQueryApi {

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
