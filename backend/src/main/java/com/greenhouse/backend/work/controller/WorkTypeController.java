package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.application.operation.WorkTypeService;
import com.greenhouse.backend.work.dto.operation.WorkTypeCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkTypeReorderRequest;
import com.greenhouse.backend.work.dto.operation.WorkTypeResponse;
import com.greenhouse.backend.work.dto.operation.WorkTypeUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/work-types")
@RequiredArgsConstructor
public class WorkTypeController {

	private final WorkTypeService workTypeService;

	@GetMapping
	public ApiResponse<List<WorkTypeResponse>> getWorkTypes(
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return ApiResponse.ok(workTypeService.getWorkTypes(includeInactive));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkTypeResponse> createWorkType(@Valid @RequestBody WorkTypeCreateRequest request) {
		return ApiResponse.ok(workTypeService.create(request));
	}

	@PatchMapping("/{workTypeId}")
	public ApiResponse<WorkTypeResponse> updateWorkType(
			@PathVariable Long workTypeId,
			@Valid @RequestBody WorkTypeUpdateRequest request) {
		return ApiResponse.ok(workTypeService.update(workTypeId, request));
	}

	@PatchMapping("/reorder")
	public ApiResponse<List<WorkTypeResponse>> reorderWorkTypes(
			@Valid @RequestBody WorkTypeReorderRequest request) {
		return ApiResponse.ok(workTypeService.reorder(request));
	}
}
