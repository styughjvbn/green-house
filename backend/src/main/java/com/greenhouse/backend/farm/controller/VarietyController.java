package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.VarietyService;
import com.greenhouse.backend.farm.dto.VarietyConnectedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.VarietyCreateRequest;
import com.greenhouse.backend.farm.dto.VarietyResponse;
import com.greenhouse.backend.farm.dto.VarietyUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/varieties")
public class VarietyController {
	private final VarietyService varietyService;

	public VarietyController(VarietyService varietyService) {
		this.varietyService = varietyService;
	}

	@GetMapping
	public ApiResponse<List<VarietyResponse>> getVarieties(
		@RequestParam(required = false) String keyword,
		@RequestParam(required = false) String genus,
		@RequestParam(required = false) Boolean saleEnabled,
		@RequestParam(required = false) Boolean active
	) {
		return ApiResponse.ok(varietyService.getVarieties(keyword, genus, saleEnabled, active));
	}

	@GetMapping("/{varietyId}")
	public ApiResponse<VarietyResponse> getVariety(@PathVariable Long varietyId) {
		return ApiResponse.ok(varietyService.getVariety(varietyId));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<VarietyResponse> create(@Valid @RequestBody VarietyCreateRequest request) {
		return ApiResponse.ok(varietyService.create(request));
	}

	@PatchMapping("/{varietyId}")
	public ApiResponse<VarietyResponse> update(
		@PathVariable Long varietyId,
		@Valid @RequestBody VarietyUpdateRequest request
	) {
		return ApiResponse.ok(varietyService.update(varietyId, request));
	}

	@PatchMapping("/{varietyId}/deactivate")
	public ApiResponse<VarietyResponse> deactivate(@PathVariable Long varietyId) {
		return ApiResponse.ok(varietyService.deactivate(varietyId));
	}

	@GetMapping("/{varietyId}/orchid-groups")
	public ApiResponse<List<VarietyConnectedOrchidGroupResponse>> getOrchidGroups(@PathVariable Long varietyId) {
		return ApiResponse.ok(varietyService.getOrchidGroups(varietyId));
	}
}
