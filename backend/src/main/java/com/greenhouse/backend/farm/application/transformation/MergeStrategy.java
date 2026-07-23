package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import org.springframework.stereotype.Component;

@Component
public class MergeStrategy implements StructureChangeStrategy {

	@Override public String supports() { return WorkType.MERGE_CODE; }
	@Override public String workLabel() { return "합식"; }
	@Override public OrchidGroupLineageRelationType lineageType() {
		return OrchidGroupLineageRelationType.MERGED_TO;
	}

	@Override
	public void validate(StructureChangeExecutionRequest request) {
		if (request.sources().size() < 2) {
			throw new IllegalArgumentException("합식은 원본 난 묶음이 두 개 이상 필요합니다.");
		}
	}
}
