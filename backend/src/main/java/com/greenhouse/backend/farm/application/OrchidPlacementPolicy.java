package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrchidPlacementPolicy {

	private static final BigDecimal MIN_SPAN = BigDecimal.ONE.setScale(2);

	private final OrchidGroupRepository orchidGroupRepository;

	public BigDecimal normalizeNumber(BigDecimal value) {
		if (value == null) {
			return null;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	public void validatePlacement(BedZone bedZone, BigDecimal startPosition, BigDecimal endPosition, Long excludeOrchidGroupId) {
		if (startPosition == null || endPosition == null) {
			throw new IllegalArgumentException("시작 위치와 종료 위치를 모두 입력해야 합니다.");
		}
		if (endPosition.compareTo(startPosition) <= 0) {
			throw new IllegalArgumentException("종료 위치는 시작 위치보다 커야 합니다.");
		}
		if (endPosition.subtract(startPosition).compareTo(MIN_SPAN) < 0) {
			throw new IllegalArgumentException("난 묶음은 최소 1칸 이상을 차지해야 합니다.");
		}
		BigDecimal maxPosition = bedZone.getPhysicalBed().getPositionUnitCount();
		if (maxPosition != null && endPosition.compareTo(maxPosition) > 0) {
			throw new IllegalArgumentException("종료 위치는 배드 최대 칸 수를 넘을 수 없습니다.");
		}
		validateNoOverlap(bedZone, startPosition, endPosition, excludeOrchidGroupId);
	}

	public PlacementRange findFirstAvailableSingleSlot(BedZone bedZone) {
		BigDecimal maxPosition = bedZone.getPhysicalBed().getPositionUnitCount();
		if (maxPosition == null) {
			throw new IllegalArgumentException("배드 칸 수 정보가 없어 자동 배치할 수 없습니다.");
		}

		BigDecimal cursor = BigDecimal.ZERO.setScale(2);
		List<OrchidGroup> positionedGroups = orchidGroupRepository.findByBedZoneIdOrderBySortOrderAsc(bedZone.getId())
				.stream()
				.filter(group -> group.getStartPosition() != null && group.getEndPosition() != null)
				.sorted(Comparator.comparing(OrchidGroup::getStartPosition).thenComparing(OrchidGroup::getSortOrder))
				.toList();

		for (OrchidGroup group : positionedGroups) {
			BigDecimal start = normalizeNumber(group.getStartPosition());
			BigDecimal end = normalizeNumber(group.getEndPosition());
			if (start.subtract(cursor).compareTo(MIN_SPAN) >= 0) {
				return new PlacementRange(cursor, cursor.add(MIN_SPAN));
			}
			if (end.compareTo(cursor) > 0) {
				cursor = end;
			}
		}

		if (maxPosition.subtract(cursor).compareTo(MIN_SPAN) >= 0) {
			return new PlacementRange(cursor, cursor.add(MIN_SPAN));
		}

		throw new IllegalArgumentException("선택한 구역에 1칸 이상 비어 있는 공간이 없습니다.");
	}

	private void validateNoOverlap(BedZone bedZone, BigDecimal startPosition, BigDecimal endPosition, Long excludeOrchidGroupId) {
		for (OrchidGroup group : orchidGroupRepository.findByBedZoneIdOrderBySortOrderAsc(bedZone.getId())) {
			if (excludeOrchidGroupId != null && excludeOrchidGroupId.equals(group.getId())) {
				continue;
			}
			if (group.getStartPosition() == null || group.getEndPosition() == null) {
				continue;
			}
			if (isOverlapping(
					startPosition,
					endPosition,
					normalizeNumber(group.getStartPosition()),
					normalizeNumber(group.getEndPosition()))) {
				throw new IllegalArgumentException("선택한 위치가 기존 난 묶음 배치와 겹칩니다.");
			}
		}
	}

	private boolean isOverlapping(
			BigDecimal candidateStart,
			BigDecimal candidateEnd,
			BigDecimal existingStart,
			BigDecimal existingEnd) {
		return candidateStart.compareTo(existingEnd) < 0 && candidateEnd.compareTo(existingStart) > 0;
	}

	public record PlacementRange(BigDecimal startPosition, BigDecimal endPosition) {
	}
}
