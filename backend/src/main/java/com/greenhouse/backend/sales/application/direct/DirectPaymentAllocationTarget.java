package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.document.api.DocumentPaymentApi;
import com.greenhouse.backend.sales.payment.api.PaymentAllocationTargetOption;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.spi.PaymentAllocationTargetPort;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direct implements Payment's multi-allocation contract; Document retains common projection writes.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DirectPaymentAllocationTarget
    implements PaymentAllocationTargetPort<SalesSlipDocument> {
  private final DocumentPaymentApi document;

  public PaymentTargetType targetType() {
    return PaymentTargetType.SALES_SLIP;
  }

  public Map<Long, Long> findTargetOwners(Collection<Long> ids) {
    return document.findPaymentOwners(ids);
  }

  public void lockAllocationTarget(Long id, Long partnerId) {
    document.lockAllocationTarget(id, partnerId);
  }

  public void lockAllocationAmounts(Collection<Long> ids) {
    document.lockAllocationAmounts(ids);
  }

  public PageResponse<PaymentAllocationTargetOption> allocationOptions(
      Long partnerId, String keyword, int page, int size) {
    var values = document.allocationOptions(partnerId, keyword, page, size);
    return new PageResponse<>(
        values.content().stream()
            .map(
                value ->
                    new PaymentAllocationTargetOption(
                        value.id(),
                        targetType(),
                        value.sourceReference(),
                        value.receivableAmount(),
                        value.paidAmount(),
                        value.availableAmount(),
                        value.allocationAllowed(),
                        value.correctionAllowed(),
                        value.reviewRequired()))
            .toList(),
        values.page(),
        values.size(),
        values.totalElements(),
        values.totalPages());
  }

  public Long lockAndValidate(Long id) {
    return document.lockAndValidate(id);
  }

  public Map<String, Object> paymentSnapshot(Long id) {
    return document.paymentSnapshot(id);
  }

  public void recordPayment(Long id, Long amount, String worker, LocalDateTime now) {
    document.recordPayment(id, amount, worker, now);
  }

  public void updateBalance(Long id, Long eventId) {
    document.updateBalance(id, eventId);
  }

  public void auditPayment(Long id, Map<String, Object> before) {
    document.auditPayment(id, before);
  }

  public SalesSlipDocument response(Long id) {
    return document.response(id);
  }
}
