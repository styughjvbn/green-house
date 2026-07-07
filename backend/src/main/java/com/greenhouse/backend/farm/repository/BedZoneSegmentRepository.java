package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.BedZoneSegment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BedZoneSegmentRepository extends JpaRepository<BedZoneSegment, Long> {
	@EntityGraph(attributePaths = { "capacities", "bedZone", "bedZone.physicalBed", "bedZone.physicalBed.house" })
	List<BedZoneSegment> findByBedZoneIdOrderBySortOrderAsc(Long bedZoneId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from BedZoneSegment s where s.id = :id")
	Optional<BedZoneSegment> findByIdForUpdate(@Param("id") Long id);
}
