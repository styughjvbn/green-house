package com.greenhouse.backend.sales.api.payment;

import java.util.Map;

public interface PartnerBalanceQueryApi {
  Map<Long, Balance> getNonzeroBalances();

  public record Balance(long receivableBalance, long creditBalance, long unappliedPaymentAmount) {

    public static final Balance ZERO = new Balance(0, 0, 0);

    public boolean hasPositiveBalance() {
      return receivableBalance > 0 || creditBalance > 0 || unappliedPaymentAmount > 0;
    }
  }
}
