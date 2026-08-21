package com.greenhouse.backend.farm.repository.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupLineageRepository extends JpaRepository<OrchidGroupLineage, Long> {

	@EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup"})
	List<OrchidGroupLineage> findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

	@EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup"})
	List<OrchidGroupLineage> findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

	@EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup"})
	@Query("""
			select lineage from OrchidGroupLineage lineage
			where lineage.id > :afterId
			  and lineage.createdAt <= :sourceCutoff
			order by lineage.id
			""")
	List<OrchidGroupLineage> findHistoricalAfter(
			@Param("afterId") long afterId,
			@Param("sourceCutoff") LocalDateTime sourceCutoff,
			Pageable pageable);

	long countByMutationIdIsNull();

	@Query("""
			select count(lineage) from OrchidGroupLineage lineage
			where lineage.createdAt <= :sourceCutoff
			  and lineage.mutationId is null
			""")
	long countUnlinkedHistorical(@Param("sourceCutoff") LocalDateTime sourceCutoff);
}
