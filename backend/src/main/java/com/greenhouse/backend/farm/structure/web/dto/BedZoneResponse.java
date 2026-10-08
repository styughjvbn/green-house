package com.greenhouse.backend.farm.structure.web.dto;

import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import com.greenhouse.backend.farm.structure.domain.BedZoneType;
import java.time.LocalDate;
import java.util.List;

public record BedZoneResponse(
    Long id,
    Long physicalBedId,
    Integer physicalBedNumber,
    Long houseId,
    Integer houseNumber,
    String name,
    BedZoneSide side,
    BedZoneType zoneType,
    Integer sortOrder,
    Boolean active,
    String memo,
    List<OrchidGroupResponse> orchidGroups) {

  public static BedZoneResponse from(
      BedZone bedZone, List<OrchidGroup> groups, LocalDate businessDate) {
    var physicalBed = bedZone.getPhysicalBed();
    var house = physicalBed.getHouse();
    return new BedZoneResponse(
        bedZone.getId(),
        physicalBed.getId(),
        physicalBed.getNumber(),
        house.getId(),
        house.getNumber(),
        bedZone.getName(),
        bedZone.getSide(),
        bedZone.getZoneType(),
        bedZone.getSortOrder(),
        bedZone.getActive(),
        bedZone.getMemo(),
        groups.stream()
            .filter(
                orchidGroup -> orchidGroup.getQuantity() != null && orchidGroup.getQuantity() > 0)
            .map(group -> OrchidGroupResponse.from(group, businessDate))
            .toList());
  }
}
