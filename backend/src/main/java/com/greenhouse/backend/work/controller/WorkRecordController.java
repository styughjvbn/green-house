package com.greenhouse.backend.work.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.work.application.WorkRecordService;
import com.greenhouse.backend.work.dto.WorkRecordCreateRequest;
import com.greenhouse.backend.work.dto.WorkRecordResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkRecordController {

	private final WorkRecordService workRecordService;

	public WorkRecordController(WorkRecordService workRecordService) {
		this.workRecordService = workRecordService;
	}

	@GetMapping("/work-records")
	public ApiResponse<List<WorkRecordResponse>> getWorkRecords(
		@RequestParam(required = false) String targetType,
		@RequestParam(required = false) Long targetId,
		@RequestParam(required = false) String workType,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to
	) {
		return ApiResponse.ok(workRecordService.getWorkRecords(targetType, targetId, workType, from, to));
	}

	@PostMapping("/work-records")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WorkRecordResponse> create(@Valid @RequestBody WorkRecordCreateRequest request) {
		return ApiResponse.ok(workRecordService.create(request));
	}

	@GetMapping("/work-types")
	public ApiResponse<List<String>> getWorkTypes() {
		return ApiResponse.ok(workRecordService.getWorkTypes());
	}
}
