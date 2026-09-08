package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import org.springframework.stereotype.Component;

@Component
public class DivideStrategy implements StructureChangeStrategy {

	@Override public String supports() { return WorkTypeDefinition.DIVIDE.name(); }
	@Override public String workLabel() { return "분주"; }
	@Override public OrchidGroupLineageRelationType lineageType() {
		return OrchidGroupLineageRelationType.SPLIT_TO;
	}

	@Override
	public void validate(StructureChangeCommand request) {
		// 분주는 최종 결과 수량만 입력하며, 원본 투입 수량보다 커질 수 있다.
	}
}
