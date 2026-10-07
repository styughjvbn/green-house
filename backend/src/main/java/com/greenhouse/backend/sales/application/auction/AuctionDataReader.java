package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.sales.application.document.AuctionDocumentPort.Lot;
import com.greenhouse.backend.sales.application.document.AuctionDocumentPort.Shipment;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerReader;
import com.greenhouse.backend.sales.domain.auction.AuctionShipment;
import com.greenhouse.backend.sales.repository.auction.AuctionResultLineRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionResultReadRow;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentLotRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentLotRepository.LotShipmentIdRow;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
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

  private final AuctionShipmentLotRepository lotRepository;

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

  public List<Result> getSoldResultLines(Long auctionHouseId, LocalDate auctionDate) {
    return resultLineRepository.findSoldReadRows(auctionHouseId, auctionDate).stream()
        .map(Result::from)
        .toList();
  }

  public long getMaximumSoldResultId() {
    return resultLineRepository.findMaximumSoldId();
  }

  public List<Long> getSoldResultIdsBetween(long afterId, long maximumId, int size) {
    return resultLineRepository.findSoldIdsBetween(
        afterId, maximumId, PageRequest.of(0, Math.min(Math.max(size, 1), RESULT_BATCH_SIZE)));
  }

  public List<Result> getSoldResultLinesUpTo(
      Long auctionHouseId, LocalDate auctionDate, long maximumId) {
    return resultLineRepository
        .findSoldReadRowsUpTo(auctionHouseId, auctionDate, maximumId)
        .stream()
        .map(Result::from)
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

  public Map<Long, Long> getLotShipmentIds(Collection<Long> shipmentIds) {
    if (shipmentIds.isEmpty()) {
      return Map.of();
    }
    return lotRepository.findLotShipmentIds(shipmentIds).stream()
        .collect(Collectors.toMap(LotShipmentIdRow::getLotId, LotShipmentIdRow::getShipmentId));
  }

  public record Result(
      Long id,
      Long lotId,
      Long auctionHouseId,
      LocalDate auctionDate,
      LocalDate shipmentDate,
      String varietyName,
      String shipmentGrade,
      Integer quantity,
      Integer unitPrice,
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
