package com.greenhouse.backend.work.spi.target;

import com.greenhouse.backend.work.api.target.WorkTargetSelection;
import java.util.List;

public interface WorkTargetResolver {

  List<ResolvedWorkTarget> resolve(WorkTargetSelection selection);

  ResolvedWorkTarget getCurrent(Long orchidGroupId);

  void lockAndValidateActive(List<Long> orchidGroupIds);
}
