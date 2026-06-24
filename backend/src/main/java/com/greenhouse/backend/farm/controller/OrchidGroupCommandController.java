package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.OrchidGroupCommandService;
import com.greenhouse.backend.farm.dto.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.OrchidGroupUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchid-groups")
public class OrchidGroupCommandController {

	private final OrchidGroupCommandService orchidGroupCommandService;

	public OrchidGroupCommandController(OrchidGroupCommandService orchidGroupCommandService) {
		this.orchidGroupCommandService = orchidGroupCommandService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<OrchidGroupResponse> create(@Valid @RequestBody OrchidGroupCreateRequest request) {
		return ApiResponse.ok(orchidGroupCommandService.create(request));
	}

	@PatchMapping("/{orchidGroupId}")
	public ApiResponse<OrchidGroupResponse> update(
		@PathVariable Long orchidGroupId,
		@Valid @RequestBody OrchidGroupUpdateRequest request
	) {
		return ApiResponse.ok(orchidGroupCommandService.update(orchidGroupId, request));
	}

	@DeleteMapping("/{orchidGroupId}")
	public ApiResponse<Void> delete(@PathVariable Long orchidGroupId) {
		orchidGroupCommandService.delete(orchidGroupId);
		return ApiResponse.ok(null);
	}
}
