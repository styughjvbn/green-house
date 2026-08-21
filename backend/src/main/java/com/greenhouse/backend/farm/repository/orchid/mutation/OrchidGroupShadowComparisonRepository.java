package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupShadowComparison;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupShadowComparisonRepository
		extends JpaRepository<OrchidGroupShadowComparison, Long> {

	Optional<OrchidGroupShadowComparison> findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(
			OrchidGroupMutationSourceDomain sourceDomain,
			String sourceType,
			String sourceReferenceId,
			String sourceOperationKey);
}
