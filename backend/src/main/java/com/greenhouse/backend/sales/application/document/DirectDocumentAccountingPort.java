package com.greenhouse.backend.sales.application.document;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Temporary accounting seam until Direct receives its dedicated persisted terms. */
public interface DirectDocumentAccountingPort {
  void lockPartners(Collection<Long> ids);

  void updateReceivable(Long partnerId, Long amount, Long eventId);

  LocalDate calculate(Long partnerId, LocalDate date);

  boolean existsPayment(Long documentId);

  Set<Long> findPaidDocumentIds(Collection<Long> ids);

  void storeTerms(Terms terms);

  record Terms(
      Long documentId,
      Long partnerId,
      LocalDate saleDate,
      LocalDate expectedPaymentDate,
      String paymentMethod,
      List<Price> prices) {}

  record Price(Long documentItemId, Integer quantity, Integer unitPrice) {}
}
