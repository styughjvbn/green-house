package com.greenhouse.backend.work.application.correction;

import com.greenhouse.backend.work.application.effect.WorkMutationLink;

/** Prepare and apply the same command within the caller's transaction, retaining Farm locks. */
public interface WorkCorrectionPort {

  WorkCorrectionPlan prepare(Long originalOperationId, WorkCorrectionCommand request);

  WorkMutationLink apply(Long correctionId, WorkCorrectionCommand request, WorkCorrectionPlan plan);
}
