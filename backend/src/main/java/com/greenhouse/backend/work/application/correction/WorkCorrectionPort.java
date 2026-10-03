package com.greenhouse.backend.work.application.correction;

import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import java.util.function.Supplier;

public interface WorkCorrectionPort {

	WorkExecutionResult correct(Long originalOperationId, Supplier<Long> correctionId,
			WorkCorrectionCommand request);

}
