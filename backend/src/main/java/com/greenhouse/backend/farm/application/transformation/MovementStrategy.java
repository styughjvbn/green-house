package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.api.effect.MovementQuantityAllocator;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MovementStrategy implements StructureChangeStrategy {

  @Override
  public String supports() {
    return WorkTypeDefinition.MOVEMENT.name();
  }

  @Override
  public String workLabel() {
    return "자리 이동";
  }

  @Override
  public OrchidGroupLineageRelationType lineageType() {
    return OrchidGroupLineageRelationType.MOVED_TO;
  }

  @Override
  public boolean preservesSourceAttributes() {
    return true;
  }

  @Override
  public Map<Long, Integer> transformedQuantities(StructureChangeCommand request) {
    return MovementQuantityAllocator.allocateMovedBySource(request);
  }

  @Override
  public void validate(StructureChangeCommand request) {
    StructureChangeStrategy.super.validate(request);
    MovementQuantityAllocator.allocateMovedBySource(request);
  }
}
