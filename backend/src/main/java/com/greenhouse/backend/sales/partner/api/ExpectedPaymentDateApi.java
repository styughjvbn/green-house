package com.greenhouse.backend.sales.partner.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface ExpectedPaymentDateApi {
  LocalDate calculate(Long partnerId, LocalDate baseDate);

  Map<PaymentDateTarget, LocalDate> calculateAll(Collection<PaymentDateTarget> targets);

  record PaymentDateTarget(Long partnerId, LocalDate baseDate) {}
}
