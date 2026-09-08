package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesSlip;
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

public interface SalesSlipRepository extends JpaRepository<SalesSlip, Long>, SalesSlipRepositoryCustom {

	boolean existsByAuctionShipmentId(Long auctionShipmentId);

	@Query("""
			select slip.auctionShipmentId from SalesSlip slip
			where slip.auctionShipmentId in :shipmentIds
			""")
	List<Long> findUsedAuctionShipmentIds(@Param("shipmentIds") Collection<Long> shipmentIds);

	@EntityGraph(attributePaths = { "items" })
	Optional<SalesSlip> findWithDetailsById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select slip from SalesSlip slip where slip.id = :id")
	Optional<SalesSlip> findForUpdateById(@Param("id") Long id);

	@Query("""
			select coalesce(sum(coalesce(s.remainingAmount, s.totalAmount)), 0)
			from SalesSlip s
			where s.partnerId = :partnerId
			  and (s.salesType is null or s.salesType = com.greenhouse.backend.sales.domain.SalesType.DIRECT)
			  and s.salesStatus <> '취소'
			""")
	Long sumDirectReceivableByPartnerId(@Param("partnerId") Long partnerId);

	@Query("select count(item) from SalesSlipItem item")
	long countItems();

	@Query("select count(snapshot) from SalesOrchidGroupSnapshot snapshot")
	long countOrchidGroupSnapshots();

}
