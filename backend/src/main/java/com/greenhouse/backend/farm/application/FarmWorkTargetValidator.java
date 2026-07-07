package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;
import com.greenhouse.backend.work.application.WorkTargetValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmWorkTargetValidator implements WorkTargetValidator {
	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	@Override
	public void validate(String targetType, Long targetId) {
		if ("FARM".equals(targetType))
			return;
		if (targetId == null)
			throw new IllegalArgumentException("Work target id is required.");

		boolean exists = switch (targetType) {
			case "HOUSE" -> houseRepository.existsById(targetId);
			case "PHYSICAL_BED" -> physicalBedRepository.existsById(targetId);
			case "BED_ZONE" -> bedZoneRepository.existsById(targetId);
			case "ORCHID_GROUP" -> orchidGroupRepository.existsById(targetId);
			default -> throw new IllegalArgumentException("Unsupported work target.");
		};
		if (!exists)
			throw new NotFoundException("Work target not found.");
	}
}
