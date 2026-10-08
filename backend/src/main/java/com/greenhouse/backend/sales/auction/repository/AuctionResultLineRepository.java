package com.greenhouse.backend.sales.auction.repository;

import com.greenhouse.backend.sales.auction.domain.AuctionResultLine;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionResultLineRepository extends JpaRepository<AuctionResultLine, Long> {

  @Query(
      "select line from AuctionResultLine line join fetch line.auctionAttempt attempt "
          + "join fetch attempt.shipmentLot lot join fetch lot.shipment where line.id in :ids")
  List<AuctionResultLine> findAllWithLotByIdIn(@Param("ids") Collection<Long> ids);

  String READ_ROWS =
      """
      select new com.greenhouse.backend.sales.auction.repository.AuctionResultReadRow(
          line.id, lot.id, shipment.auctionHouseId, line.auctionDate, shipment.shipmentDate,
          lot.varietyName, lot.shipmentGrade, line.quantity, line.unitPrice, line.amount)
      from AuctionResultLine line
      join line.auctionAttempt attempt
      join attempt.shipmentLot lot
      join lot.shipment shipment
      """;

  @Query(READ_ROWS + "where line.id in :ids")
  List<AuctionResultReadRow> findReadRowsByIdIn(@Param("ids") Collection<Long> ids);
}
