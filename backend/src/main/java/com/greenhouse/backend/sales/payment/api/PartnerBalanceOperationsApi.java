package com.greenhouse.backend.sales.payment.api;

import java.util.Collection;

public interface PartnerBalanceOperationsApi {
  void lockPartners(Collection<Long> partnerIds);

  void updateReceivable(Long partnerId, Long receivableBalance, Long lastPaymentEventId);

  void recordActivity(Long partnerId, Long lastPaymentEventId);
}
