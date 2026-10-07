package com.greenhouse.backend.sales.application.auction.settlement;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.sales.domain.auction.settlement.AuctionSettlement;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SettlementAuditSupport {

  private final AuditEventWriter auditWriter;

  Map<String, Object> auctionPaymentSnapshot(AuctionSettlement settlement) {
    var data = new LinkedHashMap<String, Object>();
    data.put("paidAmount", settlement.getPaidAmount());
    data.put("remainingAmount", settlement.getRemainingAmount());
    data.put("paymentStatus", settlement.getStatus().name());
    return data;
  }

  void recordAuctionPayment(
      AuctionSettlement settlement, Map<String, Object> before, Map<String, Object> after) {
    auditWriter.record(
        AuditAction.UPDATED,
        AuditSource.SETTLEMENT_MANAGEMENT,
        "AUCTION_SETTLEMENT",
        settlement.getId(),
        before,
        after,
        Map.of(
            "partnerId",
            settlement.getAuctionHouseId(),
            "targetType",
            PaymentTargetType.AUCTION_SETTLEMENT.name()));
  }
}
