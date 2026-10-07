package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.sales.domain.document.SalesSlip;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionSalesSlipCancellationPolicy {

  private final AuctionDocumentPort auctionShipmentLifecycleService;

  public void cancelShipmentIfPossible(SalesSlip salesSlip) {
    if (salesSlip.getAuctionShipmentId() == null) {
      return;
    }

    Long shipmentId = salesSlip.getAuctionShipmentId();
    if (auctionShipmentLifecycleService.existsByAuctionShipmentId(shipmentId)) {
      throw new IllegalArgumentException("정산에 반영된 경매 출하 전표는 취소할 수 없습니다.");
    }

    salesSlip.getItems().forEach(item -> item.clearAuctionShipmentLot());
    var shipment = salesSlip.getAuctionShipmentId();
    salesSlip.clearAuctionShipment();
    auctionShipmentLifecycleService.deleteDraftShipment(shipment);
  }

  public Set<Long> findNonCancelableShipmentIds(Collection<Long> shipmentIds) {
    if (shipmentIds.isEmpty()) {
      return Set.of();
    }
    Set<Long> blockedShipmentIds =
        new HashSet<>(auctionShipmentLifecycleService.findNonCancelableShipmentIds(shipmentIds));
    return Set.copyOf(blockedShipmentIds);
  }
}
