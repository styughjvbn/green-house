package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupMutationEntryRepository extends JpaRepository<OrchidGroupMutationEntry, Long> {

	List<OrchidGroupMutationEntry> findByMutationIdOrderByIdAsc(Long mutationId);

	List<OrchidGroupMutationEntry> findByMutationIdInOrderByMutationIdAscIdAsc(
			Collection<Long> mutationIds);

	@EntityGraph(attributePaths = "mutation")
	List<OrchidGroupMutationEntry> findByOrchidGroupIdInOrderByOrchidGroupIdAscStateRevisionAfterAsc(
			Collection<Long> orchidGroupIds);
}
