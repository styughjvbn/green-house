package com.greenhouse.backend.sales.domain.partner;

import java.util.List;

/** Current execution policy, independent of persisted partner preferences. */
public record SettlementCapabilities(
    List<SettlementUnit> executableUnits,
    boolean autoMatching,
    boolean autoSettlement,
    boolean prepayment,
    boolean creditAutoApply,
    boolean ruleExecution) {
  public SettlementCapabilities {
    executableUnits = List.copyOf(executableUnits);
  }

  public static SettlementCapabilities forPartnerType(PartnerType partnerType) {
    return new SettlementCapabilities(
        partnerType == PartnerType.AUCTION_HOUSE ? List.of() : List.of(SettlementUnit.SALES_SLIP),
        false,
        false,
        false,
        false,
        false);
  }
}
