package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DocumentPaymentTarget {
  private final SalesSlipAggregateLoader loader;
  private final SalesSlipRepository repository;
  private final DirectDocumentAccountingPort accounting;
  private final SalesSlipAuditSupport audit;
  private final SalesSlipDocumentAssembler assembler;

  public Map<Long, Long> findPaymentOwners(Collection<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return repository.findPaymentOwners(ids).stream()
        .collect(Collectors.toMap(row -> row.getId(), row -> row.getPartnerId()));
  }

  public void lockAllocationTarget(Long id, Long partnerId) {
    var slip =
        repository
            .findForUpdateById(id)
            .orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
    if (!partnerId.equals(slip.getPartnerId()) || slip.getSalesType() != SalesType.DIRECT)
      throw new ConflictException(
          "PAYMENT_TARGET_PARTNER_MISMATCH", "같은 거래처의 일반 판매 전표에만 배분할 수 있습니다.");
  }

  public void lockAllocationAmounts(Collection<Long> ids) {
    accounting.lockPaymentTargets(ids);
  }

  public PageResponse<PaymentOption> allocationOptions(
      Long partnerId, String keyword, int page, int size) {
    var roots =
        repository.findPaymentTargets(
            partnerId, keyword, PageRequest.of(page, size, Sort.by("id").descending()));
    var values = accounting.findFinancials(roots.stream().map(slip -> slip.getId()).toList());
    return PageResponse.from(
        roots.map(
            slip -> {
              var value = values.get(slip.getId());
              return new PaymentOption(
                  slip.getId(),
                  slip.getSlipNumber(),
                  value == null ? null : value.totalAmount().longValue(),
                  value == null ? null : value.allocatedAmount(),
                  value == null ? null : value.remainingAmount(),
                  value != null && value.paymentAllowed(),
                  value != null && value.allocationCorrectionAllowed(),
                  value == null || value.reviewRequired());
            }));
  }

  public record PaymentOption(
      Long id,
      String sourceReference,
      Long receivableAmount,
      BigDecimal paidAmount,
      BigDecimal availableAmount,
      boolean allocationAllowed,
      boolean correctionAllowed,
      boolean reviewRequired) {}

  public Long lockAndValidate(Long id) {
    var slip = loader.getForUpdate(id);
    slip.validatePaymentTarget();
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
    repository.findById(id).orElseThrow().validatePaymentTarget();
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
