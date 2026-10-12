package com.greenhouse.backend.sales.direct.application;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.direct.domain.DirectSaleChangeLedger;
import com.greenhouse.backend.sales.direct.repository.DirectSaleChangeRepository;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectDocumentAccountingAdapter implements DirectDocumentAccountingPort {
  private final DirectSaleChangeRepository changes;
  private final PartnerBalanceOperationsApi balances;
  private final PaymentEventQueryApi events;
  private final ExpectedPaymentDateApi dates;
  private final DirectSaleTermsWriter termsWriter;
  private final DirectSaleFinancialReader financials;
  private final DirectSaleReviewReader reviews;
  private final DirectSalePaymentPolicy payments;

  public void requireNoActiveChanges(Long id) {
    if (DirectSaleChangeLedger.hasActiveChanges(
        changes.findByDocumentIdInOrderByIdAsc(List.of(id))))
      throw new ConflictException(
          "DIRECT_CHANGE_HISTORY_EXISTS", "취소하지 않은 가격 조정·반품이 있는 전표는 전체 취소할 수 없습니다.");
  }

  public void requireFinancialReviewCleared(Long id, Long expectedPartnerId) {
    reviews.requireClear(id, expectedPartnerId);
  }

  public void requirePaymentAmount(Long documentId, Long expectedPartnerId, Long amount) {
    payments.requirePaymentAmount(documentId, expectedPartnerId, amount);
  }

  public Map<Long, Long> findItemAmountChanges(LocalDate from, LocalDate to) {
    return changes.sumItemChanges(from, to).stream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
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
