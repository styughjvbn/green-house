package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.application.document.DirectDocumentAccountingPort;
import com.greenhouse.backend.sales.application.partner.ExpectedPaymentDateCalculator;
import com.greenhouse.backend.sales.application.payment.PartnerBalanceService;
import com.greenhouse.backend.sales.application.payment.PaymentEventReader;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectDocumentAccountingAdapter implements DirectDocumentAccountingPort {
  private final PartnerBalanceService balances;
  private final PaymentEventReader events;
  private final ExpectedPaymentDateCalculator dates;
  private final DirectSaleTermsWriter termsWriter;
  private final DirectSaleFinancialReader financials;
  private final DirectSaleReviewReader reviews;

  public Set<Long> findFinancialReviewRequiredIds(Collection<Long> ids) {
    return reviews.findRequired(ids);
  }

  public void requireFinancialReviewCleared(Long id) {
    reviews.requireClear(id);
  }

  public Map<Long, FinancialSnapshot> findFinancials(Collection<Long> documentIds) {
    return financials.findAll(documentIds);
  }

  public void storeTerms(Terms terms) {
    termsWriter.store(terms);
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
