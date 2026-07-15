package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkEffectOrchidGroup;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkEffectOrchidGroupRepository extends JpaRepository<WorkEffectOrchidGroup, Long> {

	@EntityGraph(attributePaths = {"workAppliedEffect", "orchidGroup", "orchidGroup.bedZone",
			"orchidGroup.bedZone.physicalBed", "orchidGroup.bedZone.physicalBed.house", "orchidGroup.variety"})
	List<WorkEffectOrchidGroup> findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(Long workOperationId);

	boolean existsByOrchidGroupId(Long orchidGroupId);
}
