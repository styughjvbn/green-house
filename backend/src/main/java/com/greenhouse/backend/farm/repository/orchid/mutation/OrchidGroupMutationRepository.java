package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrchidGroupMutationRepository extends JpaRepository<OrchidGroupMutation, Long> {

	Optional<OrchidGroupMutation> findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(
			OrchidGroupMutationSourceDomain sourceDomain, String sourceType, String sourceReferenceId,
			String sourceOperationKey);

	@Query("select count(m) from OrchidGroupMutation m where not exists "
			+ "(select e.id from OrchidGroupMutationEntry e where e.mutation = m)")
	long countWithoutEntries();

}
