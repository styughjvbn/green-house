package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkCommandReceiptMembership;
import com.greenhouse.backend.work.domain.operation.WorkCommandReceiptMembershipId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkCommandReceiptMembershipRepository
    extends JpaRepository<WorkCommandReceiptMembership, WorkCommandReceiptMembershipId> {

  @Query(
      "select membership.id.receiptKey from WorkCommandReceiptMembership membership "
          + "where membership.id.operationId = :operationId order by membership.id.receiptKey")
  List<String> findReceiptKeysByOperationId(@Param("operationId") Long operationId);

  @Query(
      "select membership from WorkCommandReceiptMembership membership "
          + "where membership.id.operationId in :operationIds order by membership.id.receiptKey, membership.id.operationId")
  List<WorkCommandReceiptMembership> findByOperationIdIn(
      @Param("operationIds") Collection<Long> operationIds);
}
