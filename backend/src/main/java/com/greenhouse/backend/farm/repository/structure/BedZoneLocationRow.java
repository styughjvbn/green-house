package com.greenhouse.backend.farm.repository.structure;

import com.greenhouse.backend.farm.domain.structure.BedZoneSide;

public record BedZoneLocationRow(Long id, Integer houseNumber, Integer physicalBedNumber, BedZoneSide side,
		String bedZoneName) {
}
