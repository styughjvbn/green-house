package com.greenhouse.backend.farm.repository.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupLineageRepository extends JpaRepository<OrchidGroupLineage, Long> {

	@EntityGraph(attributePaths = { "sourceOrchidGroup", "resultOrchidGroup" })
	List<OrchidGroupLineage> findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

	@EntityGraph(attributePaths = { "sourceOrchidGroup", "resultOrchidGroup" })
	List<OrchidGroupLineage> findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

	long countByMutationIdIsNull();

	@EntityGraph(attributePaths = { "sourceOrchidGroup", "resultOrchidGroup" })
	@Query("select lineage from OrchidGroupLineage lineage where lineage.mutationId in :mutationIds order by lineage.id")
	List<OrchidGroupLineage> findByMutationIdInOrderByIdAsc(@Param("mutationIds") Collection<Long> mutationIds);

}
