package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerLock;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerReader;
import com.greenhouse.backend.sales.domain.auction.AuctionInspectionStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionProceeds;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.repository.auction.AuctionProceedsRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionResultLineRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for supplied evidence; parser and matching automation are separate. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionProceedsService {
  private final AuctionProceedsRepository proceedsRepository;
  private final AuctionResultLineRepository resultRepository;
  private final BusinessPartnerReader partners;
  private final Clock clock;
  private final BusinessPartnerLock partnerLock;

  @Transactional
  public Long record(
      Long auctionHouseId,
      String sourceReference,
      Long reportedGrossAmount,
      Long receivableAmount,
      List<Long> resultIds) {
    if (partners.getActiveInfo(auctionHouseId).partnerType() != PartnerType.AUCTION_HOUSE)
      throw new IllegalArgumentException("경매 대금 자료에는 경매장 유형 거래처가 필요합니다.");
    if (resultIds == null || resultIds.isEmpty() || resultIds.size() > 500)
      throw new IllegalArgumentException("대금 자료에는 1개 이상 500개 이하의 결과 참조가 필요합니다.");
    partnerLock.lockAll(List.of(auctionHouseId));
    var proceeds =
        new AuctionProceeds(
            auctionHouseId, sourceReference, reportedGrossAmount, receivableAmount, resultIds);
    requireResults(proceeds, false);
    return proceedsRepository.saveAndFlush(proceeds).getId();
  }

  @Transactional
  public void confirm(Long id, String worker) {
    Long partnerId =
        proceedsRepository
            .findAuctionHouseId(id)
            .orElseThrow(() -> new NotFoundException("경매 대금 자료를 찾을 수 없습니다."));
    partnerLock.lockAll(List.of(partnerId));
    var proceeds =
        proceedsRepository
            .findForUpdate(id)
            .orElseThrow(() -> new NotFoundException("경매 대금 자료를 찾을 수 없습니다."));
    long gross = requireResults(proceeds, true);
    proceeds.confirmMatching(gross, worker, TimeConfig.utcNow(clock));
  }

  private long requireResults(AuctionProceeds proceeds, boolean confirming) {
    var ids = proceeds.getResults().stream().map(result -> result.getResultLineId()).toList();
    var lines = resultRepository.findAllWithLotByIdIn(ids);
    if (lines.size() != ids.size())
      throw new ConflictException("AUCTION_PROCEEDS_RESULT_MISSING", "연결할 경매 결과를 모두 찾을 수 없습니다.");
    long gross = 0;
    for (var line : lines) {
      if (!Objects.equals(
          line.getAuctionAttempt().getShipmentLot().getShipment().getAuctionHouseId(),
          proceeds.getAuctionHouseId()))
        throw new ConflictException("AUCTION_PROCEEDS_MARKET_MISMATCH", "같은 경매장의 결과만 연결할 수 있습니다.");
      if (confirming
          && AuctionInspectionStatus.reviewStatuses().contains(line.getInspectionStatus()))
        throw new ConflictException(
            "AUCTION_PROCEEDS_RESULT_REVIEW", "검토가 필요한 경매 결과는 대금 자료 연결을 확인할 수 없습니다.");
      if (line.getAmount() == null || line.getAmount() < 0)
        throw new ConflictException("AUCTION_PROCEEDS_RESULT_REVIEW", "경매 결과 금액을 확인해야 합니다.");
      gross = Math.addExact(gross, line.getAmount());
    }
    return gross;
  }
}
