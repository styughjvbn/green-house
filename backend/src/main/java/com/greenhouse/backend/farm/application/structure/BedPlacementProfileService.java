package com.greenhouse.backend.farm.application.structure;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedPlacementProfilePolicy;
import com.greenhouse.backend.farm.domain.structure.BedPlacementProfilePolicy.CapacityRule;
import com.greenhouse.backend.farm.dto.structure.BedZoneCapacityRequest;
import com.greenhouse.backend.farm.dto.structure.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.dto.structure.BedZonePlacementProfileResponse;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BedPlacementProfileService {

	private final BedZoneRepository bedZoneRepository;
	private final BedPlacementAuditSupport auditSupport;

	public BedZonePlacementProfileResponse getProfile(Long bedZoneId) {
		return BedZonePlacementProfileResponse.from(findZone(bedZoneId));
	}

	@Transactional
	public BedZonePlacementProfileResponse updateProfile(Long bedZoneId, BedZonePlacementProfileRequest request) {
		BedZone bedZone = findZone(bedZoneId);
		Map<String, Object> before = auditSupport.snapshot(bedZone);
		var rules = request.capacities().stream().map(this::toRule).toList();
		bedZone.replaceCapacities(BedPlacementProfilePolicy.createCapacities(rules));
		auditSupport.record(bedZone, before, auditSupport.snapshot(bedZone));
		return BedZonePlacementProfileResponse.from(bedZone);
	}

	private CapacityRule toRule(BedZoneCapacityRequest request) {
		return new CapacityRule(request.placementType(), request.potSize(), request.capacityMode(),
				request.unitSpan(), request.capacityValue(), request.allowed(), request.memo());
	}

	private BedZone findZone(Long id) {
		return bedZoneRepository.findWithDetailsById(id)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}
}
