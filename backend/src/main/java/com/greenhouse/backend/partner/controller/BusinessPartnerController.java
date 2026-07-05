package com.greenhouse.backend.partner.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.partner.application.BusinessPartnerService;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.dto.BusinessPartnerCreateRequest;
import com.greenhouse.backend.partner.dto.BusinessPartnerResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/business-partners")
public class BusinessPartnerController {
	private final BusinessPartnerService service;

	public BusinessPartnerController(BusinessPartnerService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<List<BusinessPartnerResponse>> getPartners(
		@RequestParam(required = false) String keyword,
		@RequestParam(required = false) PartnerType partnerType
	) {
		return ApiResponse.ok(service.getPartners(keyword, partnerType));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<BusinessPartnerResponse> create(@Valid @RequestBody BusinessPartnerCreateRequest request) {
		return ApiResponse.ok(service.create(request));
	}
}
