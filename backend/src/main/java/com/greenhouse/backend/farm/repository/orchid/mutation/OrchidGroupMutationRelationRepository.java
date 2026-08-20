package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupMutationRelationRepository
		extends JpaRepository<OrchidGroupMutationRelation, Long> {

	List<OrchidGroupMutationRelation> findByMutationIdOrderByIdAsc(Long mutationId);
}
