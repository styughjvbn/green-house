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
	private final OrchidPlacementPolicy orchidPlacementPolicy;

	public OrchidGroupResponse create(OrchidGroupCreateRequest request) {
		return OrchidGroupResponse.from(createEntity(request));
	}

	public OrchidGroup createEntity(OrchidGroupCreateRequest request) {
		BedZone bedZone = findZone(request.bedZoneId());
		Variety variety = findVariety(request.varietyId());
		if (!variety.isActive()) {
			throw new IllegalArgumentException("비활성 품종으로 난 묶음을 생성할 수 없습니다.");
		}
		BigDecimal startPosition = orchidPlacementPolicy.normalizeNumber(request.startPosition());
		BigDecimal endPosition = orchidPlacementPolicy.normalizeNumber(request.endPosition());
		orchidPlacementPolicy.validatePlacement(bedZone, startPosition, endPosition, null);

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
				startPosition,
				endPosition);
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
				startPosition,
				endPosition,
				normalize(request.memo()));
		orchidGroup.assignVariety(variety);
		return orchidGroupRepository.save(orchidGroup);
	}

	public OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		Variety variety = findVariety(request.varietyId());
		BigDecimal startPosition = orchidPlacementPolicy.normalizeNumber(request.startPosition());
		BigDecimal endPosition = orchidPlacementPolicy.normalizeNumber(request.endPosition());
		orchidPlacementPolicy.validatePlacement(orchidGroup.getBedZone(), startPosition, endPosition, orchidGroupId);

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
				startPosition,
				endPosition,
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
		return move(orchidGroupId, request, true);
	}

	public OrchidGroupResponse moveForOperation(Long orchidGroupId, OrchidGroupMoveRequest request) {
		return move(orchidGroupId, request, false);
	}

	private OrchidGroupResponse move(
			Long orchidGroupId,
			OrchidGroupMoveRequest request,
			boolean recordLegacyWork) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		BedZone toBedZone = findZone(request.toBedZoneId());
		BigDecimal startPosition = orchidPlacementPolicy.normalizeNumber(request.startPosition());
		BigDecimal endPosition = orchidPlacementPolicy.normalizeNumber(request.endPosition());
		orchidPlacementPolicy.validatePlacement(toBedZone, startPosition, endPosition, orchidGroupId);

		Long fromBedZoneId = orchidGroup.getBedZone().getId();
		if (fromBedZoneId.equals(toBedZone.getId())
				&& equalPosition(orchidGroup.getStartPosition(), startPosition)
				&& equalPosition(orchidGroup.getEndPosition(), endPosition)) {
			return OrchidGroupResponse.from(orchidGroup);
		}

		if (!fromBedZoneId.equals(toBedZone.getId())) {
			int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(toBedZone.getId()) + 1;
			orchidGroup.moveTo(toBedZone, nextSortOrder, startPosition, endPosition);
		} else {
			orchidGroup.moveTo(toBedZone, orchidGroup.getSortOrder(), startPosition, endPosition);
		}

		if (recordLegacyWork) {
			movementWorkRecorder.record(
					orchidGroup.getId(),
					fromBedZoneId,
					toBedZone.getId(),
					normalize(request.worker()),
					normalize(request.memo()));
		}
		return OrchidGroupResponse.from(orchidGroup);
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
}
