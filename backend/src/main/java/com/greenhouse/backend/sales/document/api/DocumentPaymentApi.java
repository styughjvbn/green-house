package com.greenhouse.backend.sales.document.api;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface DocumentPaymentApi {
  Map<Long, Long> findPaymentOwners(Collection<Long> ids);

  void lockAllocationTarget(Long id, Long partnerId);

  void lockAllocationAmounts(Collection<Long> ids);

  PageResponse<PaymentOption> allocationOptions(Long partnerId, String keyword, int page, int size);

  Long lockAndValidate(Long id);

  Map<String, Object> paymentSnapshot(Long id);

  void recordPayment(Long id, Long amount, String worker, LocalDateTime now);

  void updateBalance(Long id, Long eventId);

  void auditPayment(Long id, Map<String, Object> before);

  SalesSlipDocument response(Long id);

  public record PaymentOption(
      Long id,
      String sourceReference,
      Long receivableAmount,
      BigDecimal paidAmount,
      BigDecimal availableAmount,
      boolean allocationAllowed,
      boolean correctionAllowed,
      boolean reviewRequired) {}
}
