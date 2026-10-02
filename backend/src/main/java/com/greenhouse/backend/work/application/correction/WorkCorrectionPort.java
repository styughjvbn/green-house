package com.greenhouse.backend.work.application.correction;

import com.greenhouse.backend.work.application.effect.WorkExecutionResult;

public interface WorkCorrectionPort {

	WorkExecutionResult correct(Long originalOperationId, java.util.function.Supplier<Long> correctionId,
			WorkCorrectionCommand request);

}
