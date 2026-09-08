package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.domain.operation.WorkRegistrationMode;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.List;

public record WorkTypeResponse(Long id, String code, String name, WorkTypeTemplate template, boolean defaultType,
		boolean systemType, boolean active, int sortOrder, boolean settingsEditable,
		List<WorkRegistrationMode> registrationModes, WorkTypeWorkflow workflow, WorkTargetReferenceType targetSource) {

	public static WorkTypeResponse from(WorkType workType) {
		return new WorkTypeResponse(workType.getId(), workType.getCode(), workType.getName(), workType.getTemplate(),
				workType.isDefaultType(), workType.isSystemType(), workType.isActive(), workType.getSortOrder(),
				workType.isSettingsEditable(), workType.registrationModes(), workType.workflow(),
				workType.registrationTargetSource());
	}
}
