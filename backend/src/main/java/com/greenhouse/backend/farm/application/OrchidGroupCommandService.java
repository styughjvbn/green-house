package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.dto.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.VarietyRepository;
import com.greenhouse.backend.work.application.MovementWorkRecorder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupCommandService {

	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundRecordRepository inboundRecordRepository;
	private final VarietyRepository varietyRepository;
	private final MovementWorkRecorder movementWorkRecorder;

	public OrchidGroupResponse create(OrchidGroupCreateRequest request) {
		BedZone bedZone = findZone(request.bedZoneId());
		Variety variety = findVariety(request.varietyId());
		validatePlacementRange(bedZone, request.startPosition(), request.endPosition());
		int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1;
		OrchidGroup orchidGroup = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				request.quantity(),
				normalize(request.potSize()),
				request.ageYear(),
				normalizeRequired(request.status()),
				nextSortOrder,
				normalizeNumber(request.startPosition()),
				normalizeNumber(request.endPosition()));
		orchidGroup.updateDetails(
				variety.getGenus(),
				variety.getName(),
				request.quantity(),
				normalize(request.potSize()),
				request.ageYear(),
				normalizeRequired(request.status()),
				normalize(request.placementType()),
				request.trayCount(),
				request.splitPlacementAllowed(),
				normalizeNumber(request.startPosition()),
				normalizeNumber(request.endPosition()),
				normalize(request.memo()));
		orchidGroup.assignVariety(variety);
		return OrchidGroupResponse.from(orchidGroupRepository.save(orchidGroup));
	}

	public OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		Variety variety = findVariety(request.varietyId());
		validatePlacementRange(orchidGroup.getBedZone(), request.startPosition(), request.endPosition());
		orchidGroup.updateDetails(
				variety.getGenus(),
				variety.getName(),
				request.quantity(),
				normalize(request.potSize()),
				request.ageYear(),
				normalizeRequired(request.status()),
				normalize(request.placementType()),
				request.trayCount(),
				request.splitPlacementAllowed(),
				normalizeNumber(request.startPosition()),
				normalizeNumber(request.endPosition()),
				normalize(request.memo()));
		orchidGroup.assignVariety(variety);
		return OrchidGroupResponse.from(orchidGroup);
	}

	public void delete(Long orchidGroupId) {
		if (!orchidGroupRepository.existsById(orchidGroupId)) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		inboundRecordRepository.clearCreatedOrchidGroup(orchidGroupId);
		orchidGroupRepository.deleteById(orchidGroupId);
	}

	public OrchidGroupResponse move(Long orchidGroupId, OrchidGroupMoveRequest request) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		BedZone toBedZone = findZone(request.toBedZoneId());
		validatePlacementRange(toBedZone, request.startPosition(), request.endPosition());

		Long fromBedZoneId = orchidGroup.getBedZone().getId();
		if (fromBedZoneId.equals(toBedZone.getId())
				&& equalPosition(orchidGroup.getStartPosition(), request.startPosition())
				&& equalPosition(orchidGroup.getEndPosition(), request.endPosition())) {
			return OrchidGroupResponse.from(orchidGroup);
		}

		if (!fromBedZoneId.equals(toBedZone.getId())) {
			int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(toBedZone.getId()) + 1;
			orchidGroup.moveTo(
					toBedZone,
					nextSortOrder,
					normalizeNumber(request.startPosition()),
					normalizeNumber(request.endPosition()));
		} else {
			orchidGroup.moveTo(
					toBedZone,
					orchidGroup.getSortOrder(),
					normalizeNumber(request.startPosition()),
					normalizeNumber(request.endPosition()));
		}

		movementWorkRecorder.record(
				orchidGroup.getId(),
				fromBedZoneId,
				toBedZone.getId(),
				normalize(request.worker()),
				normalize(request.memo()));
		return OrchidGroupResponse.from(orchidGroup);
	}

	private void validatePlacementRange(BedZone bedZone, BigDecimal startPosition, BigDecimal endPosition) {
		if (startPosition == null && endPosition == null) {
			return;
		}
		if (startPosition == null || endPosition == null) {
			throw new IllegalArgumentException("시작 위치와 종료 위치는 함께 입력해야 합니다.");
		}
		if (endPosition.compareTo(startPosition) <= 0) {
			throw new IllegalArgumentException("종료 위치는 시작 위치보다 커야 합니다.");
		}
		BigDecimal maxPosition = bedZone.getPhysicalBed().getPositionUnitCount();
		if (maxPosition != null && endPosition.compareTo(maxPosition) > 0) {
			throw new IllegalArgumentException("종료 위치는 배드 기준 치수를 넘을 수 없습니다.");
		}
	}

	private boolean equalPosition(BigDecimal currentValue, BigDecimal requestValue) {
		if (currentValue == null && requestValue == null) {
			return true;
		}
		if (currentValue == null || requestValue == null) {
			return false;
		}
		return currentValue.compareTo(requestValue) == 0;
	}

	private BedZone findZone(Long bedZoneId) {
		return bedZoneRepository.findWithDetailsById(bedZoneId)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private Variety findVariety(Long varietyId) {
		return varietyRepository.findById(varietyId)
				.orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return normalized;
	}

	private BigDecimal normalizeNumber(BigDecimal value) {
		if (value == null) {
			return null;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}
}
