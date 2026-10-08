package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.api.document.SalesSlipDocumentItem;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
import com.greenhouse.backend.sales.domain.document.SalesSlipItem;
import com.greenhouse.backend.sales.domain.document.SalesSlipItemAllocation;
import java.util.List;
import java.util.Map;

final class SalesSlipDocumentItemFactory {

  private SalesSlipDocumentItemFactory() {}

  public static SalesSlipDocumentItem from(
      SalesSlipItem item,
      List<SalesSlipItemAllocation> allocations,
      Map<Long, OrchidGroupState> states,
      DirectDocumentAccountingPort.PriceSnapshot price) {
    return new SalesSlipDocumentItem(
        item.getId(),
        item.getAuctionShipmentLotId(),
        item.getItemName(),
        item.getGenus(),
        item.getSpec(),
        item.getQuantity(),
        price == null ? item.getUnitPrice() : price.unitPrice(),
        price == null ? item.getAmount() : price.amount(),
        item.getMemo(),
        allocations.stream()
            .map(
                allocation ->
                    SalesSlipDocumentAllocationFactory.from(
                        allocation, states.get(allocation.getOrchidGroupId())))
            .toList());
  }
}
