package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionSettlementRepository extends JpaRepository<AuctionSettlement, Long> {
	@Query("select distinct line.auctionShipmentLotId from AuctionSettlementLine line where line.auctionShipmentLotId in :lotIds")
	List<Long> findSettledLotIds(@Param("lotIds") Collection<Long> lotIds);

	@Query("select line.auctionResultLineId from AuctionSettlementLine line where line.auctionResultLineId in :resultIds")
	List<Long> findLinkedResultIds(@Param("resultIds") Collection<Long> resultIds);

	Optional<AuctionSettlement> findByAuctionHouseIdAndAuctionDate(Long auctionHouseId, LocalDate auctionDate);

	@EntityGraph(attributePaths = { "lines" })
	@Query("""
			select distinct settlement from AuctionSettlement settlement
			where settlement.auctionHouseId in :auctionHouseIds
			  and settlement.auctionDate between :fromDate and :toDate
			""")
	List<AuctionSettlement> findAllWithDetailsForRebuild(
			@Param("auctionHouseIds") Collection<Long> auctionHouseIds,
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate);

	@EntityGraph(attributePaths = { "lines" })
	@Query("""
			select distinct settlement from AuctionSettlement settlement
			where (:auctionHouseId is null or settlement.auctionHouseId = :auctionHouseId)
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

	@EntityGraph(attributePaths = { "lines" })
	Optional<AuctionSettlement> findWithDetailsById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select settlement from AuctionSettlement settlement where settlement.id = :id")
	Optional<AuctionSettlement> findForUpdateById(@Param("id") Long id);
}
