package com.greenhouse.backend.settlement.domain;

import com.greenhouse.backend.partner.domain.PartnerType;
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
    var unit =
        partnerType == PartnerType.AUCTION_HOUSE
            ? SettlementUnit.AUCTION_DATE
            : SettlementUnit.SALES_SLIP;
    return new SettlementCapabilities(List.of(unit), false, false, false, false, false);
  }
}
