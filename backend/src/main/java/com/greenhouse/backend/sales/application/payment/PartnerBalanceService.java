package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.domain.payment.PartnerBalanceSummary;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.dto.payment.PartnerBalanceSummaryResponse;
import com.greenhouse.backend.sales.partner.api.BusinessPartnerLockApi;
import com.greenhouse.backend.sales.repository.payment.PartnerBalanceSummaryRepository;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
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
public class PartnerBalanceService {

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

  public record Balance(long receivableBalance, long creditBalance, long unappliedPaymentAmount) {

    public static final Balance ZERO = new Balance(0, 0, 0);

    public boolean hasPositiveBalance() {
      return receivableBalance > 0 || creditBalance > 0 || unappliedPaymentAmount > 0;
    }
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
