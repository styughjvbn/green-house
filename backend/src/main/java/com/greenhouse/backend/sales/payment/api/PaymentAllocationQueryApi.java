package com.greenhouse.backend.sales.payment.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

public interface PaymentAllocationQueryApi {
  Map<Long, Allocation> findAll(PaymentTargetType type, Collection<Long> targetIds);

  Map<Long, Allocation> findAll(PaymentTargetType type, Map<Long, Long> owners);

  public record Allocation(BigDecimal amount, Long partnerId, boolean reviewRequired) {}
}
