package com.greenhouse.backend.farm.transformation.application;

import com.greenhouse.backend.farm.transformation.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import org.springframework.stereotype.Component;

@Component
public class RepotStrategy implements StructureChangeStrategy {

  @Override
  public String supports() {
    return WorkTypeDefinition.REPOT.name();
  }

  @Override
  public String workLabel() {
    return "분갈이";
  }

  @Override
  public OrchidGroupLineageRelationType lineageType() {
    return OrchidGroupLineageRelationType.REPOTTED_TO;
  }

  @Override
  public void validate(StructureChangeCommand request) {
    // 분갈이 중 촉을 나누는 경우 결과 수량이 투입 수량보다 커질 수 있다.
  }
}
