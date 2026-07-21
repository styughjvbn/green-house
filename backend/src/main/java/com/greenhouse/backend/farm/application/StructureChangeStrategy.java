package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.dto.StructureChangeExecutionRequest;

public interface StructureChangeStrategy {

	String supports();

	String workLabel();

	OrchidGroupLineageRelationType lineageType();

	default void validate(StructureChangeExecutionRequest request) {
	}
}
