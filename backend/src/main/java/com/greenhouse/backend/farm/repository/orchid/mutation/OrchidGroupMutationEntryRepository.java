package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupMutationEntryRepository extends JpaRepository<OrchidGroupMutationEntry, Long> {

	interface InboundPottingDateRow {

		Long getInboundRecordId();

		LocalDate getPottingDate();

	}

	List<OrchidGroupMutationEntry> findByMutationIdOrderByIdAsc(Long mutationId);

	@Query("""
			select orchidGroup.inboundRecord.id as inboundRecordId,
			       max(entry.mutation.effectiveBusinessDate) as pottingDate
			from OrchidGroupMutationEntry entry
			join OrchidGroup orchidGroup on orchidGroup.id = entry.orchidGroupId
			where orchidGroup.inboundRecord.id in :inboundRecordIds
			  and entry.entryKind = :entryKind
			group by orchidGroup.inboundRecord.id
			""")
	List<InboundPottingDateRow> findInboundPottingDates(
			@Param("inboundRecordIds") Collection<Long> inboundRecordIds,
			@Param("entryKind") OrchidGroupMutationEntryKind entryKind);

	List<OrchidGroupMutationEntry> findByMutationIdInOrderByMutationIdAscIdAsc(Collection<Long> mutationIds);

	@EntityGraph(attributePaths = "mutation")
	@Query("select entry from OrchidGroupMutationEntry entry " + "where entry.orchidGroupId in :orchidGroupIds "
			+ "order by entry.orchidGroupId, entry.stateRevisionAfter")
	List<OrchidGroupMutationEntry> findStateChainByOrchidGroupIdIn(
			@Param("orchidGroupIds") Collection<Long> orchidGroupIds);

	List<OrchidGroupMutationEntry> findByMutationIdOrderByOrchidGroupIdAsc(Long mutationId);

	@EntityGraph(attributePaths = "mutation")
	@Query("select entry from OrchidGroupMutationEntry entry " + "where entry.orchidGroupId in :orchidGroupIds "
			+ "order by entry.mutation.occurredAt desc, entry.mutation.id desc, entry.id asc")
	Slice<OrchidGroupMutationEntry> findGraphEntriesByOrchidGroupIdIn(
			@Param("orchidGroupIds") Collection<Long> orchidGroupIds, Pageable pageable);

	@EntityGraph(attributePaths = "mutation")
	@Query("select entry from OrchidGroupMutationEntry entry where entry.mutation.id in :mutationIds "
			+ "order by entry.mutation.id asc, entry.id asc")
	Slice<OrchidGroupMutationEntry> findGraphEntriesByMutationIdIn(@Param("mutationIds") Collection<Long> mutationIds,
			Pageable pageable);

	@EntityGraph(attributePaths = "mutation")
	@Query("select entry from OrchidGroupMutationEntry entry "
			+ "where not exists (select group.id from OrchidGroup group where group.id = entry.orchidGroupId) "
			+ "order by entry.orchidGroupId, entry.stateRevisionAfter")
	List<OrchidGroupMutationEntry> findChainsWithoutCurrentGroup();

}
