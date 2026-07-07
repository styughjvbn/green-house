package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.Variety;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VarietyRepository extends JpaRepository<Variety, Long> {

	@Query("""
		select v from Variety v
		where (:keyword = '' or lower(v.code) like lower(concat('%', :keyword, '%'))
			or lower(v.name) like lower(concat('%', :keyword, '%'))
			or lower(coalesce(v.alias, '')) like lower(concat('%', :keyword, '%')))
			and (:genus = '' or v.genus = :genus)
			and (:saleEnabled is null or v.saleEnabled = :saleEnabled)
			and (:active is null or v.active = :active)
		order by v.active desc, v.genus asc, v.name asc
		""")
	Page<Variety> search(
		@Param("keyword") String keyword,
		@Param("genus") String genus,
		@Param("saleEnabled") Boolean saleEnabled,
		@Param("active") Boolean active,
		Pageable pageable
	);

	Optional<Variety> findTopByOrderByIdDesc();

	Optional<Variety> findByGenusAndName(String genus, String name);
}
