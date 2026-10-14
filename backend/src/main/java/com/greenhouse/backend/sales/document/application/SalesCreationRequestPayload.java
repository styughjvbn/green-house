package com.greenhouse.backend.sales.document.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.document.application.command.SalesInitialReceiptInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.application.command.SalesSlipItemInput;
import java.time.LocalDate;
import java.util.List;

/**
 * Preserves legacy v1 hashes when no receipt is supplied; opt-in receipt fields participate in the
 * hash.
 */
record SalesCreationRequestPayload(
    LocalDate saleDate,
    SalesType salesType,
    Long partnerId,
    Long auctionShipmentId,
    String paymentStatus,
    String salesStatus,
    String paymentMethod,
    String memo,
    List<Item> items,
    @JsonInclude(JsonInclude.Include.NON_NULL) SalesInitialReceiptInput initialReceipt,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean recordOnly) {

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
            request.items() == null ? null : request.items().stream().map(Item::from).toList(),
            request.initialReceipt(),
            Boolean.TRUE.equals(request.recordOnly()) ? true : null);
  }

  private record Item(
      String itemName,
      String genus,
      String spec,
      Integer quantity,
      Integer unitPrice,
      String memo,
      List<Allocation> allocations,
      @JsonInclude(JsonInclude.Include.NON_NULL) String unit,
      @JsonInclude(JsonInclude.Include.NON_NULL) Integer boxes) {
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
                      .toList(),
              value.unit(),
              value.boxes());
    }
  }

  private record Allocation(Long orchidGroupId, Integer quantity) {}
}
