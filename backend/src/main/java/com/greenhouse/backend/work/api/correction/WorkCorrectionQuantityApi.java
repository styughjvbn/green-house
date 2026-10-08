package com.greenhouse.backend.work.api.correction;

import java.util.List;

/** Saved Work quantity facts and validation required before Farm applies a correction. */
public interface WorkCorrectionQuantityApi {

  List<WorkQuantityBalance> context(Long workId);

  List<WorkQuantityBalanceChange> validate(Long workId, WorkCorrectionCommand request);

  void requireEnabled();
}
