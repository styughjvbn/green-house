package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesSlipRepository extends JpaRepository<SalesSlip, Long> {

	long countBySaleDate(LocalDate saleDate);

	boolean existsByAuctionShipmentId(Long auctionShipmentId);

	@EntityGraph(attributePaths = { "partner", "auctionShipment", "auctionShipment.auctionHouse", "items",
			"items.auctionShipmentLot" })
	Optional<SalesSlip> findWithDetailsById(Long id);

	@EntityGraph(attributePaths = { "partner", "auctionShipment", "auctionShipment.auctionHouse", "items",
			"items.auctionShipmentLot" })
	@Query("""
			select s from SalesSlip s
			join fetch s.partner p
			where (:partnerId is null or p.id = :partnerId)
				and (:from is null or s.saleDate >= :from)
				and (:to is null or s.saleDate <= :to)
			order by s.saleDate desc, s.id desc
			""")
	List<SalesSlip> search(
			@Param("partnerId") Long partnerId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query("""
			select coalesce(sum(coalesce(s.remainingAmount, s.totalAmount)), 0)
			from SalesSlip s
			where s.partner.id = :partnerId
			  and (s.salesType is null or s.salesType = com.greenhouse.backend.sales.domain.SalesType.DIRECT)
			  and s.salesStatus <> '취소'
			""")
	Long sumDirectReceivableByPartnerId(@Param("partnerId") Long partnerId);
}
