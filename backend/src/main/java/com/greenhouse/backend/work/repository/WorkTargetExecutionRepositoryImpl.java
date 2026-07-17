package com.greenhouse.backend.work.repository;

import static com.greenhouse.backend.work.domain.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.domain.QWorkOperationTarget.workOperationTarget;
import static com.greenhouse.backend.work.domain.QWorkTargetExecution.workTargetExecution;
import static com.greenhouse.backend.work.domain.QWorkType.workType;

import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.WorkType;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public class WorkTargetExecutionRepositoryImpl implements WorkTargetExecutionRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	public WorkTargetExecutionRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public List<WorkTargetExecution> findActiveInboundPottingForUpdate(Long inboundRecordId) {
		return executionWithOperationAndWorkType()
				.where(
						workOperationTarget.inboundRecordId.eq(inboundRecordId),
						workType.code.eq(WorkType.POTTING_CODE),
						workOperation.status.in(
								WorkOperationStatus.PLANNED,
								WorkOperationStatus.IN_PROGRESS,
								WorkOperationStatus.PAUSED),
						workTargetExecution.status.in(
								WorkTargetExecutionStatus.PENDING,
								WorkTargetExecutionStatus.IN_PROGRESS))
				.orderBy(workOperation.plannedStartDate.asc(), workOperation.id.asc())
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.fetch();
	}

	@Override
	public List<WorkTargetExecution> findByTargetWorkOperationIdOrderByIdAsc(Long workOperationId) {
		return queryFactory
				.selectFrom(workTargetExecution)
				.join(workTargetExecution.target, workOperationTarget).fetchJoin()
				.where(workOperationTarget.workOperation.id.eq(workOperationId))
				.orderBy(workTargetExecution.id.asc())
				.fetch();
	}

	@Override
	public List<WorkTargetExecution> findForUpdateByTargetWorkOperationIdOrderByIdAsc(
			Long workOperationId) {
		return executionWithOperationAndWorkType()
				.where(workOperation.id.eq(workOperationId))
				.orderBy(workTargetExecution.id.asc())
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.fetch();
	}

	@Override
	public Optional<WorkTargetExecution> findByTargetIdAndTargetWorkOperationId(
			Long targetId,
			Long workOperationId) {
		return Optional.ofNullable(executionWithOperation()
				.where(
						workOperationTarget.id.eq(targetId),
						workOperation.id.eq(workOperationId))
				.fetchOne());
	}

	@Override
	public Optional<WorkTargetExecution> findForUpdateByTargetIdAndTargetWorkOperationId(
			Long targetId,
			Long workOperationId) {
		return Optional.ofNullable(executionWithOperationAndWorkType()
				.where(
						workOperationTarget.id.eq(targetId),
						workOperation.id.eq(workOperationId))
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.fetchOne());
	}

	private JPAQuery<WorkTargetExecution> executionWithOperation() {
		return queryFactory
				.selectFrom(workTargetExecution)
				.join(workTargetExecution.target, workOperationTarget).fetchJoin()
				.join(workOperationTarget.workOperation, workOperation).fetchJoin();
	}

	private JPAQuery<WorkTargetExecution> executionWithOperationAndWorkType() {
		return executionWithOperation()
				.join(workOperation.workType, workType).fetchJoin();
	}
}
