package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.sales.application.document.AuctionDocumentPort.Lot;
import com.greenhouse.backend.sales.application.document.AuctionDocumentPort.Shipment;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerReader;
import com.greenhouse.backend.sales.domain.auction.AuctionShipment;
import com.greenhouse.backend.sales.repository.auction.AuctionResultLineRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionResultReadRow;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionDataReader {

  private static final int RESULT_BATCH_SIZE = 500;

  private final BusinessPartnerReader partnerReader;

  private final AuctionShipmentRepository shipmentRepository;

  private final AuctionResultLineRepository resultLineRepository;

  public Map<Long, String> getMarketNames(Collection<Long> shipmentIds) {
    if (shipmentIds.isEmpty()) {
      return Map.of();
    }
    var shipments = shipmentRepository.findAllById(shipmentIds);
    var partners =
        partnerReader.getAllInfo(
            shipments.stream().map(AuctionShipment::getAuctionHouseId).toList());
    return shipments.stream()
        .collect(
            Collectors.toMap(
                AuctionShipment::getId,
                shipment -> partners.get(shipment.getAuctionHouseId()).name()));
  }

  public List<Long> getShipmentIdsNewestFirst(int page, int size) {
    return shipmentRepository.findIdsNewestFirst(
        PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
  }

  public List<Shipment> getShipmentsWithLotsNewestFirst(Collection<Long> shipmentIds) {
    if (shipmentIds.isEmpty()) {
      return List.of();
    }
    var shipments = shipmentRepository.findAllByIdInOrderByShipmentDateDescIdDesc(shipmentIds);
    var partners =
        partnerReader.getAllInfo(
            shipments.stream().map(AuctionShipment::getAuctionHouseId).toList());
    return shipments.stream()
        .map(
            shipment ->
                new Shipment(
                    shipment.getId(),
                    shipment.getShipmentDate(),
                    shipment.getAuctionHouseId(),
                    partners.get(shipment.getAuctionHouseId()).name(),
                    shipment.getLots().stream()
                        .map(
                            lot ->
                                new Lot(
                                    lot.getId(),
                                    lot.getItemName(),
                                    lot.getVarietyName(),
                                    lot.getShipmentGrade(),
                                    lot.getShippedQuantity()))
                        .toList()))
        .toList();
  }

  public Map<Long, Result> getResults(Collection<Long> resultIds) {
    if (resultIds.isEmpty()) {
      return Map.of();
    }
    var ids = resultIds.stream().distinct().toList();
    var results = new HashMap<Long, Result>();
    for (int start = 0; start < ids.size(); start += RESULT_BATCH_SIZE) {
      var batch = ids.subList(start, Math.min(start + RESULT_BATCH_SIZE, ids.size()));
      resultLineRepository
          .findReadRowsByIdIn(batch)
          .forEach(row -> results.put(row.id(), Result.from(row)));
    }
    return results;
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "AuctionProceedsResultReference")
  public record Result(
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long id,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long lotId,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long auctionHouseId,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          LocalDate auctionDate,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          LocalDate shipmentDate,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          String varietyName,
      @io.swagger.v3.oas.annotations.media.Schema(
              nullable = true,
              requiredMode = Schema.RequiredMode.REQUIRED)
          String shipmentGrade,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Integer quantity,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Integer unitPrice,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          Long amount) {
    static Result from(AuctionResultReadRow row) {
      return new Result(
          row.id(),
          row.lotId(),
          row.auctionHouseId(),
          row.auctionDate(),
          row.shipmentDate(),
          row.varietyName(),
          row.shipmentGrade(),
          row.quantity(),
          row.unitPrice(),
          row.amount().longValue());
    }
  }
}
