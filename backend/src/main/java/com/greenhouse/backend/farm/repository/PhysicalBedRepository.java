package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.PhysicalBed;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalBedRepository extends JpaRepository<PhysicalBed, Long> {

	List<PhysicalBed> findByHouseIdOrderByDisplayOrderAsc(Long houseId);

	@EntityGraph(attributePaths = {"house", "bedZones"})
	Optional<PhysicalBed> findWithHouseAndBedZonesById(Long id);
}
