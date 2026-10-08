package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.application.payment.ManualPaymentService;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesPaymentService {
  private final ManualPaymentService payments;
  private final DirectPaymentAllocationTarget target;

  public SalesSlipDocument confirmPayment(Long id, ManualPaymentCommand payment) {
    return payments.confirm(id, PaymentTargetType.SALES_SLIP, payment, target);
  }
}
