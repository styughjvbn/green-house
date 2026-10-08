package com.greenhouse.backend.farm.dto.inbound;

import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import java.math.BigDecimal;

public record InboundOrchidGroupResponse(
    Long id,
    Integer quantity,
    String potSize,
    Integer ageYear,
    String status,
    String placementType,
    Integer trayCount,
    Long bedZoneId,
    String location,
    BigDecimal startPosition,
    BigDecimal endPosition) {

  public static InboundOrchidGroupResponse from(OrchidGroup group) {
    var zone = group.getBedZone();
    var bed = zone.getPhysicalBed();
    var house = bed.getHouse();
    String location = "%d동-%d다이 %s".formatted(house.getNumber(), bed.getNumber(), zone.getName());
    return new InboundOrchidGroupResponse(
        group.getId(),
        group.getQuantity(),
        group.getPotSize(),
        group.getAgeYear(),
        group.getStatus(),
        group.getPlacementType(),
        group.getTrayCount(),
        zone.getId(),
        location,
        group.getStartPosition(),
        group.getEndPosition());
  }
}
