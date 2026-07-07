package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.OrchidGroupSegmentPlacement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupSegmentPlacementRepository extends JpaRepository<OrchidGroupSegmentPlacement, Long> {
	List<OrchidGroupSegmentPlacement> findBySegmentId(Long segmentId);

	List<OrchidGroupSegmentPlacement> findBySegmentBedZoneId(Long bedZoneId);

	boolean existsBySegmentId(Long segmentId);
}
