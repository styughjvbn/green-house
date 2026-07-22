package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import org.springframework.stereotype.Component;

@Component
public class DivideStrategy implements StructureChangeStrategy {

	@Override public String supports() { return WorkType.DIVIDE_CODE; }
	@Override public String workLabel() { return "분주"; }
	@Override public OrchidGroupLineageRelationType lineageType() {
		return OrchidGroupLineageRelationType.SPLIT_TO;
	}
}
