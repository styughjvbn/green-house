package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.config.TimeConfig;
import java.time.Clock;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrchidGroupCollectionInheritanceService {
	private final Clock clock;
	private final OrchidGroupCollectionRepository collectionRepository;
	private final OrchidGroupCollectionMemberRepository memberRepository;

	public Set<Long> validate(Long sourceId, Set<Long> requestedIds) {
		Set<Long> ids = requestedIds == null ? Set.of() : Set.copyOf(requestedIds);
		if (ids.isEmpty()) return ids;
		Set<Long> sourceCollectionIds = memberRepository
				.findByOrchidGroupIdAndRemovedAtIsNullOrderByJoinedAtAsc(sourceId).stream()
				.map(OrchidGroupCollectionMember::getCollectionId)
				.collect(Collectors.toSet());
		if (!sourceCollectionIds.containsAll(ids)) {
			throw new IllegalArgumentException("원본 난 묶음에 속한 사용자 그룹만 상속할 수 있습니다.");
		}
		List<OrchidGroupCollection> collections = collectionRepository.findAllById(ids);
		if (collections.size() != ids.size() || collections.stream().anyMatch(OrchidGroupCollection::isArchived)) {
			throw new IllegalArgumentException("활성 사용자 그룹만 상속할 수 있습니다.");
		}
		return ids;
	}

	public void inherit(Set<Long> collectionIds, Collection<Long> resultOrchidGroupIds, String worker) {
		var joinedAt = TimeConfig.utcNow(clock);
		if (collectionIds.isEmpty() || resultOrchidGroupIds.isEmpty()) return;
		memberRepository.saveAll(resultOrchidGroupIds.stream()
				.flatMap(orchidGroupId -> collectionIds.stream()
						.map(collectionId -> new OrchidGroupCollectionMember(collectionId, orchidGroupId, worker, joinedAt)))
				.toList());
	}
}
