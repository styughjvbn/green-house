package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.InboundRecordService;
import com.greenhouse.backend.farm.domain.InboundStatus;
import com.greenhouse.backend.farm.domain.InboundType;
import com.greenhouse.backend.farm.dto.InboundRecordCancelRequest;
import com.greenhouse.backend.farm.dto.InboundRecordCreateRequest;
import com.greenhouse.backend.farm.dto.InboundRecordPageResponse;
import com.greenhouse.backend.farm.dto.InboundRecordPottingRequest;
import com.greenhouse.backend.farm.dto.InboundRecordResponse;
import com.greenhouse.backend.farm.dto.InboundRecordUpdateRequest;
import com.greenhouse.backend.work.application.InboundPottingOperationService;
import com.greenhouse.backend.work.dto.InboundPottingExecutionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/inbound-records")
@RequiredArgsConstructor
public class InboundRecordController {

	private final InboundRecordService inboundRecordService;
	private final InboundPottingOperationService inboundPottingOperationService;

	@GetMapping
	public ApiResponse<InboundRecordPageResponse> getInboundRecords(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) InboundType inboundType,
			@RequestParam(required = false) InboundStatus status,
			@RequestParam(required = false) String variety,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse
				.ok(inboundRecordService.getInboundRecords(from, to, inboundType, status, variety, page, size));
	}

	@GetMapping("/{inboundRecordId}")
	public ApiResponse<InboundRecordResponse> getInboundRecord(@PathVariable Long inboundRecordId) {
		return ApiResponse.ok(inboundRecordService.getInboundRecord(inboundRecordId));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<InboundRecordResponse> create(@Valid @RequestBody InboundRecordCreateRequest request) {
		return ApiResponse.ok(inboundRecordService.create(request));
	}

	@PatchMapping("/{inboundRecordId}")
	public ApiResponse<InboundRecordResponse> update(
			@PathVariable Long inboundRecordId,
			@Valid @RequestBody InboundRecordUpdateRequest request) {
		return ApiResponse.ok(inboundRecordService.update(inboundRecordId, request));
	}

	@PostMapping("/{inboundRecordId}/potting")
	public ApiResponse<InboundRecordResponse> potting(
			@PathVariable Long inboundRecordId,
			@Valid @RequestBody InboundRecordPottingRequest request) {
		inboundPottingOperationService.executeNow(
				new InboundPottingExecutionRequest(
						inboundRecordId,
						request.pottingDate(),
						request.actualQuantity(),
						request.potSize(),
						request.ageYear(),
						request.growthStage(),
						request.placementType(),
						request.trayCount(),
						request.bedZoneId(),
						request.startPosition(),
						request.endPosition(),
						request.worker(),
						request.memo()));
		return ApiResponse.ok(inboundRecordService.getInboundRecord(inboundRecordId));
	}

	@PostMapping("/{inboundRecordId}/cancel")
	public ApiResponse<InboundRecordResponse> cancel(
			@PathVariable Long inboundRecordId,
			@RequestBody(required = false) InboundRecordCancelRequest request) {
		return ApiResponse.ok(inboundRecordService.cancel(
				inboundRecordId,
				request == null ? new InboundRecordCancelRequest(null) : request));
	}

	@DeleteMapping("/{inboundRecordId}")
	public ApiResponse<Void> delete(@PathVariable Long inboundRecordId) {
		inboundRecordService.delete(inboundRecordId);
		return ApiResponse.ok(null);
	}
}
