package com.greenhouse.backend.farm.controller.material;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.farm.application.material.MaterialService;
import com.greenhouse.backend.farm.dto.material.MaterialCreateRequest;
import com.greenhouse.backend.farm.dto.material.MaterialResponse;
import com.greenhouse.backend.farm.dto.material.MaterialUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

	private final MaterialService materialService;

	@GetMapping
	public ApiResponse<PageResponse<MaterialResponse>> getMaterials(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String manufacturer,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(materialService.getMaterials(keyword, category, manufacturer, active, page, size));
	}

	@GetMapping("/{materialId}")
	public ApiResponse<MaterialResponse> getMaterial(@PathVariable Long materialId) {
		return ApiResponse.ok(materialService.getMaterial(materialId));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MaterialResponse> create(@Valid @RequestBody MaterialCreateRequest request) {
		return ApiResponse.ok(materialService.create(request));
	}

	@PatchMapping("/{materialId}")
	public ApiResponse<MaterialResponse> update(
			@PathVariable Long materialId,
			@Valid @RequestBody MaterialUpdateRequest request) {
		return ApiResponse.ok(materialService.update(materialId, request));
	}

	@PatchMapping("/{materialId}/deactivate")
	public ApiResponse<MaterialResponse> deactivate(@PathVariable Long materialId) {
		return ApiResponse.ok(materialService.deactivate(materialId));
	}

	@DeleteMapping("/{materialId}")
	public ApiResponse<Void> delete(@PathVariable Long materialId) {
		materialService.delete(materialId);
		return ApiResponse.ok(null);
	}
}
