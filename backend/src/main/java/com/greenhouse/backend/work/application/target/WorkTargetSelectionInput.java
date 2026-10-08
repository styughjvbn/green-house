package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import java.util.List;

public interface WorkTargetSelectionInput {

  WorkSourceScopeType sourceScopeType();

  Long sourceScopeId();

  String sourceDerivedGroupKey();

  List<Long> sourceOrchidGroupIds();
}
