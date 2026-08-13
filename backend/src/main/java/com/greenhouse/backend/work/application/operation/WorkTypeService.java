package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.dto.operation.WorkTypeCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkTypeMetadataResponse;
import com.greenhouse.backend.work.dto.operation.WorkTypeReorderRequest;
import com.greenhouse.backend.work.dto.operation.WorkTypeResponse;
import com.greenhouse.backend.work.dto.operation.WorkTypeUpdateRequest;
import com.greenhouse.backend.work.repository.WorkTypeRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkTypeService {

	private final WorkTypeRepository workTypeRepository;

	@Transactional(readOnly = true)
	public List<WorkTypeResponse> getWorkTypes(boolean includeInactive) {
		List<WorkType> workTypes = includeInactive
				? workTypeRepository.findAllByOrderBySortOrderAscIdAsc()
				: workTypeRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc();
		return workTypes.stream()
				.map(WorkTypeResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public WorkTypeMetadataResponse getMetadata() {
		return new WorkTypeMetadataResponse(Arrays.stream(WorkTypeTemplate.values())
				.filter(WorkTypeTemplate::isCustomTypeAllowed)
				.toList());
	}

	public WorkTypeResponse create(WorkTypeCreateRequest request) {
		validateCustomTemplate(request.template());
		String name = normalizeRequired(request.name());
		validateUniqueName(name, null);
		WorkType workType = new WorkType(
				"CUSTOM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
				name,
				request.template(),
				false,
				false,
				true,
				nextSortOrder());
		return WorkTypeResponse.from(workTypeRepository.save(workType));
	}

	public WorkTypeResponse update(Long id, WorkTypeUpdateRequest request) {
		WorkType workType = getById(id);
		if (!workType.isSettingsEditable()) {
			throw new IllegalArgumentException("Work type cannot be changed.");
		}
		validateCustomTemplate(request.template());
		String name = normalizeRequired(request.name());
		validateUniqueName(name, id);
		workType.update(name, request.template(), request.active());
		return WorkTypeResponse.from(workType);
	}

	public List<WorkTypeResponse> reorder(WorkTypeReorderRequest request) {
		List<WorkType> workTypes = workTypeRepository.findAllByOrderBySortOrderAscIdAsc();
		for (int index = 0; index < request.orderedIds().size(); index++) {
			Long id = request.orderedIds().get(index);
			WorkType workType = workTypes.stream()
					.filter(candidate -> candidate.getId().equals(id))
					.findFirst()
					.orElseThrow(() -> new NotFoundException("Work type not found."));
			workType.changeSortOrder(index + 1);
		}
		return workTypeRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.map(WorkTypeResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public WorkType getActiveForCreate(Long workTypeId) {
		WorkType workType = getById(workTypeId);
		if (!workType.isManualCreateAllowed()) {
			throw new IllegalArgumentException("Work type is not available for manual records.");
		}
		return workType;
	}

	@Transactional(readOnly = true)
	public WorkType getActiveForPlan(Long workTypeId) {
		WorkType workType = getById(workTypeId);
		if (workType.isPeriodOperationAllowed()) {
			return workType;
		}
		throw new IllegalArgumentException("기간 작업으로 계획할 수 없는 작업 유형입니다.");
	}

	@Transactional(readOnly = true)
	public WorkType getMovementType() {
		return workTypeRepository.findByCode(WorkType.MOVEMENT_CODE)
				.orElseThrow(() -> new NotFoundException("Movement work type not found."));
	}

	@Transactional(readOnly = true)
	public WorkType getByCode(String code) {
		return workTypeRepository.findByCode(code)
				.orElseThrow(() -> new NotFoundException("Work type not found."));
	}

	private WorkType getById(Long id) {
		if (id == null) {
			throw new IllegalArgumentException("Work type id is required.");
		}
		return workTypeRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Work type not found."));
	}

	private int nextSortOrder() {
		return workTypeRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.mapToInt(WorkType::getSortOrder)
				.max()
				.orElse(0) + 1;
	}

	private void validateCustomTemplate(WorkTypeTemplate template) {
		if (!template.isCustomTypeAllowed()) {
			throw new IllegalArgumentException("사용자 작업 유형에서 지원하지 않는 템플릿입니다.");
		}
	}

	private void validateUniqueName(String name, Long id) {
		if (workTypeRepository.existsByNameAndIdNot(name, id == null ? -1L : id)) {
			throw new IllegalArgumentException("Work type name already exists.");
		}
	}

	private String normalizeRequired(String value) {
		String normalized = value == null ? null : value.trim();
		if (normalized == null || normalized.isEmpty()) {
			throw new IllegalArgumentException("Required text value is empty.");
		}
		return normalized;
	}
}
