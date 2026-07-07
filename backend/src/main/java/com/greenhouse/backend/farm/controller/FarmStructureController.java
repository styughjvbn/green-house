package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.FarmQueryService;
import com.greenhouse.backend.farm.dto.BedZoneResponse;
import com.greenhouse.backend.farm.dto.HouseResponse;
import com.greenhouse.backend.farm.dto.PhysicalBedResponse;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FarmStructureController {

	private final FarmQueryService farmQueryService;

	@GetMapping("/houses")
	public ApiResponse<List<HouseResponse>> getHouses() {
		return ApiResponse.ok(farmQueryService.getHouses());
	}

	@GetMapping("/houses/{houseId}")
	public ApiResponse<HouseResponse> getHouse(@PathVariable Long houseId) {
		return ApiResponse.ok(farmQueryService.getHouse(houseId));
	}

	@GetMapping("/physical-beds")
	public ApiResponse<List<PhysicalBedResponse>> getPhysicalBeds(@RequestParam Long houseId) {
		return ApiResponse.ok(farmQueryService.getPhysicalBeds(houseId));
	}

	@GetMapping("/physical-beds/{physicalBedId}")
	public ApiResponse<PhysicalBedResponse> getPhysicalBed(@PathVariable Long physicalBedId) {
		return ApiResponse.ok(farmQueryService.getPhysicalBed(physicalBedId));
	}

	@GetMapping("/bed-zones")
	public ApiResponse<List<BedZoneResponse>> getBedZones(
			@RequestParam(required = false) Long houseId,
			@RequestParam(required = false) Long physicalBedId) {
		return ApiResponse.ok(farmQueryService.getBedZones(houseId, physicalBedId));
	}

	@GetMapping("/bed-zones/{bedZoneId}")
	public ApiResponse<BedZoneResponse> getBedZone(@PathVariable Long bedZoneId) {
		return ApiResponse.ok(farmQueryService.getBedZone(bedZoneId));
	}

}
