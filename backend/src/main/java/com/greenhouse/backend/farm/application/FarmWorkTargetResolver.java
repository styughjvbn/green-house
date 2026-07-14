package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.ResolvedWorkTarget;
import com.greenhouse.backend.work.application.WorkTargetResolver;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmWorkTargetResolver implements WorkTargetResolver {

	private final HouseRepository houseRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	@Override
	public List<ResolvedWorkTarget> resolve(WorkSourceScopeType scopeType, Long scopeId) {
		if (scopeType != WorkSourceScopeType.HOUSE) {
			throw new IllegalArgumentException("초기 작업 실행 모델은 동 전체 대상만 지원합니다.");
		}
		if (!houseRepository.existsById(scopeId)) {
			throw new NotFoundException("동을 찾을 수 없습니다.");
		}
		return orchidGroupRepository.findActiveWorkTargetsByHouseId(scopeId).stream()
				.map(this::toResolvedTarget)
				.toList();
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
				location(group));
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
