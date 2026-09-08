package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionResultLineRepository extends JpaRepository<AuctionResultLine, Long> {

	@EntityGraph(
			attributePaths = { "auctionAttempt", "auctionAttempt.shipmentLot", "auctionAttempt.shipmentLot.shipment" })
	@Query("""
			select line from AuctionResultLine line
			where line.amount > 0 and line.auctionDate = :auctionDate
			  and line.auctionAttempt.shipmentLot.shipment.auctionHouseId = :auctionHouseId
			order by line.id asc
			""")
	List<AuctionResultLine> findSoldLines(@Param("auctionHouseId") Long auctionHouseId,
			@Param("auctionDate") LocalDate auctionDate);

	@Query("select line.id from AuctionResultLine line where line.amount > 0 and line.id > :afterId order by line.id")
	List<Long> findSoldIdsAfter(@Param("afterId") Long afterId, Pageable pageable);

	@EntityGraph(
			attributePaths = { "auctionAttempt", "auctionAttempt.shipmentLot", "auctionAttempt.shipmentLot.shipment" })
	List<AuctionResultLine> findAllByIdIn(Collection<Long> ids);

}
