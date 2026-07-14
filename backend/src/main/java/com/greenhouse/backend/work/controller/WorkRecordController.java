package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.application.WorkRecordService;
import com.greenhouse.backend.work.application.WorkTypeService;
import com.greenhouse.backend.work.dto.WorkRecordCancelRequest;
import com.greenhouse.backend.work.dto.WorkRecordCreateRequest;
import com.greenhouse.backend.work.dto.WorkRecordResponse;
import com.greenhouse.backend.work.dto.WorkTypeCreateRequest;
import com.greenhouse.backend.work.dto.WorkTypeReorderRequest;
import com.greenhouse.backend.work.dto.WorkTypeResponse;
import com.greenhouse.backend.work.dto.WorkTypeUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkRecordController {

	private final WorkRecordService workRecordService;
	private final WorkTypeService workTypeService;

	@GetMapping("/work-records")
	public ApiResponse<List<WorkRecordResponse>> getWorkRecords(
			@RequestParam(required = false) String targetType,
			@RequestParam(required = false) Long targetId,
			@RequestParam(required = false) String workType,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "false") boolean includeCanceled) {
		return ApiResponse.ok(workRecordService.getWorkRecords(
				targetType,
				targetId,
				workType,
				from,
				to,
				includeCanceled));
	}

	@PostMapping("/work-records")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkRecordResponse> create(@Valid @RequestBody WorkRecordCreateRequest request) {
		return ApiResponse.ok(workRecordService.create(request));
	}

	@PatchMapping("/work-records/{workRecordId}/cancel")
	public ApiResponse<WorkRecordResponse> cancel(
			@PathVariable Long workRecordId,
			@Valid @RequestBody WorkRecordCancelRequest request) {
		return ApiResponse.ok(workRecordService.cancel(workRecordId, request));
	}

	@GetMapping("/work-types")
	public ApiResponse<List<WorkTypeResponse>> getWorkTypes(
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return ApiResponse.ok(workTypeService.getWorkTypes(includeInactive));
	}

	@PostMapping("/work-types")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkTypeResponse> createWorkType(@Valid @RequestBody WorkTypeCreateRequest request) {
		return ApiResponse.ok(workTypeService.create(request));
	}

	@PatchMapping("/work-types/{workTypeId}")
	public ApiResponse<WorkTypeResponse> updateWorkType(
			@PathVariable Long workTypeId,
			@Valid @RequestBody WorkTypeUpdateRequest request) {
		return ApiResponse.ok(workTypeService.update(workTypeId, request));
	}

	@PatchMapping("/work-types/reorder")
	public ApiResponse<List<WorkTypeResponse>> reorderWorkTypes(@Valid @RequestBody WorkTypeReorderRequest request) {
		return ApiResponse.ok(workTypeService.reorder(request));
	}
}
