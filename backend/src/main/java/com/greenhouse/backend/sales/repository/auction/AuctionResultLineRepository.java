package com.greenhouse.backend.sales.repository.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionResultLine;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
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
      select new com.greenhouse.backend.sales.repository.auction.AuctionResultReadRow(
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

  @Query("select coalesce(max(line.id), 0) from AuctionResultLine line where line.amount > 0")
  long findMaximumSoldId();

  @Query(
      "select line.id from AuctionResultLine line where line.amount > 0 and line.id > :afterId "
          + "and line.id <= :maximumId order by line.id")
  List<Long> findSoldIdsBetween(long afterId, long maximumId, Pageable pageable);

  // 결과 기반 조회로 전환할 때 통계 미갱신의 join 순서와 실제 scan/loop를 검증한다.
  // 20행 조회가 10,020행을 경유한 재현 근거: backend-audit/archive/14-performance-diagnosis.md
  @Query(
      READ_ROWS
          + "where line.amount > 0 and line.auctionDate = :auctionDate "
          + "and shipment.auctionHouseId = :auctionHouseId and line.id <= :maximumId order by line.id")
  List<AuctionResultReadRow> findSoldReadRowsUpTo(
      Long auctionHouseId, LocalDate auctionDate, long maximumId);

  @Query(READ_ROWS + "where line.id in :ids")
  List<AuctionResultReadRow> findReadRowsByIdIn(@Param("ids") Collection<Long> ids);
}
