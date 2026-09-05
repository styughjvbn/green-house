package com.greenhouse.backend.farm.repository.collection;

import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrchidGroupCollectionMemberRepository extends JpaRepository<OrchidGroupCollectionMember, Long> {
	@Query("select distinct member.orchidGroupId from OrchidGroupCollectionMember member order by member.orchidGroupId")
	List<Long> findDistinctOrchidGroupIds();

	List<OrchidGroupCollectionMember> findByCollectionIdAndRemovedAtIsNullOrderByJoinedAtAsc(Long collectionId);

	List<OrchidGroupCollectionMember> findByCollectionIdInAndRemovedAtIsNullOrderByJoinedAtAsc(Collection<Long> collectionIds);

	List<OrchidGroupCollectionMember> findByOrchidGroupIdAndRemovedAtIsNullOrderByJoinedAtAsc(Long orchidGroupId);

	List<OrchidGroupCollectionMember> findByOrchidGroupIdInAndRemovedAtIsNull(Collection<Long> orchidGroupIds);

	List<OrchidGroupCollectionMember> findByCollectionIdAndOrchidGroupIdInAndRemovedAtIsNull(
			Long collectionId,
			Collection<Long> orchidGroupIds);

	Optional<OrchidGroupCollectionMember> findByCollectionIdAndOrchidGroupIdAndRemovedAtIsNull(
			Long collectionId,
			Long orchidGroupId);
}
