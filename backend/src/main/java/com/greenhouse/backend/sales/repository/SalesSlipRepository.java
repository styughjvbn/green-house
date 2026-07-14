package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesSlipRepository extends JpaRepository<SalesSlip, Long>, SalesSlipRepositoryCustom {

	long countBySaleDate(LocalDate saleDate);

	boolean existsByAuctionShipmentId(Long auctionShipmentId);

	@EntityGraph(attributePaths = { "partner", "auctionShipment", "auctionShipment.auctionHouse", "items",
			"items.auctionShipmentLot" })
	Optional<SalesSlip> findWithDetailsById(Long id);

	@Query("""
			select coalesce(sum(coalesce(s.remainingAmount, s.totalAmount)), 0)
			from SalesSlip s
			where s.partner.id = :partnerId
			  and (s.salesType is null or s.salesType = com.greenhouse.backend.sales.domain.SalesType.DIRECT)
			  and s.salesStatus <> '취소'
			""")
	Long sumDirectReceivableByPartnerId(@Param("partnerId") Long partnerId);
}
