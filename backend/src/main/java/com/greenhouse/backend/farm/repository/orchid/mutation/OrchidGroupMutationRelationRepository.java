package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupMutationRelationRepository extends JpaRepository<OrchidGroupMutationRelation, Long> {

	List<OrchidGroupMutationRelation> findByMutationIdOrderByIdAsc(Long mutationId);

	@Query("""
			select relation from OrchidGroupMutationRelation relation
			where relation.mutation.id in :mutationIds or relation.relatedMutation.id in :mutationIds
			order by relation.id
			""")
	List<OrchidGroupMutationRelation> findConnectedToMutationIds(@Param("mutationIds") Collection<Long> mutationIds);

}
