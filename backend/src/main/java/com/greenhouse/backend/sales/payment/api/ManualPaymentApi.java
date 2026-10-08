package com.greenhouse.backend.sales.payment.api;

import com.greenhouse.backend.sales.payment.spi.PaymentTargetPort;

public interface ManualPaymentApi {
  <T> T confirm(
      Long id, PaymentTargetType type, ManualPaymentCommand command, PaymentTargetPort<T> target);
}
