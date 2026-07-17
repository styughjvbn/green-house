package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkOperationSearchView;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkOperationRepositoryCustom {

	Optional<WorkOperation> findWithWorkTypeById(Long id);

	Optional<WorkOperation> findByRequestKey(String requestKey);

	List<WorkOperation> search(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			LocalDateTime todayStartedAt,
			WorkSourceScopeType scopeType,
			Long scopeId);
}
