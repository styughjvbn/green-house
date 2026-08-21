package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupHistoricalEvidence;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupHistoricalEvidenceRepository
		extends JpaRepository<OrchidGroupHistoricalEvidence, Long> {

	List<OrchidGroupHistoricalEvidence> findByMutationIdOrderByOrchidGroupIdAsc(Long mutationId);

	@EntityGraph(attributePaths = "mutation")
	List<OrchidGroupHistoricalEvidence> findByOrchidGroupIdInOrderByOrchidGroupIdAscMutationOccurredAtAscMutationIdAsc(
			Collection<Long> orchidGroupIds);

	long countByMigrationRunId(Long migrationRunId);

	@org.springframework.data.jpa.repository.Query("select count(distinct evidence.mutation.id) "
			+ "from OrchidGroupHistoricalEvidence evidence where evidence.migrationRunId = :migrationRunId")
	long countDistinctMutationsByMigrationRunId(
			@org.springframework.data.repository.query.Param("migrationRunId") Long migrationRunId);
}
