package com.greenhouse.backend.farm.transformation.application;

import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StructureChangeStrategyRegistry {

  private final Map<String, StructureChangeStrategy> strategies = new HashMap<>();

  public StructureChangeStrategyRegistry(List<StructureChangeStrategy> strategies) {
    for (StructureChangeStrategy strategy : strategies) {
      if (this.strategies.put(strategy.supports(), strategy) != null) {
        throw new IllegalStateException("구조 변경 Strategy 코드는 중복될 수 없습니다.");
      }
    }
  }

  @PostConstruct
  void validateDefinitions() {
    for (WorkTypeDefinition definition : WorkTypeDefinition.values()) {
      if (definition.supportsStructureExecution()) {
        var strategy = strategies.get(definition.name());
        if (strategy == null) {
          throw new IllegalStateException("작업 정의에 필요한 구조 변경 Strategy가 없습니다: " + definition.name());
        }
        if (strategy.lineageType() == null) {
          throw new IllegalStateException("구조 변경 Strategy의 계보 관계가 없습니다: " + definition.name());
        }
      }
    }
  }

  public StructureChangeStrategy get(String workTypeCode) {
    StructureChangeStrategy strategy = strategies.get(workTypeCode);
    if (strategy == null) {
      throw new IllegalArgumentException("지원하지 않는 구조 변경 작업입니다: " + workTypeCode);
    }
    return strategy;
  }
}
