package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.House;
import java.util.List;

public record HouseResponse(
		Long id,
		Integer number,
		String name,
		String memo,
		List<PhysicalBedResponse> physicalBeds) {

	public static HouseResponse from(House house) {
		return new HouseResponse(
				house.getId(),
				house.getNumber(),
				house.getName(),
				house.getMemo(),
				house.getPhysicalBeds().stream().map(PhysicalBedResponse::from).toList());
	}
}
