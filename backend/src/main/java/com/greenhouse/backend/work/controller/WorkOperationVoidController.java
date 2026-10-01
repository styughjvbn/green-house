package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.application.operation.WorkOperationVoidService;
import com.greenhouse.backend.work.dto.operation.WorkOperationVoidEligibilityResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationVoidRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-operations")
@RequiredArgsConstructor
public class WorkOperationVoidController {

	private final WorkOperationVoidService service;

	@GetMapping("/{workOperationId}/void-eligibility")
	public ApiResponse<WorkOperationVoidEligibilityResponse> eligibility(@PathVariable Long workOperationId) {
		return ApiResponse.ok(service.eligibility(workOperationId));
	}

	@PostMapping("/{workOperationId}/void")
	public ApiResponse<WorkOperationView> voidOperation(@PathVariable Long workOperationId,
			@Valid @RequestBody WorkOperationVoidRequest request) {
		return ApiResponse.ok(service.voidOperation(workOperationId, request));
	}

}
