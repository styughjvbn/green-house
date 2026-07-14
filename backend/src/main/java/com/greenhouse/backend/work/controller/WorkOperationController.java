package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.application.WorkOperationService;
import com.greenhouse.backend.work.dto.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.WorkTargetPreviewResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.features", name = "work-operation-v2-enabled", havingValue = "true")
public class WorkOperationController {

	private final WorkOperationService workOperationService;

	@PostMapping("/work-operations/target-preview")
	public ApiResponse<WorkTargetPreviewResponse> preview(@Valid @RequestBody WorkTargetPreviewRequest request) {
		return ApiResponse.ok(workOperationService.preview(request));
	}

	@PostMapping("/work-operations")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkOperationResponse> create(@Valid @RequestBody WorkOperationCreateRequest request) {
		return ApiResponse.ok(workOperationService.create(request));
	}

	@GetMapping("/work-operations/{workOperationId}")
	public ApiResponse<WorkOperationResponse> get(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.get(workOperationId));
	}

	@PostMapping("/work-operations/{workOperationId}/complete")
	public ApiResponse<WorkOperationResponse> complete(@PathVariable Long workOperationId) {
		return ApiResponse.ok(workOperationService.complete(workOperationId));
	}

	@GetMapping("/orchid-groups/{orchidGroupId}/work-history")
	public ApiResponse<List<OrchidGroupWorkHistoryResponse>> getOrchidGroupHistory(
			@PathVariable Long orchidGroupId) {
		return ApiResponse.ok(workOperationService.getOrchidGroupHistory(orchidGroupId));
	}
}
