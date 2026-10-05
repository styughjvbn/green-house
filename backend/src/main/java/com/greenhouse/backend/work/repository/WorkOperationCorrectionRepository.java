package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOperationCorrectionRepository
    extends JpaRepository<WorkOperationCorrection, Long> {

  List<WorkOperationCorrection> findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(
      Long originalWorkOperationId);

  @EntityGraph(attributePaths = {"originalWorkOperation", "originalWorkOperation.workType"})
  List<WorkOperationCorrection> findByMutationIdIn(Collection<Long> mutationIds);

  @EntityGraph(attributePaths = {"originalWorkOperation", "originalWorkOperation.workType"})
  @Query("select c from WorkOperationCorrection c where c.mutationId in :mutationIds order by c.id")
  List<WorkOperationCorrection> findByMutationIdIn(Collection<Long> mutationIds, Pageable pageable);

  List<WorkOperationCorrection> findByOriginalWorkOperationIdIn(Collection<Long> operationIds);

  @Query(
      "select c from WorkOperationCorrection c where c.originalWorkOperation.id in :operationIds order by c.id")
  List<WorkOperationCorrection> findByOriginalWorkOperationIdIn(
      Collection<Long> operationIds, Pageable pageable);

  @Query(
      "select c.originalWorkOperation.id as operationId, count(c) as total from WorkOperationCorrection c where c.originalWorkOperation.id in :ids group by c.originalWorkOperation.id")
  List<Count> countByOriginalIds(Collection<Long> ids);

  @Query("select c from WorkOperationCorrection c where c.id > :afterId order by c.id")
  List<WorkOperationCorrection> findAfterId(Long afterId, Pageable pageable);

  interface Count {

    Long getOperationId();

    long getTotal();
  }
}
