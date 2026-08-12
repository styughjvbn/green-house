package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.work.dto.operation.WorkExecutionLocationResponse;
import java.util.Collection;
import java.util.Map;

public interface WorkExecutionReferenceGateway {

	Map<Long, String> varietyNames(Collection<Long> orchidGroupIds);

	Map<Long, WorkExecutionLocationResponse> locations(Collection<Long> bedZoneIds);
}
