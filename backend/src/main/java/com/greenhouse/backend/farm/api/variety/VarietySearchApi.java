package com.greenhouse.backend.farm.api.variety;

import java.util.List;
import java.util.Set;

public interface VarietySearchApi {
  /** 품종명·별명 일치도순 후보. 속은 제외 조건이 아닌 동순위 후보 정렬 힌트다. */
  List<Option> searchOptions(String keyword, String genus, int limit);

  /** 식별자 검증용 일괄 조회. 비활성 품종도 포함하며 최대 500개만 허용한다. */
  List<Option> findOptionsByIds(Set<Long> ids);

  /** 세션 초기 참고용 전체 이름. 활성 품종 중 수량과 무관하게 연결된 난 묶음이 있는 이름을 중복 제거한다. */
  List<String> findActiveNamesWithLinkedGroups();

  record Option(Long id, String name, String genus, boolean active) {}
}
