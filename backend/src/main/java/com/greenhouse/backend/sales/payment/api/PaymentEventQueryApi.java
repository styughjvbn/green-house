package com.greenhouse.backend.sales.payment.api;

import java.util.List;
import java.util.Set;

public interface PaymentEventQueryApi {
  boolean existsByTarget(PaymentTargetType targetType, Long targetId);

  Set<Long> findExistingTargetIds(PaymentTargetType targetType, List<Long> targetIds);
}
