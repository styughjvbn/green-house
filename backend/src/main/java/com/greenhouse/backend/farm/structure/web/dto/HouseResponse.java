package com.greenhouse.backend.farm.structure.web.dto;

import com.greenhouse.backend.farm.structure.domain.House;
import java.util.List;

public record HouseResponse(
    Long id, Integer number, String name, String memo, List<PhysicalBedResponse> physicalBeds) {

  public static HouseResponse from(House house, List<PhysicalBedResponse> physicalBeds) {
    return new HouseResponse(
        house.getId(), house.getNumber(), house.getName(), house.getMemo(), physicalBeds);
  }
}
