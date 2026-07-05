package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;

public interface AuctionShipmentLotRepository extends JpaRepository<AuctionShipmentLot, Long> {
	@EntityGraph(attributePaths = "shipment")
	List<AuctionShipmentLot> findAllByOrderByIdDesc();

	@EntityGraph(attributePaths = "shipment")
	Optional<AuctionShipmentLot> findWithDetailsById(Long id);

	@Query(
		value = """
			select lot from AuctionShipmentLot lot
			join fetch lot.shipment shipment
			where (:fromDate is null or shipment.shipmentDate >= :fromDate)
			  and (:toDate is null or shipment.shipmentDate <= :toDate)
			  and (:market = '' or lower(shipment.auctionMarket) = lower(:market))
			  and (:variety = '' or lower(lot.varietyName) like lower(concat('%', :variety, '%')))
			  and (:grade = '' or lot.shipmentGrade = :grade)
			  and (:status is null or lot.currentStatus = :status)
			  and (:returnOnly = false or lot.currentStatus in :returnStatuses)
			  and (:waitingOnly = false or lot.currentStatus in :waitingStatuses)
			  and (:reviewOnly = false or lot.currentStatus in :reviewStatuses or exists (
			    select line.id from AuctionResultLine line
			    where line.auctionAttempt.shipmentLot = lot and line.inspectionStatus in :reviewInspections
			  ))
			  and (:keyword = '' or lower(concat(concat(concat(lot.itemName, ' '), lot.varietyName), concat(' ', shipment.auctionMarket))) like lower(concat('%', :keyword, '%')))
			""",
		countQuery = """
			select count(lot) from AuctionShipmentLot lot
			join lot.shipment shipment
			where (:fromDate is null or shipment.shipmentDate >= :fromDate)
			  and (:toDate is null or shipment.shipmentDate <= :toDate)
			  and (:market = '' or lower(shipment.auctionMarket) = lower(:market))
			  and (:variety = '' or lower(lot.varietyName) like lower(concat('%', :variety, '%')))
			  and (:grade = '' or lot.shipmentGrade = :grade)
			  and (:status is null or lot.currentStatus = :status)
			  and (:returnOnly = false or lot.currentStatus in :returnStatuses)
			  and (:waitingOnly = false or lot.currentStatus in :waitingStatuses)
			  and (:reviewOnly = false or lot.currentStatus in :reviewStatuses or exists (
			    select line.id from AuctionResultLine line
			    where line.auctionAttempt.shipmentLot = lot and line.inspectionStatus in :reviewInspections
			  ))
			  and (:keyword = '' or lower(concat(concat(concat(lot.itemName, ' '), lot.varietyName), concat(' ', shipment.auctionMarket))) like lower(concat('%', :keyword, '%')))
			""")
	Page<AuctionShipmentLot> search(
		@Param("fromDate") LocalDate from,
		@Param("toDate") LocalDate to,
		@Param("market") String market,
		@Param("variety") String variety,
		@Param("grade") String grade,
		@Param("status") AuctionLotStatus status,
		@Param("reviewOnly") boolean reviewOnly,
		@Param("returnOnly") boolean returnOnly,
		@Param("waitingOnly") boolean waitingOnly,
		@Param("keyword") String keyword,
		@Param("returnStatuses") List<AuctionLotStatus> returnStatuses,
		@Param("waitingStatuses") List<AuctionLotStatus> waitingStatuses,
		@Param("reviewStatuses") List<AuctionLotStatus> reviewStatuses,
		@Param("reviewInspections") List<AuctionInspectionStatus> reviewInspections,
		Pageable pageable);

	@Query(value = """
		select
		  count(*) as lotCount,
		  coalesce(sum(lot.shipped_quantity), 0) as shippedQuantity,
		  coalesce(sum(lot.sold_quantity), 0) as soldQuantity,
		  coalesce(sum(lot.waiting_quantity), 0) as waitingQuantity,
		  coalesce(sum(lot.returned_quantity), 0) as returnedQuantity,
		  coalesce(sum(case when lot.current_status in ('REVIEW_REQUIRED', 'QUANTITY_MISMATCH', 'RETURN_INFERRED', 'PARTIALLY_RETURNED')
		    or exists (select 1 from auction_result_lines line join auction_attempts attempt on attempt.id = line.auction_attempt_id
		      where attempt.shipment_lot_id = lot.id and line.inspection_status in ('MANUAL_REVIEW', 'MATCH_FAILED', 'QUANTITY_MISMATCH', 'RETURN_INFERRED', 'SOURCE_ERROR'))
		    then 1 else 0 end), 0) as reviewRequiredCount,
		  coalesce((select sum(line.amount) from auction_result_lines line), 0) as totalAmount
		from auction_shipment_lots lot
		""", nativeQuery = true)
	AuctionTrackingSummaryProjection summarize();
}
