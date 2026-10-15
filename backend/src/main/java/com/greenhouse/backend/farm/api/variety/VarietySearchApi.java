package com.greenhouse.backend.farm.api.variety;

import java.util.List;

public interface VarietySearchApi {
  List<Option> searchOptions(String keyword, String genus, int limit);

  record Option(Long id, String name, String genus, boolean active) {}
}
