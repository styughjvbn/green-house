package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupMutationRelationRepository
    extends JpaRepository<OrchidGroupMutationRelation, Long> {

  List<OrchidGroupMutationRelation> findByMutationIdOrderByIdAsc(Long mutationId);

  @Query(
      """
			select relation from OrchidGroupMutationRelation relation
			where relation.mutation.id in :mutationIds or relation.relatedMutation.id in :mutationIds
			order by relation.id
			""")
  @EntityGraph(attributePaths = {"mutation", "relatedMutation"})
  List<OrchidGroupMutationRelation> findConnectedToMutationIds(
      @Param("mutationIds") Collection<Long> mutationIds);

  @Query(
      "select relation from OrchidGroupMutationRelation relation "
          + "where relation.mutation.id in :mutationIds and relation.relatedMutation.id in :mutationIds "
          + "order by relation.id")
  @EntityGraph(attributePaths = {"mutation", "relatedMutation"})
  Slice<OrchidGroupMutationRelation> findVisibleGraphRelations(
      Collection<Long> mutationIds, Pageable pageable);

  boolean existsByRelatedMutationIdInAndRelationType(
      Collection<Long> mutationIds, OrchidGroupMutationRelationType relationType);
}
