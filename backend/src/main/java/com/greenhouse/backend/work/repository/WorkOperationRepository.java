package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOperationRepository extends JpaRepository<WorkOperation, Long> {

	@EntityGraph(attributePaths = "workType")
	Optional<WorkOperation> findWithWorkTypeById(Long id);

	@EntityGraph(attributePaths = "workType")
	Optional<WorkOperation> findByRequestKey(String requestKey);

	@Query("""
			select operation
			from WorkOperation operation
			join fetch operation.workType
			where (:status is null or operation.status = :status)
			  and (:scopeType is null or operation.sourceScopeType = :scopeType)
			  and (:scopeId is null or operation.sourceScopeId = :scopeId)
			  and (:fromDate is null or coalesce(operation.plannedEndDate, operation.plannedStartDate) >= :fromDate)
			  and (:toDate is null or operation.plannedStartDate <= :toDate)
			order by operation.plannedStartDate desc, operation.id desc
			""")
	List<WorkOperation> search(
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate,
			@Param("status") WorkOperationStatus status,
			@Param("scopeType") WorkSourceScopeType scopeType,
			@Param("scopeId") Long scopeId);
}
