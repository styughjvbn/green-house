package com.greenhouse.backend.sales.payment.spi;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.payment.api.PaymentAllocationTargetOption;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import java.util.Collection;
import java.util.Map;

/** Multi-target writes separate root locking, amount-source locking and allocation application. */
public interface PaymentAllocationTargetPort<T> extends PaymentTargetPort<T> {
  PaymentTargetType targetType();

  Map<Long, Long> findTargetOwners(Collection<Long> ids);

  void lockAllocationTarget(Long id, Long partnerId);

  void lockAllocationAmounts(Collection<Long> ids);

  PageResponse<PaymentAllocationTargetOption> allocationOptions(
      Long partnerId, String keyword, int page, int size);
}
