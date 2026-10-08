package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.sales.auction.api.AuctionProceedsResponse;
import com.greenhouse.backend.sales.payment.api.ManualPaymentApi;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionProceedsPaymentService {
  private final ManualPaymentApi payments;
  private final AuctionProceedsPaymentTarget target;

  @Transactional
  public AuctionProceedsResponse confirm(Long id, ManualPaymentCommand command) {
    return payments.confirm(id, PaymentTargetType.AUCTION_PROCEEDS, command, target);
  }
}
