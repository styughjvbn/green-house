package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOperationTargetRepository extends JpaRepository<WorkOperationTarget, Long> {

	@Query("select distinct target.orchidGroupId from WorkOperationTarget target "
			+ "where target.orchidGroupId is not null order by target.orchidGroupId")
	List<Long> findDistinctOrchidGroupIds();

	@Query("""
			select count(target) from WorkOperationTarget target
			where target.orchidGroupId in :orchidGroupIds
			  and target.workOperation.id <> :workOperationId
			  and target.workOperation.status not in :ignoredStatuses
			  and target.excludedAt is null
			""")
	long countActiveOtherOperations(Collection<Long> orchidGroupIds, Long workOperationId,
			Collection<WorkOperationStatus> ignoredStatuses);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(Long workOperationId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
			Collection<Long> workOperationIds);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(Collection<Long> workOperationIds);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
			Long orchidGroupId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
			Collection<Long> orchidGroupIds);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdInAndOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
			Collection<Long> workOperationIds, Collection<Long> orchidGroupIds);

}
