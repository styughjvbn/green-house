package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionResultLineRepository extends JpaRepository<AuctionResultLine, Long> {

  String READ_ROWS =
      """
      select new com.greenhouse.backend.auction.repository.AuctionResultReadRow(
          line.id, lot.id, shipment.auctionHouseId, line.auctionDate, shipment.shipmentDate,
          lot.varietyName, lot.shipmentGrade, line.quantity, line.unitPrice, line.amount)
      from AuctionResultLine line
      join line.auctionAttempt attempt
      join attempt.shipmentLot lot
      join lot.shipment shipment
      """;

  @Query(
      READ_ROWS
          + """
      where line.amount > 0 and line.auctionDate = :auctionDate
        and shipment.auctionHouseId = :auctionHouseId
      order by line.id asc
      """)
  List<AuctionResultReadRow> findSoldReadRows(
      @Param("auctionHouseId") Long auctionHouseId, @Param("auctionDate") LocalDate auctionDate);

  @Query(
      "select line.id from AuctionResultLine line where line.amount > 0 and line.id > :afterId order by line.id")
  List<Long> findSoldIdsAfter(@Param("afterId") Long afterId, Pageable pageable);

  @Query(READ_ROWS + "where line.id in :ids")
  List<AuctionResultReadRow> findReadRowsByIdIn(@Param("ids") Collection<Long> ids);
}
