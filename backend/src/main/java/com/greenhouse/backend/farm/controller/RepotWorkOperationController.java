package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.RepotWorkOperationService;
import com.greenhouse.backend.farm.dto.RepotWorkOperationRequest;
import com.greenhouse.backend.farm.dto.RepotWorkOperationResponse;
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
public class RepotWorkOperationController {

	private final RepotWorkOperationService service;

	@PostMapping("/repot")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<RepotWorkOperationResponse> execute(
			@Valid @RequestBody RepotWorkOperationRequest request) {
		return ApiResponse.ok(service.execute(request));
	}

	@GetMapping("/{workOperationId}/repot-results")
	public ApiResponse<RepotWorkOperationResponse> get(@PathVariable Long workOperationId) {
		return ApiResponse.ok(service.get(workOperationId));
	}
}
