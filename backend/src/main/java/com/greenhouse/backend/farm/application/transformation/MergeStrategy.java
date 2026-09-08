package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import org.springframework.stereotype.Component;

@Component
public class MergeStrategy implements StructureChangeStrategy {

	@Override
	public String supports() {
		return WorkTypeDefinition.MERGE.name();
	}

	@Override
	public String workLabel() {
		return "합식";
	}

	@Override
	public OrchidGroupLineageRelationType lineageType() {
		return OrchidGroupLineageRelationType.MERGED_TO;
	}

}
