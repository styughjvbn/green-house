package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.OrchidGroupLineage;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupLineageRepository extends JpaRepository<OrchidGroupLineage, Long> {

	@EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup", "workOperation"})
	List<OrchidGroupLineage> findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

	@EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup", "workOperation"})
	List<OrchidGroupLineage> findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);
}
