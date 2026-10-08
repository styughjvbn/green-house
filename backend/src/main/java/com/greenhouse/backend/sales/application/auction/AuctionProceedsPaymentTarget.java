package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerLock;
import com.greenhouse.backend.sales.application.payment.PartnerBalanceService;
import com.greenhouse.backend.sales.application.payment.PaymentTargetPort;
import com.greenhouse.backend.sales.dto.auction.AuctionProceedsResponse;
import com.greenhouse.backend.sales.repository.auction.AuctionProceedsRepository;
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
public class AuctionProceedsPaymentTarget implements PaymentTargetPort<AuctionProceedsResponse> {
  private final AuctionProceedsRepository repository;
  private final BusinessPartnerLock partners;
  private final PartnerBalanceService balances;
  private final AuctionProceedsReader reader;
  private final AuditEventWriter audit;

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
