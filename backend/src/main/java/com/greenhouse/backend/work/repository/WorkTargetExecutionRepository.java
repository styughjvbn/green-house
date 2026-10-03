package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkTargetExecutionRepository
    extends JpaRepository<WorkTargetExecution, Long>, WorkTargetExecutionRepositoryCustom {

  @Query(
      "select distinct e.target.workOperation.id from WorkTargetExecution e "
          + "where e.target.inboundRecordId = :inboundRecordId order by e.target.workOperation.id")
  List<Long> findOperationIdsForInbound(Long inboundRecordId);

  @Query(
      "select distinct e.target.workOperation.id from WorkTargetExecution e "
          + "where e.target.inboundRecordId = :inboundRecordId and e.effectAppliedAt is not null "
          + "and e.target.workOperation.workType.code = :code and e.target.workOperation.status in :statuses "
          + "order by e.target.workOperation.id")
  List<Long> findAppliedOperationIdsForInbound(
      Long inboundRecordId, String code, Collection<WorkOperationStatus> statuses);

  @Query(
      "select distinct e.target.inboundRecordId from WorkTargetExecution e "
          + "where e.target.inboundRecordId in :inboundIds and e.effectAppliedAt is not null "
          + "and e.target.workOperation.workType.code = :code and e.target.workOperation.status in :statuses")
  List<Long> findInboundIdsWithAppliedOperation(
      Collection<Long> inboundIds, String code, Collection<WorkOperationStatus> statuses);

  @Query(
      """
			select new com.greenhouse.backend.work.repository.WorkExecutionReconciliationRow(
				execution.id,
				target.orchidGroupId,
				target.quantitySnapshot,
				execution.processedQuantity,
				execution.status,
				execution.effectAppliedAt)
			from WorkTargetExecution execution
			join execution.target target
			where target.orchidGroupId is not null
			order by execution.id
			""")
  List<WorkExecutionReconciliationRow> findReconciliationRows();
}
