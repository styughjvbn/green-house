package com.greenhouse.backend.sales.application.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Document-owned value contract for Direct terms and accounting coordination. */
public interface DirectDocumentAccountingPort {
  void lockPartners(Collection<Long> ids);

  void updateReceivable(Long partnerId, Long amount, Long eventId);

  LocalDate calculate(Long partnerId, LocalDate date);

  boolean existsPayment(Long documentId);

  Set<Long> findPaidDocumentIds(Collection<Long> ids);

  void storeTerms(Terms terms);

  Map<Long, FinancialSnapshot> findFinancials(Collection<Long> documentIds);

  record FinancialSnapshot(
      Long documentId,
      Long partnerId,
      Integer totalAmount,
      LocalDate expectedPaymentDate,
      String paymentMethod,
      BigDecimal allocatedAmount,
      BigDecimal remainingAmount,
      boolean reviewRequired,
      Map<Long, PriceSnapshot> prices) {}

  record PriceSnapshot(Integer pricedQuantity, Integer unitPrice, Integer amount) {}

  record Terms(
      Long documentId,
      Long partnerId,
      LocalDate saleDate,
      LocalDate expectedPaymentDate,
      String paymentMethod,
      List<Price> prices) {}

  record Price(Long documentItemId, Integer quantity, Integer unitPrice) {}
}
