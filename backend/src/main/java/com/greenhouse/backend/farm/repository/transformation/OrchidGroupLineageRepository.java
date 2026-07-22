package com.greenhouse.backend.farm.repository.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupLineageRepository extends JpaRepository<OrchidGroupLineage, Long> {

	@EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup"})
	List<OrchidGroupLineage> findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

	@EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup"})
	List<OrchidGroupLineage> findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);
}
