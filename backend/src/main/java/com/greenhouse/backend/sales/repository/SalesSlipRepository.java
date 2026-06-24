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

	@EntityGraph(attributePaths = {"customer", "items", "items.orchidGroup"})
	Optional<SalesSlip> findWithDetailsById(Long id);

	@Query("""
		select s from SalesSlip s
		join fetch s.customer c
		where (:customerId is null or c.id = :customerId)
			and (:from is null or s.saleDate >= :from)
			and (:to is null or s.saleDate <= :to)
		order by s.saleDate desc, s.id desc
		""")
	List<SalesSlip> search(
		@Param("customerId") Long customerId,
		@Param("from") LocalDate from,
		@Param("to") LocalDate to
	);
}
