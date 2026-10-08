package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.direct.api.SalesPaymentApi;
import com.greenhouse.backend.sales.payment.api.ManualPaymentApi;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SalesPaymentService implements SalesPaymentApi {
  private final ManualPaymentApi payments;
  private final DirectPaymentAllocationTarget target;

  public SalesSlipDocument confirmPayment(Long id, ManualPaymentCommand payment) {
    return payments.confirm(id, PaymentTargetType.SALES_SLIP, payment, target);
  }
}
