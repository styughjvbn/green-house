package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.MultiCreateWorkOperationService;
import com.greenhouse.backend.farm.dto.MultiCreateWorkOperationRequest;
import com.greenhouse.backend.farm.dto.MultiCreateWorkOperationResponse;
import com.greenhouse.backend.farm.dto.MultiCreateCancellationEligibilityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-operations")
@RequiredArgsConstructor
public class MultiCreateWorkOperationController {

	private final MultiCreateWorkOperationService service;

	@PostMapping("/multi-create")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MultiCreateWorkOperationResponse> create(
			@Valid @RequestBody MultiCreateWorkOperationRequest request) {
		return ApiResponse.ok(service.create(request));
	}

	@GetMapping("/{workOperationId}/created-orchid-groups")
	public ApiResponse<MultiCreateWorkOperationResponse> get(@PathVariable Long workOperationId) {
		return ApiResponse.ok(service.get(workOperationId));
	}

	@GetMapping("/{workOperationId}/cancel-eligibility")
	public ApiResponse<MultiCreateCancellationEligibilityResponse> getCancellationEligibility(
			@PathVariable Long workOperationId) {
		return ApiResponse.ok(service.getCancellationEligibility(workOperationId));
	}

	@PostMapping("/{workOperationId}/cancel-created-orchid-groups")
	public ApiResponse<MultiCreateWorkOperationResponse> cancel(@PathVariable Long workOperationId) {
		return ApiResponse.ok(service.cancel(workOperationId));
	}
}
