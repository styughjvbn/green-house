package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkTargetExecution;
import java.util.List;
import java.util.Optional;

public interface WorkTargetExecutionRepositoryCustom {

	List<WorkTargetExecution> findActiveInboundPottingForUpdate(Long inboundRecordId);

	List<WorkTargetExecution> findForUpdateByTargetInboundRecordIdOrderByIdAsc(Long inboundRecordId);

	List<WorkTargetExecution> findByTargetWorkOperationIdOrderByIdAsc(Long workOperationId);

	List<WorkTargetExecution> findForUpdateByTargetWorkOperationIdOrderByIdAsc(Long workOperationId);

	Optional<WorkTargetExecution> findByTargetIdAndTargetWorkOperationId(
			Long targetId,
			Long workOperationId);

	Optional<WorkTargetExecution> findForUpdateByTargetIdAndTargetWorkOperationId(
			Long targetId,
			Long workOperationId);
}
