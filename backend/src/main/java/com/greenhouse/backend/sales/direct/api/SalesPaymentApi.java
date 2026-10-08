package com.greenhouse.backend.sales.direct.api;

import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;

public interface SalesPaymentApi {
  SalesSlipDocument confirmPayment(Long id, ManualPaymentCommand payment);
}
