package com.greenhouse.backend.work.operation.repository;

import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkOperationSearchView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

  Page<WorkOperation> search(
      LocalDate fromDate,
      LocalDate toDate,
      WorkOperationStatus status,
      WorkOperationSearchView view,
      LocalDateTime todayStartedAt,
      WorkSourceScopeType sourceScopeType,
      Long sourceScopeId,
      String keyword,
      Boolean hasCorrections,
      Pageable pageable);

  List<WorkOperation> searchAll(
      LocalDate fromDate,
      LocalDate toDate,
      WorkOperationStatus status,
      WorkOperationSearchView view,
      LocalDateTime todayStartedAt,
      Boolean hasCorrections);

  List<WorkOperation> searchAll(
      LocalDate fromDate,
      LocalDate toDate,
      WorkOperationStatus status,
      WorkOperationSearchView view,
      LocalDateTime todayStartedAt);
}
