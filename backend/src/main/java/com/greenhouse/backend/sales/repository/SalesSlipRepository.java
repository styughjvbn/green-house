package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	@EntityGraph(attributePaths = { "partner", "auctionShipment", "auctionShipment.auctionHouse" })
	@Query("""
			select s from SalesSlip s
			join s.partner p
			where (:partnerId is null or p.id = :partnerId)
				and (:from is null or s.saleDate >= :from)
				and (:to is null or s.saleDate <= :to)
				and (:paymentStatus is null or s.paymentStatus = :paymentStatus)
				and (:salesStatus is null or s.salesStatus = :salesStatus)
			""")
	Page<SalesSlip> searchPage(
			@Param("partnerId") Long partnerId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("paymentStatus") String paymentStatus,
			@Param("salesStatus") String salesStatus,
			Pageable pageable);

	@EntityGraph(attributePaths = { "partner", "auctionShipment", "auctionShipment.auctionHouse" })
	@Query("""
			select s from SalesSlip s
			join s.partner p
			where (:partnerId is null or p.id = :partnerId)
				and (:from is null or s.saleDate >= :from)
				and (:to is null or s.saleDate <= :to)
				and (:paymentStatus is null or s.paymentStatus = :paymentStatus)
				and (:salesStatus is null or s.salesStatus = :salesStatus)
				and (
					lower(s.slipNumber) like :keywordLike
					or lower(p.name) like :keywordLike
					or lower(coalesce(p.ownerName, '')) like :keywordLike
					or lower(coalesce(p.phone, '')) like :keywordLike
					or lower(coalesce(s.memo, '')) like :keywordLike
				)
			""")
	Page<SalesSlip> searchPageWithKeyword(
			@Param("partnerId") Long partnerId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("paymentStatus") String paymentStatus,
			@Param("salesStatus") String salesStatus,
			@Param("keywordLike") String keywordLike,
			Pageable pageable);

	@Query("""
			select coalesce(sum(coalesce(s.remainingAmount, s.totalAmount)), 0)
			from SalesSlip s
			where s.partner.id = :partnerId
			  and (s.salesType is null or s.salesType = com.greenhouse.backend.sales.domain.SalesType.DIRECT)
			  and s.salesStatus <> '취소'
			""")
	Long sumDirectReceivableByPartnerId(@Param("partnerId") Long partnerId);
}
