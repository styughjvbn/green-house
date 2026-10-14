package com.greenhouse.backend.work.operation.repository;

import com.greenhouse.backend.work.operation.domain.WorkCreationBatchMembership;
import com.greenhouse.backend.work.operation.domain.WorkCreationBatchMembershipId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkCreationBatchMembershipRepository
    extends JpaRepository<WorkCreationBatchMembership, WorkCreationBatchMembershipId> {

  @Query(
      "select membership.id.receiptKey from WorkCreationBatchMembership membership "
          + "where membership.id.operationId = :operationId order by membership.id.receiptKey")
  List<String> findReceiptKeysByOperationId(@Param("operationId") Long operationId);

  @Query(
      "select membership from WorkCreationBatchMembership membership "
          + "where membership.id.operationId in :operationIds order by membership.id.receiptKey, membership.id.operationId")
  List<WorkCreationBatchMembership> findByOperationIdIn(
      @Param("operationIds") Collection<Long> operationIds);
}
