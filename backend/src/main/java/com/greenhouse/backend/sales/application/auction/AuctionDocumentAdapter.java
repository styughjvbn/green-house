package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.sales.application.auction.settlement.AuctionSettlementReader;
import com.greenhouse.backend.sales.application.document.AuctionDocumentPort;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionDocumentAdapter implements AuctionDocumentPort {
  private final AuctionShipmentCreator creator;
  private final AuctionShipmentLifecycleService lifecycle;
  private final AuctionDataReader reader;
  private final AuctionSettlementReader settlements;

  public CreatedShipment create(LocalDate date, Long partnerId, List<LotDraft> drafts) {
    return creator.create(date, partnerId, drafts);
  }

  public boolean existsByAuctionShipmentId(Long id) {
    return settlements.existsByAuctionShipmentId(id);
  }

  public void deleteDraftShipment(Long id) {
    lifecycle.deleteDraftShipment(id);
  }

  public Set<Long> findNonCancelableShipmentIds(Collection<Long> ids) {
    var blocked = new HashSet<>(settlements.findSettledAuctionShipmentIds(ids));
    blocked.addAll(lifecycle.findNonCancelableShipmentIds(ids));
    return Set.copyOf(blocked);
  }

  public Map<Long, String> getMarketNames(Collection<Long> ids) {
    return reader.getMarketNames(ids);
  }

  public List<Long> getShipmentIdsNewestFirst(int page, int size) {
    return reader.getShipmentIdsNewestFirst(page, size);
  }

  public List<Shipment> getShipmentsWithLotsNewestFirst(Collection<Long> ids) {
    return reader.getShipmentsWithLotsNewestFirst(ids);
  }
}
