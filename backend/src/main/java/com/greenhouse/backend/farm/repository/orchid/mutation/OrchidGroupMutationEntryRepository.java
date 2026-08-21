package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupMutationEntryRepository extends JpaRepository<OrchidGroupMutationEntry, Long> {

	List<OrchidGroupMutationEntry> findByMutationIdOrderByIdAsc(Long mutationId);

	List<OrchidGroupMutationEntry> findByMutationIdInOrderByMutationIdAscIdAsc(
			Collection<Long> mutationIds);

	@EntityGraph(attributePaths = "mutation")
	@Query("select entry from OrchidGroupMutationEntry entry "
			+ "where entry.orchidGroupId in :orchidGroupIds "
			+ "and entry.entryKind <> com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind.HISTORICAL "
			+ "order by entry.orchidGroupId, entry.stateRevisionAfter")
	List<OrchidGroupMutationEntry> findStateChainByOrchidGroupIdIn(
			@Param("orchidGroupIds") Collection<Long> orchidGroupIds);

	List<OrchidGroupMutationEntry> findByMutationIdOrderByOrchidGroupIdAsc(Long mutationId);

	long countByMigrationRunId(Long migrationRunId);

	@Query("select count(distinct entry.mutation.id) from OrchidGroupMutationEntry entry "
			+ "where entry.migrationRunId = :migrationRunId")
	long countDistinctMutationsByMigrationRunId(@Param("migrationRunId") Long migrationRunId);
}
