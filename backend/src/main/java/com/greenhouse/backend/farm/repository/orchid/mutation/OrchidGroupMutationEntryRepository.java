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

	List<OrchidGroupMutationEntry> findByMutationIdInOrderByMutationIdAscIdAsc(Collection<Long> mutationIds);

	@EntityGraph(attributePaths = "mutation")
	@Query("select entry from OrchidGroupMutationEntry entry " + "where entry.orchidGroupId in :orchidGroupIds "
			+ "order by entry.orchidGroupId, entry.stateRevisionAfter")
	List<OrchidGroupMutationEntry> findStateChainByOrchidGroupIdIn(
			@Param("orchidGroupIds") Collection<Long> orchidGroupIds);

	List<OrchidGroupMutationEntry> findByMutationIdOrderByOrchidGroupIdAsc(Long mutationId);

	@EntityGraph(attributePaths = "mutation")
	@Query("select entry from OrchidGroupMutationEntry entry "
			+ "where not exists (select group.id from OrchidGroup group where group.id = entry.orchidGroupId) "
			+ "order by entry.orchidGroupId, entry.stateRevisionAfter")
	List<OrchidGroupMutationEntry> findChainsWithoutCurrentGroup();

}
