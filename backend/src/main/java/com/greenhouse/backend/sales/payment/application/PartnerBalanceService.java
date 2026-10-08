package com.greenhouse.backend.sales.payment.application;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.api.payment.PartnerBalanceQueryApi;
import com.greenhouse.backend.sales.api.payment.PartnerBalanceQueryApi.Balance;
import com.greenhouse.backend.sales.partner.api.BusinessPartnerLockApi;
import com.greenhouse.backend.sales.payment.api.PartnerBalanceOperationsApi;
import com.greenhouse.backend.sales.payment.domain.PartnerBalanceSummary;
import com.greenhouse.backend.sales.payment.domain.PartnerPaymentEvent;
import com.greenhouse.backend.sales.payment.repository.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.sales.payment.repository.PartnerPaymentEventRepository;
import com.greenhouse.backend.sales.payment.web.dto.PartnerBalanceSummaryResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PartnerBalanceService implements PartnerBalanceOperationsApi, PartnerBalanceQueryApi {

  private final PartnerBalanceSummaryRepository balanceRepository;

  private final PartnerPaymentEventRepository eventRepository;

  private final BusinessPartnerLockApi partnerLock;

  @Transactional(readOnly = true)
  public Map<Long, Balance> getNonzeroBalances() {
    return balanceRepository.findNonzeroBalances().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                row -> row.getPartnerId(),
                row ->
                    new Balance(
                        row.getReceivableBalance(),
                        row.getCreditBalance(),
                        row.getUnappliedPaymentAmount())));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockPartners(Collection<Long> partnerIds) {
    partnerLock.lockAll(partnerIds);
  }

  public void updateReceivable(Long partnerId, Long receivableBalance, Long lastPaymentEventId) {
    partnerLock.lockAll(List.of(partnerId));
    var summary = findOrCreateForUpdate(partnerId);
    summary.updateReceivableBalance(receivableBalance, paymentEventReference(lastPaymentEventId));
    balanceRepository.save(summary);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void refreshUnassignedAmount(Long partnerId, Long eventId) {
    partnerLock.lockAll(List.of(partnerId));
    eventRepository.flush();
    var summary = findOrCreateForUpdate(partnerId);
    try {
      summary.updateUnappliedPaymentAmount(
          eventRepository.sumUnassignedAmount(partnerId).longValueExact(),
          paymentEventReference(eventId));
    } catch (ArithmeticException overflow) {
      throw new ConflictException("PAYMENT_BALANCE_LIMIT_EXCEEDED", "미배분 수납 잔액이 허용 범위를 초과합니다.");
    }
  }

  public void recordActivity(Long partnerId, Long lastPaymentEventId) {
    partnerLock.lockAll(List.of(partnerId));
    var summary = findOrCreateForUpdate(partnerId);
    summary.updateReceivableBalance(
        summary.getReceivableBalance(), paymentEventReference(lastPaymentEventId));
    balanceRepository.save(summary);
  }

  public PartnerBalanceSummaryResponse getBalance(Long partnerId) {
    var partner = partnerLock.lockAll(List.of(partnerId)).getFirst();
    return PartnerBalanceSummaryResponse.from(findOrCreateForUpdate(partnerId), partner.name());
  }

  private PartnerBalanceSummary findOrCreateForUpdate(Long partnerId) {
    return balanceRepository
        .findForUpdateByPartnerId(partnerId)
        .orElseGet(() -> balanceRepository.save(new PartnerBalanceSummary(partnerId)));
  }

  private PartnerPaymentEvent paymentEventReference(Long eventId) {
    return eventId == null ? null : eventRepository.getReferenceById(eventId);
  }
}
