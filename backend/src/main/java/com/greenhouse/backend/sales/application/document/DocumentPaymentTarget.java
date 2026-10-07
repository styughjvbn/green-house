package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.sales.application.partner.BusinessPartnerLock;
import com.greenhouse.backend.sales.application.payment.PaymentTargetPort;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import java.time.LocalDateTime;
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
    return audit.paymentSnapshot(repository.findById(id).orElseThrow());
  }

  public void recordPayment(Long id, Long amount, String worker, LocalDateTime now) {
    var slip = repository.findById(id).orElseThrow();
    slip.recordPayment(amount);
    repository.save(slip);
  }

  public void updateBalance(Long id, Long eventId) {
    var slip = repository.findById(id).orElseThrow();
    accounting.updateReceivable(
        slip.getPartnerId(),
        repository.sumDirectReceivableByPartnerId(slip.getPartnerId()),
        eventId);
  }

  public void auditPayment(Long id, Map<String, Object> before) {
    var slip = repository.findById(id).orElseThrow();
    audit.recordPayment(slip, before, audit.paymentSnapshot(slip));
  }

  public SalesSlipDocument response(Long id) {
    return assembler.assemble(repository.findById(id).orElseThrow());
  }
}
