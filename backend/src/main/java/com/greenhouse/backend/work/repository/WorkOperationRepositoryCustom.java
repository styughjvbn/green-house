package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkOperationSearchView;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkOperationRepositoryCustom {

	Optional<WorkOperation> findWithWorkTypeById(Long id);

	List<WorkOperation> findWithWorkTypeByIdIn(Collection<Long> ids);

	Optional<WorkOperation> findByRequestKey(String requestKey);

	Page<WorkOperation> search(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			LocalDateTime todayStartedAt,
			WorkSourceScopeType sourceScopeType,
			Long sourceScopeId,
			String keyword,
			Pageable pageable);

	List<WorkOperation> searchAll(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			LocalDateTime todayStartedAt);
}
