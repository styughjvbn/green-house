package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.BedZoneSegment;
import com.greenhouse.backend.farm.domain.BedZoneSegmentCapacity;
import com.greenhouse.backend.farm.dto.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileResponse;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupSegmentPlacementRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BedPlacementProfileService {

	private static final Set<String> FIXED_TYPES = Set.of("TRAY_15", "TRAY_20", "TRAY_24", "SINGLE_POT", "HANGING");
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupSegmentPlacementRepository placementRepository;

	public BedPlacementProfileService(BedZoneRepository bedZoneRepository, OrchidGroupSegmentPlacementRepository placementRepository) {
		this.bedZoneRepository = bedZoneRepository;
		this.placementRepository = placementRepository;
	}

	public BedZonePlacementProfileResponse getProfile(Long bedZoneId) {
		return BedZonePlacementProfileResponse.from(findZone(bedZoneId));
	}

	@Transactional
	public BedZonePlacementProfileResponse updateProfile(Long bedZoneId, BedZonePlacementProfileRequest request) {
		var bedZone = findZone(bedZoneId);
		validateSortOrders(request);
		Map<Long, BedZoneSegment> existing = new HashMap<>();
		bedZone.getSegments().forEach(segment -> existing.put(segment.getId(), segment));
		Set<Long> retainedIds = new HashSet<>();

		for (var segmentRequest : request.segments()) {
			validateCapacities(segmentRequest.capacities());
			BedZoneSegment segment;
			if (segmentRequest.id() == null) {
				segment = new BedZoneSegment(normalizeRequired(segmentRequest.name()), segmentRequest.segmentType(), segmentRequest.sortOrder(), normalize(segmentRequest.memo()));
				bedZone.addSegment(segment);
			} else {
				segment = existing.get(segmentRequest.id());
				if (segment == null) throw new IllegalArgumentException("해당 구역에 속하지 않은 구간입니다.");
				retainedIds.add(segment.getId());
				segment.update(normalizeRequired(segmentRequest.name()), segmentRequest.segmentType(), segmentRequest.sortOrder(), normalize(segmentRequest.memo()));
			}
			segment.replaceCapacities(segmentRequest.capacities().stream().map(this::toCapacity).toList());
		}

		bedZone.getSegments().removeIf(segment -> {
			if (segment.getId() == null || retainedIds.contains(segment.getId())) return false;
			if (placementRepository.existsBySegmentId(segment.getId())) throw new IllegalArgumentException("실제 배치가 있는 구간은 삭제할 수 없습니다.");
			return true;
		});
		bedZone.getSegments().sort((left, right) -> left.getSortOrder().compareTo(right.getSortOrder()));
		return BedZonePlacementProfileResponse.from(bedZone);
	}

	private BedZoneSegmentCapacity toCapacity(BedZoneCapacityRequest request) {
		return new BedZoneSegmentCapacity(
			normalizePlacementType(request.placementType()), normalize(request.potSize()), request.capacityMode(),
			request.capacityValue(), request.allowed(), normalize(request.memo())
		);
	}

	private void validateSortOrders(BedZonePlacementProfileRequest request) {
		Set<Integer> orders = new HashSet<>();
		if (request.segments().stream().anyMatch(segment -> !orders.add(segment.sortOrder()))) {
			throw new IllegalArgumentException("구간 표시 순서는 중복될 수 없습니다.");
		}
	}

	private void validateCapacities(java.util.List<BedZoneCapacityRequest> capacities) {
		Map<String, Integer> previousByKey = new HashMap<>();
		Set<String> modes = new HashSet<>();
		capacities.stream()
			.sorted((left, right) -> Integer.compare(left.capacityMode().ordinal(), right.capacityMode().ordinal()))
			.forEach(capacity -> {
				String type = normalizePlacementType(capacity.placementType());
				String key = type + "|" + Objects.toString(normalize(capacity.potSize()), "*");
				String modeKey = key + "|" + capacity.capacityMode();
				if (!modes.add(modeKey)) throw new IllegalArgumentException("같은 수용량 설정이 중복되었습니다.");
				Integer previous = previousByKey.get(key);
				if (previous != null && capacity.capacityValue() < previous) {
					throw new IllegalArgumentException("강한 배치 모드의 수용량은 이전 모드보다 작을 수 없습니다.");
				}
				previousByKey.put(key, capacity.capacityValue());
			});
	}

	private String normalizePlacementType(String value) {
		String normalized = normalizeRequired(value).toUpperCase();
		if (!FIXED_TYPES.contains(normalized) && !normalized.startsWith("CUSTOM:")) {
			throw new IllegalArgumentException("지원하지 않는 배치 규격입니다.");
		}
		return normalized;
	}

	private com.greenhouse.backend.farm.domain.BedZone findZone(Long id) {
		return bedZoneRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private String normalize(String value) { if (value == null) return null; String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed; }
	private String normalizeRequired(String value) { String result = normalize(value); if (result == null) throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다."); return result; }
}
