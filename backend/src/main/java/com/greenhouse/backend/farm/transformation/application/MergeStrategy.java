package com.greenhouse.backend.farm.transformation.application;

import com.greenhouse.backend.farm.transformation.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
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
