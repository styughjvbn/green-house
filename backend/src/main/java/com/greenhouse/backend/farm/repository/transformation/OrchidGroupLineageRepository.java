package com.greenhouse.backend.farm.repository.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupLineageRepository extends JpaRepository<OrchidGroupLineage, Long> {

  @EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup"})
  List<OrchidGroupLineage> findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

  @EntityGraph(attributePaths = {"sourceOrchidGroup", "resultOrchidGroup"})
  List<OrchidGroupLineage> findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(Long orchidGroupId);

  long countByMutationIdIsNull();

  // Only the first relation type for each visible mutation/result pair labels a graph edge.
  @Query(
      """
      select new com.greenhouse.backend.farm.repository.transformation.GraphLineageTypeRow(
          lineage.mutationId, lineage.resultOrchidGroup.id, lineage.relationType)
      from OrchidGroupLineage lineage
      where lineage.mutationId in :mutationIds
        and exists (select entry.id from OrchidGroupMutationEntry entry
          where entry.id in :entryIds and entry.mutation.id = lineage.mutationId
            and entry.orchidGroupId = lineage.resultOrchidGroup.id)
        and lineage.id = (
          select min(candidate.id) from OrchidGroupLineage candidate
          where candidate.mutationId = lineage.mutationId
            and candidate.resultOrchidGroup.id = lineage.resultOrchidGroup.id)
      order by lineage.id
      """)
  List<GraphLineageTypeRow> findGraphLineageTypes(
      @Param("mutationIds") Collection<Long> mutationIds,
      @Param("entryIds") Collection<Long> entryIds);
}
