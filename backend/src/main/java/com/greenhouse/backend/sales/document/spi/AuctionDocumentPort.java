package com.greenhouse.backend.sales.document.spi;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shipment operations required by common documents; no auction aggregate escapes. */
public interface AuctionDocumentPort {
  CreatedShipment create(LocalDate date, Long partnerId, List<LotDraft> drafts);

  boolean existsByAuctionShipmentId(Long shipmentId);

  void deleteDraftShipment(Long shipmentId);

  Set<Long> findNonCancelableShipmentIds(Collection<Long> shipmentIds);

  Map<Long, String> getMarketNames(Collection<Long> shipmentIds);

  List<Long> getShipmentIdsNewestFirst(int page, int size);

  List<Shipment> getShipmentsWithLotsNewestFirst(Collection<Long> shipmentIds);

  public record LotDraft(
      Long sourceItemId,
      String itemName,
      String varietyName,
      String shipmentGrade,
      Integer quantity) {}

  public record CreatedShipment(Long id, Map<Long, Long> lotIdsBySourceItemId) {
    public CreatedShipment {
      lotIdsBySourceItemId = Map.copyOf(lotIdsBySourceItemId);
    }
  }

  public record Shipment(
      Long id, LocalDate shipmentDate, Long auctionHouseId, String auctionMarket, List<Lot> lots) {
    public Shipment {
      lots = List.copyOf(lots);
    }
  }

  public record Lot(
      Long id,
      String itemName,
      String varietyName,
      String shipmentGrade,
      Integer shippedQuantity) {}
}
