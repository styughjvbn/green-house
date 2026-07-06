package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.dto.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.VarietyRepository;
import com.greenhouse.backend.work.application.MovementWorkRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrchidGroupCommandService {

	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final VarietyRepository varietyRepository;
	private final MovementWorkRecorder movementWorkRecorder;
	private final PlacementRecommendationService placementRecommendationService;

	public OrchidGroupCommandService(
		BedZoneRepository bedZoneRepository,
		OrchidGroupRepository orchidGroupRepository,
		VarietyRepository varietyRepository,
		MovementWorkRecorder movementWorkRecorder,
		PlacementRecommendationService placementRecommendationService
	) {
		this.bedZoneRepository = bedZoneRepository;
		this.orchidGroupRepository = orchidGroupRepository;
		this.varietyRepository = varietyRepository;
		this.movementWorkRecorder = movementWorkRecorder;
		this.placementRecommendationService = placementRecommendationService;
	}

	public OrchidGroupResponse create(OrchidGroupCreateRequest request) {
		var bedZone = bedZoneRepository.findWithDetailsById(request.bedZoneId())
			.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
		int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1;
		var orchidGroup = new OrchidGroup(
			bedZone,
			normalize(request.genus()),
			normalizeRequired(request.varietyName()),
			request.quantity(),
			normalize(request.potSize()),
			request.ageYear(),
			normalizeRequired(request.status()),
			nextSortOrder
		);
		orchidGroup.updateDetails(
			normalize(request.genus()),
			normalizeRequired(request.varietyName()),
			request.quantity(),
			normalize(request.potSize()),
			request.ageYear(),
			normalizeRequired(request.status()),
			normalize(request.placementType()),
			request.trayCount(),
			request.splitPlacementAllowed(),
			normalize(request.memo())
		);
		resolveVariety(request.genus(), request.varietyName()).ifPresent(orchidGroup::assignVariety);
		return OrchidGroupResponse.from(orchidGroupRepository.save(orchidGroup));
	}

	public OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request) {
		var orchidGroup = orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		orchidGroup.updateDetails(
			normalize(request.genus()),
			normalizeRequired(request.varietyName()),
			request.quantity(),
			normalize(request.potSize()),
			request.ageYear(),
			normalizeRequired(request.status()),
			normalize(request.placementType()),
			request.trayCount(),
			request.splitPlacementAllowed(),
			normalize(request.memo())
		);
		orchidGroup.assignVariety(resolveVariety(request.genus(), request.varietyName()).orElse(null));
		return OrchidGroupResponse.from(orchidGroup);
	}

	public void delete(Long orchidGroupId) {
		if (!orchidGroupRepository.existsById(orchidGroupId)) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		orchidGroupRepository.deleteById(orchidGroupId);
	}

	public OrchidGroupResponse move(Long orchidGroupId, OrchidGroupMoveRequest request) {
		var orchidGroup = orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		var toBedZone = bedZoneRepository.findWithDetailsById(request.toBedZoneId())
			.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));

		Long fromBedZoneId = orchidGroup.getBedZone().getId();
		boolean precisePlacement = request.placements() != null && !request.placements().isEmpty();
		if (fromBedZoneId.equals(toBedZone.getId()) && !precisePlacement) {
			return OrchidGroupResponse.from(orchidGroup);
		}

		if (precisePlacement) {
			var placements = placementRecommendationService.validateAndCreatePlacements(
				orchidGroup, toBedZone, request.placementMode(), request.placements(),
				request.reorganizeDueDate(), normalize(request.memo())
			);
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

	private java.util.Optional<com.greenhouse.backend.farm.domain.Variety> resolveVariety(String genus, String varietyName) {
		String normalizedGenus = normalize(genus);
		String normalizedVarietyName = normalize(varietyName);
		if (normalizedGenus == null || normalizedVarietyName == null) {
			return java.util.Optional.empty();
		}
		return varietyRepository.findByGenusAndName(normalizedGenus, normalizedVarietyName);
	}
}
