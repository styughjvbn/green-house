package com.greenhouse.backend.work.operation.web.dto;

import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;

public enum WorkHistoryScopeType {
  HOUSE,
  PHYSICAL_BED,
  BED_ZONE,
  ORCHID_GROUP;

  public WorkSourceScopeType toSourceScopeType() {
    return WorkSourceScopeType.valueOf(name());
  }
}
