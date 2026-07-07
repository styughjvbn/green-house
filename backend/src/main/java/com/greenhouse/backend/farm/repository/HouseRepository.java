package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.House;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseRepository extends JpaRepository<House, Long> {

	boolean existsByNumber(Integer number);

	@EntityGraph(attributePaths = { "physicalBeds" })
	Optional<House> findWithPhysicalBedsById(Long id);
}
