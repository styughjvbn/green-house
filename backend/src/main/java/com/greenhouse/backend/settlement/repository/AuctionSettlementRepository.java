package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionSettlementRepository extends JpaRepository<AuctionSettlement, Long> {
	@Query("""
			select count(line) > 0
			from AuctionSettlementLine line
			where line.auctionShipmentLot.shipment.id = :shipmentId
			""")
	boolean existsByAuctionShipmentId(@Param("shipmentId") Long shipmentId);

	Optional<AuctionSettlement> findByAuctionHouseIdAndAuctionDate(Long auctionHouseId, LocalDate auctionDate);

	@EntityGraph(attributePaths = { "auctionHouse", "lines", "lines.auctionResultLine", "lines.auctionShipmentLot",
			"lines.auctionShipmentLot.shipment" })
	@Query("""
			select distinct settlement from AuctionSettlement settlement
			where (:auctionHouseId is null or settlement.auctionHouse.id = :auctionHouseId)
			  and (:fromDate is null or settlement.auctionDate >= :fromDate)
			  and (:toDate is null or settlement.auctionDate <= :toDate)
			  and (:status is null or settlement.status = :status)
			order by settlement.auctionDate desc, settlement.id desc
			""")
	List<AuctionSettlement> search(
			@Param("auctionHouseId") Long auctionHouseId,
			@Param("fromDate") LocalDate from,
			@Param("toDate") LocalDate to,
			@Param("status") AuctionSettlementStatus status);

	@EntityGraph(attributePaths = { "auctionHouse", "lines", "lines.auctionResultLine", "lines.auctionShipmentLot",
			"lines.auctionShipmentLot.shipment" })
	Optional<AuctionSettlement> findWithDetailsById(Long id);
}
