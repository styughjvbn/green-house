package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupMutationRepository extends JpaRepository<OrchidGroupMutation, Long> {

	Optional<OrchidGroupMutation> findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(
			OrchidGroupMutationSourceDomain sourceDomain,
			String sourceType,
			String sourceReferenceId,
			String sourceOperationKey);
}
