package com.greenhouse.backend.sales.application.auction.settlement;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerLock;
import com.greenhouse.backend.sales.application.payment.PartnerBalanceService;
import com.greenhouse.backend.sales.application.payment.PaymentLedgerService;
import com.greenhouse.backend.sales.application.payment.PaymentTargetPort;
import com.greenhouse.backend.sales.domain.auction.settlement.AuctionSettlement;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.dto.auction.settlement.AuctionSettlementResponse;
import com.greenhouse.backend.sales.repository.auction.settlement.AuctionSettlementRepository;
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
public class AuctionSettlementPaymentTarget
    implements PaymentTargetPort<AuctionSettlementResponse> {
  private final AuctionSettlementRepository repository;
  private final BusinessPartnerLock partners;
  private final PartnerBalanceService balances;
  private final PaymentLedgerService ledger;
  private final SettlementAuditSupport audit;
  private final AuctionSettlementResponseAssembler assembler;

  public Long lockAndValidate(Long id) {
    Long partnerId =
        repository
            .findAuctionHouseId(id)
            .orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
    partners.lockAll(List.of(partnerId));
    repository.findForUpdateById(id).orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
    return partnerId;
  }

  private AuctionSettlement get(Long id) {
    return repository.findById(id).orElseThrow();
  }

  public Map<String, Object> paymentSnapshot(Long id) {
    return audit.auctionPaymentSnapshot(get(id));
  }

  public void recordPayment(Long id, Long amount, String worker, LocalDateTime now) {
    if (ledger.isTargetMigrated(PaymentTargetType.AUCTION_SETTLEMENT, id))
      throw new ConflictException(
          "AUCTION_SETTLEMENT_TARGET_RETIRED",
          "입금 대상이 전환된 정산에는 새 입금을 연결할 수 없습니다. 지급 근거가 확인된 경매 대금을 사용하세요.");
    get(id).recordPayment(amount, worker, now);
  }

  public void updateBalance(Long id, Long eventId) {
    balances.recordActivity(get(id).getAuctionHouseId(), eventId);
  }

  public void auditPayment(Long id, Map<String, Object> before) {
    var saved = repository.save(get(id));
    audit.recordAuctionPayment(saved, before, audit.auctionPaymentSnapshot(saved));
  }

  public AuctionSettlementResponse response(Long id) {
    return assembler.assemble(get(id));
  }
}
