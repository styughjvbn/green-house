package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionResultLineRepository extends JpaRepository<AuctionResultLine, Long> {
	@EntityGraph(attributePaths = {"auctionAttempt", "auctionAttempt.shipmentLot", "auctionAttempt.shipmentLot.shipment", "auctionAttempt.shipmentLot.shipment.auctionHouse"})
	@Query("""
		select line from AuctionResultLine line
		where line.amount > 0
		  and line.auctionDate = :auctionDate
		  and line.auctionAttempt.shipmentLot.shipment.auctionHouse.id = :auctionHouseId
		order by line.id
		""")
	List<AuctionResultLine> findSoldLines(
		@Param("auctionHouseId") Long auctionHouseId,
		@Param("auctionDate") LocalDate auctionDate);

	@EntityGraph(attributePaths = {"auctionAttempt", "auctionAttempt.shipmentLot", "auctionAttempt.shipmentLot.shipment", "auctionAttempt.shipmentLot.shipment.auctionHouse"})
	@Query("select line from AuctionResultLine line where line.amount > 0 order by line.auctionDate, line.id")
	List<AuctionResultLine> findAllSoldLines();
}
