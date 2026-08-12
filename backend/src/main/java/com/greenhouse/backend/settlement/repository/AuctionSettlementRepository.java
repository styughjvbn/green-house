package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AuctionSettlementRepository extends JpaRepository<AuctionSettlement, Long> {
	@Query("""
			select count(line) > 0
			from AuctionSettlementLine line
			where line.auctionShipmentLot.shipment.id = :shipmentId
			""")
	boolean existsByAuctionShipmentId(@Param("shipmentId") Long shipmentId);

	@Query("""
			select distinct line.auctionShipmentLot.shipment.id
			from AuctionSettlementLine line
			where line.auctionShipmentLot.shipment.id in :shipmentIds
			""")
	List<Long> findSettledAuctionShipmentIds(@Param("shipmentIds") Collection<Long> shipmentIds);

	Optional<AuctionSettlement> findByAuctionHouseIdAndAuctionDate(Long auctionHouseId, LocalDate auctionDate);

	@Query("""
			select resultLine from AuctionResultLine resultLine
			join fetch resultLine.auctionAttempt attempt
			join fetch attempt.shipmentLot lot
			join fetch lot.shipment shipment
			join fetch shipment.auctionHouse
			where resultLine.amount > 0
			  and not exists (
				select settlementLine.id from AuctionSettlementLine settlementLine
				where settlementLine.auctionResultLine = resultLine
			  )
			order by resultLine.auctionDate asc, resultLine.id asc
			""")
	List<AuctionResultLine> findUnsettledSoldResultLines();

	@EntityGraph(attributePaths = { "auctionHouse", "lines", "lines.auctionResultLine", "lines.auctionShipmentLot",
			"lines.auctionShipmentLot.shipment" })
	@Query("""
			select distinct settlement from AuctionSettlement settlement
			where settlement.auctionHouse.id in :auctionHouseIds
			  and settlement.auctionDate between :fromDate and :toDate
			""")
	List<AuctionSettlement> findAllWithDetailsForRebuild(
			@Param("auctionHouseIds") Collection<Long> auctionHouseIds,
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate);

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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select settlement from AuctionSettlement settlement where settlement.id = :id")
	Optional<AuctionSettlement> findForUpdateById(@Param("id") Long id);
}
