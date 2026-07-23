package com.greenhouse.backend.farm.application.collection;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionStatus;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionCreateRequest;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionMemberAddRequest;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionMemberResponse;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionResponse;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionUpdateRequest;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupCollectionService {

	private final OrchidGroupCollectionRepository collectionRepository;
	private final OrchidGroupCollectionMemberRepository memberRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	@Transactional(readOnly = true)
	public List<OrchidGroupCollectionResponse> getCollections(boolean includeArchived) {
		List<OrchidGroupCollection> collections = includeArchived
				? collectionRepository.findAllByOrderByUpdatedAtDesc()
				: collectionRepository.findByStatusOrderByUpdatedAtDesc(OrchidGroupCollectionStatus.ACTIVE);
		return collections.stream().map(this::toResponse).toList();
	}

	public OrchidGroupCollectionResponse create(OrchidGroupCollectionCreateRequest request) {
		OrchidGroupCollection collection = collectionRepository.save(new OrchidGroupCollection(
				normalizeRequired(request.name()), normalize(request.description()), normalize(request.purpose()),
				normalize(request.createdBy())));
		return toResponse(collection);
	}

	@Transactional(readOnly = true)
	public OrchidGroupCollectionResponse get(Long collectionId) {
		return toResponse(findCollection(collectionId));
	}

	public OrchidGroupCollectionResponse update(Long collectionId, OrchidGroupCollectionUpdateRequest request) {
		OrchidGroupCollection collection = findCollection(collectionId);
		collection.update(
				normalizeRequired(request.name()), normalize(request.description()), normalize(request.purpose()));
		return toResponse(collection);
	}

	public OrchidGroupCollectionResponse archive(Long collectionId) {
		OrchidGroupCollection collection = findCollection(collectionId);
		collection.archive();
		return toResponse(collection);
	}

	public OrchidGroupCollectionResponse addMembers(
			Long collectionId,
			OrchidGroupCollectionMemberAddRequest request) {
		OrchidGroupCollection collection = findEditableCollection(collectionId);
		Set<Long> requestedIds = request.orchidGroupIds();
		List<OrchidGroup> groups = orchidGroupRepository.findDetailsByIds(requestedIds);
		Set<Long> foundIds = groups.stream().map(OrchidGroup::getId).collect(Collectors.toSet());
		if (!foundIds.containsAll(requestedIds)) {
			throw new NotFoundException("추가할 난 묶음 중 찾을 수 없는 대상이 있습니다.");
		}

		Set<Long> existingIds = memberRepository
				.findByCollectionIdAndOrchidGroupIdInAndRemovedAtIsNull(collectionId, requestedIds)
				.stream().map(OrchidGroupCollectionMember::getOrchidGroupId).collect(Collectors.toSet());
		List<OrchidGroupCollectionMember> additions = requestedIds.stream()
				.filter(id -> !existingIds.contains(id))
				.map(id -> new OrchidGroupCollectionMember(collectionId, id, normalize(request.createdBy())))
				.toList();
		memberRepository.saveAll(additions);
		return toResponse(collection);
	}

	public OrchidGroupCollectionResponse removeMember(Long collectionId, Long orchidGroupId) {
		OrchidGroupCollection collection = findEditableCollection(collectionId);
		OrchidGroupCollectionMember member = memberRepository
				.findByCollectionIdAndOrchidGroupIdAndRemovedAtIsNull(collectionId, orchidGroupId)
				.orElseThrow(() -> new NotFoundException("사용자 그룹 소속을 찾을 수 없습니다."));
		member.remove();
		return toResponse(collection);
	}

	@Transactional(readOnly = true)
	public List<OrchidGroupCollectionResponse> getCollectionsForOrchidGroup(Long orchidGroupId) {
		if (!orchidGroupRepository.existsById(orchidGroupId)) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		Set<Long> collectionIds = memberRepository
				.findByOrchidGroupIdAndRemovedAtIsNullOrderByJoinedAtAsc(orchidGroupId)
				.stream().map(OrchidGroupCollectionMember::getCollectionId).collect(Collectors.toSet());
		return collectionRepository.findAllById(collectionIds).stream().map(this::toResponse).toList();
	}

	private OrchidGroupCollectionResponse toResponse(OrchidGroupCollection collection) {
		List<OrchidGroupCollectionMember> members = memberRepository
				.findByCollectionIdAndRemovedAtIsNullOrderByJoinedAtAsc(collection.getId());
		if (members.isEmpty()) {
			return OrchidGroupCollectionResponse.from(collection, List.of());
		}
		Map<Long, OrchidGroup> groupsById = orchidGroupRepository
				.findDetailsByIds(members.stream().map(OrchidGroupCollectionMember::getOrchidGroupId).toList())
				.stream().collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		List<OrchidGroupCollectionMemberResponse> memberResponses = members.stream()
				.filter(member -> groupsById.containsKey(member.getOrchidGroupId()))
				.map(member -> OrchidGroupCollectionMemberResponse.from(member, groupsById.get(member.getOrchidGroupId())))
				.toList();
		return OrchidGroupCollectionResponse.from(collection, memberResponses);
	}

	private OrchidGroupCollection findCollection(Long collectionId) {
		return collectionRepository.findById(collectionId)
				.orElseThrow(() -> new NotFoundException("사용자 그룹을 찾을 수 없습니다."));
	}

	private OrchidGroupCollection findEditableCollection(Long collectionId) {
		OrchidGroupCollection collection = findCollection(collectionId);
		if (collection.isArchived()) {
			throw new IllegalArgumentException("보관된 사용자 그룹의 소속은 변경할 수 없습니다.");
		}
		return collection;
	}

	private String normalize(String value) {
		if (value == null) return null;
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) throw new IllegalArgumentException("사용자 그룹 이름은 필수입니다.");
		return normalized;
	}
}
