package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
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
	private final PlacementRecommendationService placementRecommendationService;

	public OrchidGroupResponse create(OrchidGroupCreateRequest request) {
		var bedZone = bedZoneRepository.findWithDetailsById(request.bedZoneId())
				.orElseThrow(() -> new NotFoundException("?쇰━ 援ъ뿭??李얠쓣 ???놁뒿?덈떎."));
		var variety = findVariety(request.varietyId());
		int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1;
		var orchidGroup = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				request.quantity(),
				normalize(request.potSize()),
				request.ageYear(),
				normalizeRequired(request.status()),
				nextSortOrder);
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
				normalize(request.memo()));
		orchidGroup.assignVariety(variety);
		return OrchidGroupResponse.from(orchidGroupRepository.save(orchidGroup));
	}

	public OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request) {
		var orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("??臾띠쓬??李얠쓣 ???놁뒿?덈떎."));
		var variety = findVariety(request.varietyId());
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
				normalize(request.memo()));
		orchidGroup.assignVariety(variety);
		return OrchidGroupResponse.from(orchidGroup);
	}

	public void delete(Long orchidGroupId) {
		if (!orchidGroupRepository.existsById(orchidGroupId)) {
			throw new NotFoundException("??臾띠쓬??李얠쓣 ???놁뒿?덈떎.");
		}
		inboundRecordRepository.clearCreatedOrchidGroup(orchidGroupId);
		orchidGroupRepository.deleteById(orchidGroupId);
	}

	public OrchidGroupResponse move(Long orchidGroupId, OrchidGroupMoveRequest request) {
		var orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("??臾띠쓬??李얠쓣 ???놁뒿?덈떎."));
		var toBedZone = bedZoneRepository.findWithDetailsById(request.toBedZoneId())
				.orElseThrow(() -> new NotFoundException("?쇰━ 援ъ뿭??李얠쓣 ???놁뒿?덈떎."));

		Long fromBedZoneId = orchidGroup.getBedZone().getId();
		boolean precisePlacement = request.placements() != null && !request.placements().isEmpty();
		if (fromBedZoneId.equals(toBedZone.getId()) && !precisePlacement) {
			return OrchidGroupResponse.from(orchidGroup);
		}

		if (precisePlacement) {
			var placements = placementRecommendationService.validateAndCreatePlacements(
					orchidGroup,
					toBedZone,
					request.placementMode(),
					request.placements(),
					request.reorganizeDueDate(),
					normalize(request.memo()));
			orchidGroup.replaceSegmentPlacements(placements);
		} else {
			orchidGroup.replaceSegmentPlacements(java.util.List.of());
		}

		if (!fromBedZoneId.equals(toBedZone.getId())) {
			int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(toBedZone.getId()) + 1;
			orchidGroup.moveTo(toBedZone, nextSortOrder);
		}

		movementWorkRecorder.record(
				orchidGroup.getId(),
				fromBedZoneId,
				toBedZone.getId(),
				normalize(request.worker()),
				normalize(request.memo()));
		return OrchidGroupResponse.from(orchidGroup);
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
			throw new IllegalArgumentException("?꾩닔 臾몄옄??媛믪? 鍮꾩썙?????놁뒿?덈떎.");
		}
		return normalized;
	}
}
