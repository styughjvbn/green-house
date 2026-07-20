package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.PhysicalBed;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhysicalBedRepository extends JpaRepository<PhysicalBed, Long> {

	List<PhysicalBed> findByHouseIdOrderByDisplayOrderAsc(Long houseId);

	@Query("""
			select b from PhysicalBed b
			join fetch b.house h
			order by h.number asc, b.displayOrder asc
			""")
	List<PhysicalBed> findAllInFarmOrder();

	@EntityGraph(attributePaths = { "house", "bedZones" })
	Optional<PhysicalBed> findWithHouseAndBedZonesById(Long id);
}
