package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.OrchidGroupCollectionMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupCollectionMemberRepository extends JpaRepository<OrchidGroupCollectionMember, Long> {
	List<OrchidGroupCollectionMember> findByCollectionIdAndRemovedAtIsNullOrderByJoinedAtAsc(Long collectionId);

	List<OrchidGroupCollectionMember> findByOrchidGroupIdAndRemovedAtIsNullOrderByJoinedAtAsc(Long orchidGroupId);

	List<OrchidGroupCollectionMember> findByCollectionIdAndOrchidGroupIdInAndRemovedAtIsNull(
			Long collectionId,
			Collection<Long> orchidGroupIds);

	Optional<OrchidGroupCollectionMember> findByCollectionIdAndOrchidGroupIdAndRemovedAtIsNull(
			Long collectionId,
			Long orchidGroupId);
}
