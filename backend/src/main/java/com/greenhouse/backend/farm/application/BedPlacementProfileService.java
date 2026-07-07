package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneCapacity;
import com.greenhouse.backend.farm.dto.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileResponse;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BedPlacementProfileService {

	private static final Set<String> FIXED_TYPES = Set.of("TRAY_15", "TRAY_20", "TRAY_24", "SINGLE_POT", "HANGING");

	private final BedZoneRepository bedZoneRepository;

	public BedZonePlacementProfileResponse getProfile(Long bedZoneId) {
		return BedZonePlacementProfileResponse.from(findZone(bedZoneId));
	}

	@Transactional
	public BedZonePlacementProfileResponse updateProfile(Long bedZoneId, BedZonePlacementProfileRequest request) {
		BedZone bedZone = findZone(bedZoneId);
		validateCapacities(request.capacities());
		bedZone.replaceCapacities(request.capacities().stream().map(this::toCapacity).toList());
		return BedZonePlacementProfileResponse.from(bedZone);
	}

	private BedZoneCapacity toCapacity(BedZoneCapacityRequest request) {
		return new BedZoneCapacity(
				normalizePlacementType(request.placementType()),
				normalize(request.potSize()),
				request.capacityMode(),
				normalizeNumber(request.unitSpan()),
				request.capacityValue(),
				request.allowed(),
				normalize(request.memo()));
	}

	private void validateCapacities(java.util.List<BedZoneCapacityRequest> capacities) {
		Map<String, Integer> previousByKey = new HashMap<>();
		Set<String> rules = new HashSet<>();
		capacities.stream()
				.sorted((left, right) -> Integer.compare(left.capacityMode().ordinal(), right.capacityMode().ordinal()))
				.forEach(capacity -> {
					String type = normalizePlacementType(capacity.placementType());
					String potSize = normalize(capacity.potSize());
					String key = type + "|" + Objects.toString(potSize, "*");
					String ruleKey = key + "|" + capacity.capacityMode();
					if (!rules.add(ruleKey)) {
						throw new IllegalArgumentException("같은 수용 규칙이 중복되었습니다.");
					}
					if (capacity.unitSpan().signum() <= 0) {
						throw new IllegalArgumentException("점유 폭은 0보다 커야 합니다.");
					}
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

	private BedZone findZone(Long id) {
		return bedZoneRepository.findWithDetailsById(id)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeRequired(String value) {
		String result = normalize(value);
		if (result == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return result;
	}

	private BigDecimal normalizeNumber(BigDecimal value) {
		if (value == null) {
			return null;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}
}
