package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Finite candidate scan; each auction-house/day is committed by the write service. */
@Service
@RequiredArgsConstructor
public class AuctionSettlementRebuildService {
  private static final int BATCH_SIZE = 500;
  private final AuctionDataReader auctionReader;
  private final AuctionSettlementRepository settlements;
  private final AuctionSettlementService settlementService;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;

  // TODO: 별도 일괄 정산 구현 시 이 경로의 재사용 여부와 통계/캐시 계획의 대량 처리 비용을 검증한다.
  // 정산별 commit, 잠금 후 미연결 결과 재확인, 재실행 시 중복 방어를 유지한다.
  @Transactional(propagation = Propagation.NEVER)
  public int rebuildExistingResults() {
    long maximumId = auctionReader.getMaximumSoldResultId();
    if (maximumId == 0) return 0;
    var receivedAt = TimeConfig.utcNow(clock);
    var affected = new HashSet<SettlementKey>();
    var readTransaction = new TransactionTemplate(transactionManager);
    readTransaction.setReadOnly(true);
    long afterId = 0;
    while (afterId < maximumId) {
      long cursor = afterId;
      var batch = readTransaction.execute(tx -> readCandidates(cursor, maximumId));
      if (batch.ids().isEmpty()) break;
      for (var key : batch.keys()) {
        if (settlementService.appendUnlinkedResults(
            key.houseId(), key.date(), maximumId, receivedAt)) affected.add(key);
      }
      afterId = batch.ids().getLast();
      if (batch.ids().size() < BATCH_SIZE) break;
    }
    return affected.size();
  }

  private CandidateBatch readCandidates(long afterId, long maximumId) {
    var ids = auctionReader.getSoldResultIdsBetween(afterId, maximumId, BATCH_SIZE);
    if (ids.isEmpty()) return new CandidateBatch(ids, List.of());
    var linked = new HashSet<>(settlements.findLinkedResultIds(ids));
    var unlinked = ids.stream().filter(id -> !linked.contains(id)).toList();
    var keys =
        auctionReader.getResults(unlinked).values().stream()
            .sorted(
                Comparator.comparing(AuctionDataReader.Result::auctionDate)
                    .thenComparing(AuctionDataReader.Result::id))
            .map(line -> new SettlementKey(line.auctionHouseId(), line.auctionDate()))
            .distinct()
            .toList();
    return new CandidateBatch(ids, keys);
  }

  private record CandidateBatch(List<Long> ids, List<SettlementKey> keys) {}

  private record SettlementKey(Long houseId, LocalDate date) {}
}
