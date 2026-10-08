package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupQueryApi;
import com.greenhouse.backend.sales.api.document.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.application.document.AuctionDocumentPort.LotDraft;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipOutboundService {

  private final SalesSlipInventoryService inventoryService;

  private final AuctionDocumentPort shipmentCreator;

  private final OrchidGroupQueryApi orchidGroupReader;

  private final Clock clock;

  public void complete(SalesSlip salesSlip) {
    SalesSlipAllocationBatch allocations = SalesSlipAllocationBatch.from(salesSlip);
    var states = orchidGroupReader.lockStates(allocations.orchidGroupIds());
    allocations.captureSnapshot(SalesOrchidSnapshotType.OUTBOUND, TimeConfig.utcNow(clock), states);
    createAuctionShipment(salesSlip);
    inventoryService.outbound(allocations);
  }

  private void createAuctionShipment(SalesSlip slip) {
    if (slip.getSalesType() != SalesType.AUCTION || slip.getAuctionShipmentId() != null) {
      return;
    }
    var drafts =
        slip.getItems().stream()
            .map(
                item ->
                    new LotDraft(
                        item.getId(),
                        SalesTextNormalizer.required(
                            item.getGenus() == null || item.getGenus().isBlank()
                                ? item.getItemName()
                                : item.getGenus()),
                        SalesTextNormalizer.required(item.getItemName()),
                        SalesTextNormalizer.normalize(item.getSpec()),
                        item.getQuantity()))
            .toList();
    var shipment = shipmentCreator.create(slip.getSaleDate(), slip.getPartnerId(), drafts);
    slip.assignAuctionShipment(shipment.id());
    slip.getItems()
        .forEach(
            item ->
                item.assignAuctionShipmentLot(shipment.lotIdsBySourceItemId().get(item.getId())));
  }
}
