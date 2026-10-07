package com.greenhouse.backend.sales.application.payment;

import java.time.LocalDateTime;
import java.util.Map;

/** A locked payment target exposes values only; its owner applies and audits its summary. */
public interface PaymentTargetPort<T> {
  Long lockAndValidate(Long targetId);

  Map<String, Object> paymentSnapshot(Long targetId);

  void recordPayment(Long targetId, Long amount, String worker, LocalDateTime now);

  void updateBalance(Long targetId, Long eventId);

  void auditPayment(Long targetId, Map<String, Object> before);

  T response(Long targetId);
}
