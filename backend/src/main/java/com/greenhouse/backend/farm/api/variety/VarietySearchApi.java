package com.greenhouse.backend.farm.api.variety;

import java.util.List;

public interface VarietySearchApi {
  /** 품종명·별명 일치도순 후보. 속은 제외 조건이 아닌 동순위 후보 정렬 힌트다. */
  List<Option> searchOptions(String keyword, String genus, int limit);

  record Option(Long id, String name, String genus, boolean active) {}
}
