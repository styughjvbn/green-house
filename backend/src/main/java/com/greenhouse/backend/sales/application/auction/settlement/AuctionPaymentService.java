package com.greenhouse.backend.sales.application.auction.settlement;

import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.application.payment.ManualPaymentService;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.dto.auction.settlement.AuctionSettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuctionPaymentService {
  private final ManualPaymentService payments;
  private final AuctionSettlementPaymentTarget target;

  public AuctionSettlementResponse confirmAuctionPayment(Long id, ManualPaymentCommand payment) {
    return payments.confirm(id, PaymentTargetType.AUCTION_SETTLEMENT, payment, target);
  }
}
