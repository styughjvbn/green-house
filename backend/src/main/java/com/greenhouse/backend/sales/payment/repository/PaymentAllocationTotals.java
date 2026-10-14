package com.greenhouse.backend.sales.payment.repository;

import java.math.BigDecimal;

/** Owner-side aggregate projection; converted to application values before leaving Payment. */
public interface PaymentAllocationTotals {
  Long getTargetId();

  Long getPartnerId();

  BigDecimal getAmount();

  Boolean getReviewRequired();
}
