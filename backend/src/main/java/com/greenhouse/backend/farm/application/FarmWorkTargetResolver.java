package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupCollectionStatus;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.ResolvedWorkTarget;
import com.greenhouse.backend.work.application.WorkTargetSelection;
import com.greenhouse.backend.work.application.WorkTargetResolver;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmWorkTargetResolver implements WorkTargetResolver {

	private final HouseRepository houseRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupCollectionRepository collectionRepository;
	private final OrchidGroupCollectionMemberRepository collectionMemberRepository;
	private final DerivedOrchidGroupService derivedOrchidGroupService;

	@Override
	public List<ResolvedWorkTarget> resolve(WorkTargetSelection selection) {
		return switch (selection.scopeType()) {
			case HOUSE -> resolveHouse(selection.scopeId());
			case DERIVED_GROUP -> resolveActiveIds(derivedOrchidGroupService
					.getMembers(selection.scopeKey(), null, null, null).stream()
					.map(member -> member.id())
					.collect(Collectors.toSet()));
			case USER_COLLECTION -> resolveCollection(selection.scopeId());
			case MANUAL_SELECTION -> resolveManual(selection.orchidGroupIds());
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		};
	}

	@Override
	public ResolvedWorkTarget getCurrent(Long orchidGroupId) {
		return orchidGroupRepository.findDetailById(orchidGroupId)
				.map(this::toResolvedTarget)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
	}

	private ResolvedWorkTarget toResolvedTarget(OrchidGroup group) {
		return new ResolvedWorkTarget(
				group.getId(),
				group.getVariety() == null ? null : group.getVariety().getId(),
				group.getVarietyName(),
				group.getQuantity(),
				currentAgeYear(group),
				group.getPotSize(),
				group.getPotSizeCode().name(),
				location(group));
	}

	private List<ResolvedWorkTarget> resolveHouse(Long houseId) {
		if (houseId == null || !houseRepository.existsById(houseId)) {
			throw new NotFoundException("동을 찾을 수 없습니다.");
		}
		return orchidGroupRepository.findActiveWorkTargetsByHouseId(houseId).stream()
				.map(this::toResolvedTarget)
				.toList();
	}

	private List<ResolvedWorkTarget> resolveCollection(Long collectionId) {
		var collection = collectionId == null ? null : collectionRepository.findById(collectionId).orElse(null);
		if (collection == null) {
			throw new NotFoundException("사용자 그룹을 찾을 수 없습니다.");
		}
		if (collection.getStatus() != OrchidGroupCollectionStatus.ACTIVE) {
			throw new IllegalArgumentException("보관된 사용자 그룹으로 새 작업을 만들 수 없습니다.");
		}
		Set<Long> ids = collectionMemberRepository
				.findByCollectionIdAndRemovedAtIsNullOrderByJoinedAtAsc(collectionId).stream()
				.map(member -> member.getOrchidGroupId())
				.collect(Collectors.toSet());
		return resolveActiveIds(ids);
	}

	private List<ResolvedWorkTarget> resolveManual(List<Long> orchidGroupIds) {
		Set<Long> ids = Set.copyOf(orchidGroupIds);
		List<ResolvedWorkTarget> resolved = resolveActiveIds(ids);
		Set<Long> resolvedIds = resolved.stream().map(ResolvedWorkTarget::orchidGroupId).collect(Collectors.toSet());
		if (!resolvedIds.containsAll(ids)) {
			throw new IllegalArgumentException("직접 선택 대상에는 현재 작업 가능한 난 묶음만 포함할 수 있습니다.");
		}
		return resolved;
	}

	private List<ResolvedWorkTarget> resolveActiveIds(Set<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return orchidGroupRepository.findActiveWorkTargetsByIds(ids).stream()
				.map(this::toResolvedTarget)
				.toList();
	}

	private Integer currentAgeYear(OrchidGroup group) {
		if (group.getAgeYear() == null) {
			return null;
		}
		LocalDate referenceDate = group.getInboundRecord() != null
				? group.getInboundRecord().getInboundDate()
				: group.getCreatedAt() == null ? null : group.getCreatedAt().toLocalDate();
		if (referenceDate == null) {
			return group.getAgeYear();
		}
		long elapsedYears = ChronoUnit.YEARS.between(referenceDate, LocalDate.now());
		return group.getAgeYear() + Math.max(0, Math.toIntExact(elapsedYears));
	}

	private Map<String, Object> location(OrchidGroup group) {
		var zone = group.getBedZone();
		var bed = zone.getPhysicalBed();
		var house = bed.getHouse();
		Map<String, Object> location = new LinkedHashMap<>();
		location.put("houseId", house.getId());
		location.put("houseNumber", house.getNumber());
		location.put("physicalBedId", bed.getId());
		location.put("physicalBedNumber", bed.getNumber());
		location.put("bedZoneId", zone.getId());
		location.put("bedZoneName", zone.getName());
		return location;
	}
}
