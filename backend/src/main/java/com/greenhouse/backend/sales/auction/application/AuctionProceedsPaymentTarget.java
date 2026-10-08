package com.greenhouse.backend.sales.auction.application;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.auction.api.AuctionProceedsResponse;
import com.greenhouse.backend.sales.auction.repository.AuctionProceedsRepository;
import com.greenhouse.backend.sales.partner.api.BusinessPartnerLockApi;
import com.greenhouse.backend.sales.payment.api.PartnerBalanceOperationsApi;
import com.greenhouse.backend.sales.payment.api.PaymentAllocationTargetOption;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.spi.PaymentAllocationTargetPort;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AuctionProceedsPaymentTarget
    implements PaymentAllocationTargetPort<AuctionProceedsResponse> {
  private final AuctionProceedsRepository repository;
  private final BusinessPartnerLockApi partners;
  private final PartnerBalanceOperationsApi balances;
  private final AuctionProceedsReader reader;
  private final AuditEventWriter audit;

  public PaymentTargetType targetType() {
    return PaymentTargetType.AUCTION_PROCEEDS;
  }

  public Map<Long, Long> findTargetOwners(Collection<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return repository.findPaymentOwners(ids).stream()
        .collect(Collectors.toMap(row -> row.getId(), row -> row.getPartnerId()));
  }

  public void lockAllocationTarget(Long id, Long partnerId) {
    var target =
        repository
            .findForUpdate(id)
            .orElseThrow(() -> new NotFoundException("경매 대금 자료를 찾을 수 없습니다."));
    if (!partnerId.equals(target.getAuctionHouseId()))
      throw new ConflictException("PAYMENT_TARGET_PARTNER_MISMATCH", "같은 거래처의 경매 대금에만 배분할 수 있습니다.");
  }

  public void lockAllocationAmounts(Collection<Long> ids) {}

  public PageResponse<PaymentAllocationTargetOption> allocationOptions(
      Long partnerId, String keyword, int page, int size) {
    return reader.allocationOptions(partnerId, keyword, page, size);
  }

  public Long lockAndValidate(Long id) {
    var partnerId =
        repository
            .findAuctionHouseId(id)
            .orElseThrow(() -> new NotFoundException("경매 대금 자료를 찾을 수 없습니다."));
    partners.lockAll(List.of(partnerId));
    repository.findForUpdate(id).orElseThrow(() -> new NotFoundException("경매 대금 자료를 찾을 수 없습니다."));
    return partnerId;
  }

  public Map<String, Object> paymentSnapshot(Long id) {
    var response = reader.get(id);
    var result = new LinkedHashMap<String, Object>();
    result.put("paidAmount", response.paidAmount());
    result.put("remainingAmount", response.remainingAmount());
    return result;
  }

  public void recordPayment(Long id, Long amount, String worker, LocalDateTime now) {
    var response = reader.get(id);
    repository
        .findById(id)
        .orElseThrow()
        .requireAllocation(response.paidAmount(), amount, response.reviewRequired());
    // Payment owns the received and allocation records; this target stores no paid summary.
  }

  public void updateBalance(Long id, Long eventId) {
    balances.recordActivity(repository.findById(id).orElseThrow().getAuctionHouseId(), eventId);
  }

  public void auditPayment(Long id, Map<String, Object> before) {
    audit.record(
        AuditAction.UPDATED,
        AuditSource.SALES_MANAGEMENT,
        "AUCTION_PROCEEDS",
        id,
        before,
        paymentSnapshot(id),
        Map.of(
            "targetType",
            "AUCTION_PROCEEDS",
            "partnerId",
            repository.findById(id).orElseThrow().getAuctionHouseId()));
  }

  public AuctionProceedsResponse response(Long id) {
    return reader.get(id);
  }
}
