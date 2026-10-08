package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
import com.greenhouse.backend.sales.partner.api.ExpectedPaymentDateApi;
import com.greenhouse.backend.sales.payment.api.PartnerBalanceOperationsApi;
import com.greenhouse.backend.sales.payment.api.PaymentEventQueryApi;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectDocumentAccountingAdapter implements DirectDocumentAccountingPort {
  private final PartnerBalanceOperationsApi balances;
  private final PaymentEventQueryApi events;
  private final ExpectedPaymentDateApi dates;
  private final DirectSaleTermsWriter termsWriter;
  private final DirectSaleFinancialReader financials;
  private final DirectSaleReviewReader reviews;
  private final DirectSalePaymentPolicy payments;

  public void requireFinancialReviewCleared(Long id, Long expectedPartnerId) {
    reviews.requireClear(id, expectedPartnerId);
  }

  public void requirePaymentAmount(Long documentId, Long expectedPartnerId, Long amount) {
    payments.requirePaymentAmount(documentId, expectedPartnerId, amount);
  }

  public Map<Long, FinancialSnapshot> findFinancials(Collection<Long> documentIds) {
    return financials.findAll(documentIds);
  }

  public QuotedPrices quotePrices(List<Price> prices) {
    return termsWriter.quotePrices(prices);
  }

  public void storeTerms(Terms terms) {
    termsWriter.store(terms);
  }

  public void lockPaymentTargets(Collection<Long> ids) {
    payments.lockTargets(ids);
  }

  public void lockPartners(Collection<Long> ids) {
    balances.lockPartners(ids);
  }

  public void updateReceivable(Long id, Long amount, Long eventId) {
    balances.updateReceivable(id, amount, eventId);
  }

  public LocalDate calculate(Long id, LocalDate date) {
    return dates.calculate(id, date);
  }

  public boolean existsPayment(Long id) {
    return events.existsByTarget(PaymentTargetType.SALES_SLIP, id);
  }

  public Set<Long> findPaidDocumentIds(Collection<Long> ids) {
    return events.findExistingTargetIds(PaymentTargetType.SALES_SLIP, ids.stream().toList());
  }
}
