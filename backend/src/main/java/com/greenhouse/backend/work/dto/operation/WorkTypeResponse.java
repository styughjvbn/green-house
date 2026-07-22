package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;

public record WorkTypeResponse(
		Long id,
		String code,
		String name,
		WorkTypeTemplate template,
		boolean defaultType,
		boolean systemType,
		boolean active,
		int sortOrder) {

	public static WorkTypeResponse from(WorkType workType) {
		return new WorkTypeResponse(
				workType.getId(),
				workType.getCode(),
				workType.getName(),
				workType.getTemplate(),
				workType.isDefaultType(),
				workType.isSystemType(),
				workType.isActive(),
				workType.getSortOrder());
	}
}
