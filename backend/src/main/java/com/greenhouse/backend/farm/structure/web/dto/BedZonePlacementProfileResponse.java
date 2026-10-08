package com.greenhouse.backend.farm.structure.web.dto;

import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneCapacity;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record BedZonePlacementProfileResponse(
    Long bedZoneId,
    String bedZoneName,
    Integer houseNumber,
    Integer physicalBedNumber,
    BigDecimal positionUnitCount,
    String positionUnitLabel,
    List<BedZoneCapacityResponse> capacities) {
  public static BedZonePlacementProfileResponse from(BedZone bedZone) {
    return new BedZonePlacementProfileResponse(
        bedZone.getId(),
        bedZone.getName(),
        bedZone.getPhysicalBed().getHouse().getNumber(),
        bedZone.getPhysicalBed().getNumber(),
        bedZone.getPhysicalBed().getPositionUnitCount(),
        bedZone.getPhysicalBed().getPositionUnitLabel(),
        bedZone.getCapacities().stream()
            .sorted(
                Comparator.comparingInt(
                    (BedZoneCapacity capacity) -> capacity.getCapacityMode().strength()))
            .map(BedZoneCapacityResponse::from)
            .toList());
  }
}
