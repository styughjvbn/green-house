package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.application.payment.ManualPaymentService;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.dto.auction.AuctionProceedsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionProceedsPaymentService {
  private final ManualPaymentService payments;
  private final AuctionProceedsPaymentTarget target;

  @Transactional
  public AuctionProceedsResponse confirm(Long id, ManualPaymentCommand command) {
    return payments.confirm(id, PaymentTargetType.AUCTION_PROCEEDS, command, target);
  }
}
