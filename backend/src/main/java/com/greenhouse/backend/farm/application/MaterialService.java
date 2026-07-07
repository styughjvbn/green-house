package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.Material;
import com.greenhouse.backend.farm.dto.MaterialCreateRequest;
import com.greenhouse.backend.farm.dto.MaterialResponse;
import com.greenhouse.backend.farm.dto.MaterialUpdateRequest;
import com.greenhouse.backend.farm.repository.MaterialRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MaterialService {

	private final MaterialRepository materialRepository;

	public MaterialService(MaterialRepository materialRepository) {
		this.materialRepository = materialRepository;
	}

	@Transactional(readOnly = true)
	public List<MaterialResponse> getMaterials(
		String keyword,
		String category,
		String manufacturer,
		Boolean active
	) {
		return materialRepository.search(
				normalize(keyword) == null ? "" : normalize(keyword),
				normalize(category) == null ? "" : normalize(category),
				normalize(manufacturer) == null ? "" : normalize(manufacturer),
				active
			).stream()
			.map(MaterialResponse::from)
			.toList();
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
			true
		));
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
			normalize(request.usage())
		);
		return MaterialResponse.from(material);
	}

	public MaterialResponse deactivate(Long materialId) {
		Material material = findMaterial(materialId);
		material.deactivate();
		return MaterialResponse.from(material);
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
}
