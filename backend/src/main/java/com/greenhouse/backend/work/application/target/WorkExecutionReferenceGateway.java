package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.work.application.operation.WorkExecutionLocation;
import java.util.Collection;
import java.util.Map;

public interface WorkExecutionReferenceGateway {

	Map<Long, String> varietyNames(Collection<Long> orchidGroupIds);

	Map<Long, WorkExecutionLocation> locations(Collection<Long> bedZoneIds);
}
