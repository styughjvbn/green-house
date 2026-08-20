package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOperationTargetRepository extends JpaRepository<WorkOperationTarget, Long> {

	@Query("select distinct target.orchidGroupId from WorkOperationTarget target "
			+ "where target.orchidGroupId is not null order by target.orchidGroupId")
	List<Long> findDistinctOrchidGroupIds();

	long countByOrchidGroupIdInAndWorkOperationIdNot(Collection<Long> orchidGroupIds, Long workOperationId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(Long workOperationId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
			Collection<Long> workOperationIds);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
			Long orchidGroupId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
			Collection<Long> orchidGroupIds);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdInAndOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
			Collection<Long> workOperationIds,
			Collection<Long> orchidGroupIds);
}
