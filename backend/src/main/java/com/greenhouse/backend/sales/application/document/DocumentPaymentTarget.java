package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerLock;
import com.greenhouse.backend.sales.application.payment.PaymentTargetPort;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DocumentPaymentTarget implements PaymentTargetPort<SalesSlipDocument> {
  private final SalesSlipAggregateLoader loader;
  private final SalesSlipRepository repository;
  private final BusinessPartnerLock partners;
  private final DirectDocumentAccountingPort accounting;
  private final SalesSlipAuditSupport audit;
  private final SalesSlipDocumentAssembler assembler;

  public Long lockAndValidate(Long id) {
    var slip = loader.getForUpdate(id);
    slip.validatePaymentTarget();
    partners.lockAll(List.of(slip.getPartnerId()));
    return slip.getPartnerId();
  }

  public Map<String, Object> paymentSnapshot(Long id) {
    var value = accounting.findFinancials(List.of(id)).get(id);
    if (value == null)
      throw new ConflictException(
          "DIRECT_AMOUNT_SOURCE_MISSING", "일반 판매 금액 자료가 없어 입금을 처리할 수 없습니다.");
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("paidAmount", value.allocatedAmount());
    snapshot.put("remainingAmount", value.remainingAmount());
    snapshot.put("paymentStatus", value.paymentStatus());
    return snapshot;
  }

  public void recordPayment(Long id, Long amount, String worker, LocalDateTime now) {
    accounting.requirePaymentAmount(
        id, repository.findById(id).orElseThrow().getPartnerId(), amount);
  }

  public void updateBalance(Long id, Long eventId) {
    var slip = repository.findById(id).orElseThrow();
    var financial = accounting.findFinancials(List.of(id)).get(id);
    slip.applyFinancialProjection(
        financial.totalAmount(),
        financial.expectedPaymentDate(),
        financial.paymentMethod(),
        financial.allocatedAmount().longValueExact(),
        financial.remainingAmount().longValueExact(),
        financial.paymentStatus());
    repository.saveAndFlush(slip);
    accounting.updateReceivable(
        slip.getPartnerId(),
        repository.sumDirectReceivableByPartnerId(slip.getPartnerId()),
        eventId);
  }

  public void auditPayment(Long id, Map<String, Object> before) {
    var slip = repository.findById(id).orElseThrow();
    // The projection was just refreshed from Direct and the newly persisted valid allocation.
    audit.recordPayment(slip, before, audit.paymentSnapshot(slip));
  }

  public SalesSlipDocument response(Long id) {
    return assembler.assemble(repository.findById(id).orElseThrow());
  }
}
