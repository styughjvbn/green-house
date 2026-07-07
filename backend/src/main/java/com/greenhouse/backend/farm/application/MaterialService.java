package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.Material;
import com.greenhouse.backend.farm.dto.MaterialCreateRequest;
import com.greenhouse.backend.farm.dto.MaterialPageResponse;
import com.greenhouse.backend.farm.dto.MaterialResponse;
import com.greenhouse.backend.farm.dto.MaterialUpdateRequest;
import com.greenhouse.backend.farm.repository.MaterialRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MaterialService {

	private final MaterialRepository materialRepository;

	@Transactional(readOnly = true)
	public MaterialPageResponse getMaterials(
			String keyword,
			String category,
			String manufacturer,
			Boolean active,
			int page,
			int size) {
		validatePageRequest(page, size);
		return MaterialPageResponse.from(materialRepository.search(
				normalize(keyword) == null ? "" : normalize(keyword),
				normalize(category) == null ? "" : normalize(category),
				normalize(manufacturer) == null ? "" : normalize(manufacturer),
				active,
				PageRequest.of(page, size, Sort.by(
						Sort.Order.desc("active"),
						Sort.Order.asc("category"),
						Sort.Order.asc("name"))))
				.map(MaterialResponse::from));
	}

	@Transactional(readOnly = true)
	public MaterialResponse getMaterial(Long materialId) {
		return MaterialResponse.from(findMaterial(materialId));
	}

	public MaterialResponse create(MaterialCreateRequest request) {
		Material material = materialRepository.save(new Material(
				nextCode(),
				normalizeRequired(request.category()),
				normalizeRequired(request.name()),
				normalize(request.manufacturer()),
				normalize(request.specification()),
				normalize(request.stockQuantity()),
				normalize(request.storageLocation()),
				normalize(request.usage()),
				true));
		return MaterialResponse.from(material);
	}

	public MaterialResponse update(Long materialId, MaterialUpdateRequest request) {
		Material material = findMaterial(materialId);
		material.update(
				normalizeRequired(request.category()),
				normalizeRequired(request.name()),
				normalize(request.manufacturer()),
				normalize(request.specification()),
				normalize(request.stockQuantity()),
				normalize(request.storageLocation()),
				normalize(request.usage()));
		return MaterialResponse.from(material);
	}

	public MaterialResponse deactivate(Long materialId) {
		Material material = findMaterial(materialId);
		material.deactivate();
		return MaterialResponse.from(material);
	}

	public void delete(Long materialId) {
		materialRepository.delete(findMaterial(materialId));
	}

	private Material findMaterial(Long materialId) {
		return materialRepository.findById(materialId)
				.orElseThrow(() -> new NotFoundException("자재를 찾을 수 없습니다."));
	}

	private String nextCode() {
		long next = materialRepository.findTopByOrderByIdDesc()
				.map(Material::getId)
				.orElse(0L) + 1;
		return "MAT-%04d".formatted(next);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return normalized;
	}

	private void validatePageRequest(int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		}
		if (size < 1 || size > 100) {
			throw new IllegalArgumentException("페이지 크기는 1~100이어야 합니다.");
		}
	}
}
