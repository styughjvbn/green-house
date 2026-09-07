package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import org.springframework.stereotype.Component;

@Component
public class RepotStrategy implements StructureChangeStrategy {

	@Override public String supports() { return WorkTypeDefinition.REPOT.name(); }
	@Override public String workLabel() { return "분갈이"; }
	@Override public OrchidGroupLineageRelationType lineageType() {
		return OrchidGroupLineageRelationType.REPOTTED_TO;
	}
}
