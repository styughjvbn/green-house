package com.greenhouse.backend.partner.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.partner.application.BusinessPartnerService;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.dto.BusinessPartnerCreateRequest;
import com.greenhouse.backend.partner.dto.BusinessPartnerOptionResponse;
import com.greenhouse.backend.partner.dto.BusinessPartnerResponse;
import com.greenhouse.backend.partner.dto.BusinessPartnerUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-partners")
@RequiredArgsConstructor
public class BusinessPartnerController {
	private final BusinessPartnerService service;

	@GetMapping
	@Operation(deprecated = true, description = "호환용 활성 거래처 목록. 이름·ID 순으로 최대 500건을 반환합니다. 관리 목록은 /page, 선택지는 /options를 사용합니다.")
	public ApiResponse<List<BusinessPartnerResponse>> getPartners(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) PartnerType partnerType) {
		return ApiResponse.ok(service.getPartners(keyword, partnerType));
	}

	@GetMapping("/page")
	public ApiResponse<PageResponse<BusinessPartnerResponse>> getPartnerPage(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) PartnerType partnerType,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(service.getPartnerPage(keyword, partnerType, active, page, size));
	}

	@GetMapping("/options")
	@Operation(description = "거래처 선택지를 이름·ID 순으로 검색합니다. 검색·활성·경매장 여부를 페이지 조회 전 적용하며, 조건 생략 시 전체를 포함합니다. page는 0 이상, size는 1~100으로 보정합니다.")
	public ApiResponse<PageResponse<BusinessPartnerOptionResponse>> getOptions(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Boolean auctionHouse,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(service.getOptions(keyword, auctionHouse, active, page, size));
	}

	@GetMapping("/{partnerId}/option")
	@Operation(description = "검색·페이지 범위 밖의 선택값을 표시합니다. 기존 기록의 비활성 거래처도 반환하며 신규 업무 사용 허용을 뜻하지 않습니다.")
	public ApiResponse<BusinessPartnerOptionResponse> getOption(@PathVariable Long partnerId) {
		return ApiResponse.ok(service.getOption(partnerId));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<BusinessPartnerResponse> create(@Valid @RequestBody BusinessPartnerCreateRequest request) {
		return ApiResponse.ok(service.create(request));
	}

	@PutMapping("/{partnerId}")
	public ApiResponse<BusinessPartnerResponse> update(
			@PathVariable Long partnerId,
			@Valid @RequestBody BusinessPartnerUpdateRequest request) {
		return ApiResponse.ok(service.update(partnerId, request));
	}
}
