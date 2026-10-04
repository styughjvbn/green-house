package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.SalesType;
import java.time.LocalDate;
import java.util.List;

/** Legacy v1 receipt hash input, independent of future command fields. */
record SalesCreationRequestPayload(
    LocalDate saleDate,
    SalesType salesType,
    Long partnerId,
    Long auctionShipmentId,
    String paymentStatus,
    String salesStatus,
    String paymentMethod,
    String memo,
    List<Item> items) {

  static SalesCreationRequestPayload from(SalesSlipCommand request) {
    return request == null
        ? null
        : new SalesCreationRequestPayload(
            request.saleDate(),
            request.salesType(),
            request.partnerId(),
            request.auctionShipmentId(),
            request.paymentStatus(),
            request.salesStatus(),
            request.paymentMethod(),
            request.memo(),
            request.items() == null ? null : request.items().stream().map(Item::from).toList());
  }

  private record Item(
      String itemName,
      String genus,
      String spec,
      Integer quantity,
      Integer unitPrice,
      String memo,
      List<Allocation> allocations) {
    static Item from(SalesSlipItemInput value) {
      return value == null
          ? null
          : new Item(
              value.itemName(),
              value.genus(),
              value.spec(),
              value.quantity(),
              value.unitPrice(),
              value.memo(),
              value.allocations() == null
                  ? null
                  : value.allocations().stream()
                      .map(
                          allocation ->
                              allocation == null
                                  ? null
                                  : new Allocation(
                                      allocation.orchidGroupId(), allocation.quantity()))
                      .toList());
    }
  }

  private record Allocation(Long orchidGroupId, Integer quantity) {}
}
