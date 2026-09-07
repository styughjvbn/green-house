package com.greenhouse.backend.work.repository;

import static com.greenhouse.backend.work.domain.operation.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.domain.operation.QWorkType.workType;
import static com.greenhouse.backend.work.domain.target.QWorkOperationTarget.workOperationTarget;
import static com.greenhouse.backend.work.domain.target.QWorkTargetExecution.workTargetExecution;

import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WorkTargetExecutionRepositoryImpl implements WorkTargetExecutionRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<WorkTargetExecution> findActiveInboundPottingForUpdate(Long inboundRecordId) {
		return findActiveInboundPottingForUpdate(List.of(inboundRecordId));
	}

	@Override
	public List<WorkTargetExecution> findActiveInboundPottingForUpdate(Collection<Long> inboundRecordIds) {
		return executionWithOperationAndWorkType()
				.where(
						workOperationTarget.inboundRecordId.in(inboundRecordIds),
						workType.code.eq(WorkTypeDefinition.POTTING.name()),
						workOperation.status.in(
								WorkOperationStatus.PLANNED,
								WorkOperationStatus.IN_PROGRESS,
								WorkOperationStatus.PAUSED),
						workTargetExecution.status.in(
								WorkTargetExecutionStatus.PENDING,
								WorkTargetExecutionStatus.IN_PROGRESS))
				.orderBy(workOperationTarget.inboundRecordId.asc(), workOperation.plannedStartDate.asc(), workOperation.id.asc())
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.fetch();
	}

	@Override
	public List<WorkTargetExecution> findForUpdateByTargetInboundRecordIdOrderByIdAsc(Long inboundRecordId) {
		return executionWithOperationAndWorkType()
				.where(workOperationTarget.inboundRecordId.eq(inboundRecordId))
				.orderBy(workTargetExecution.id.asc())
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
	public List<WorkTargetExecution> findByTargetWorkOperationIdInOrderByIdAsc(
			Collection<Long> workOperationIds) {
		return queryFactory
				.selectFrom(workTargetExecution)
				.join(workTargetExecution.target, workOperationTarget).fetchJoin()
				.where(workOperationTarget.workOperation.id.in(workOperationIds))
				.orderBy(workOperationTarget.workOperation.id.asc(), workTargetExecution.id.asc())
				.fetch();
	}

	@Override
	public List<WorkOperationProgressProjection> findProgressByWorkOperationIdIn(
			Collection<Long> workOperationIds) {
		if (workOperationIds.isEmpty()) {
			return List.of();
		}
		NumberExpression<Integer> pending = countStatus(WorkTargetExecutionStatus.PENDING);
		NumberExpression<Integer> inProgress = countStatus(WorkTargetExecutionStatus.IN_PROGRESS);
		NumberExpression<Integer> partial = countStatus(WorkTargetExecutionStatus.PARTIALLY_COMPLETED);
		NumberExpression<Integer> completed = countStatus(WorkTargetExecutionStatus.COMPLETED);
		NumberExpression<Integer> skipped = countStatus(WorkTargetExecutionStatus.SKIPPED);
		NumberExpression<Integer> canceled = countStatus(WorkTargetExecutionStatus.CANCELED);
		NumberExpression<Integer> failed = countStatus(WorkTargetExecutionStatus.FAILED);
		NumberExpression<Integer> totalQuantity = workOperationTarget.quantitySnapshot.sum();
		NumberExpression<Integer> processedQuantity = workTargetExecution.processedQuantity.sum();
		NumberExpression<Integer> skippedQuantity = new CaseBuilder()
				.when(workTargetExecution.status.eq(WorkTargetExecutionStatus.SKIPPED))
				.then(workOperationTarget.quantitySnapshot.subtract(workTargetExecution.processedQuantity))
				.otherwise(0)
				.sum();
		List<Tuple> rows = queryFactory
				.select(
						workOperationTarget.workOperation.id,
						workOperationTarget.id.count(),
						pending,
						inProgress,
						partial,
						completed,
						skipped,
						canceled,
						failed,
						totalQuantity,
						processedQuantity,
						skippedQuantity)
				.from(workOperationTarget)
				.join(workTargetExecution).on(workTargetExecution.target.eq(workOperationTarget))
				.where(
						workOperationTarget.workOperation.id.in(workOperationIds),
						workOperationTarget.excludedAt.isNull())
				.groupBy(workOperationTarget.workOperation.id)
				.fetch();
		return rows.stream()
				.map(row -> new WorkOperationProgressProjection(
						row.get(workOperationTarget.workOperation.id),
						intValue(row.get(workOperationTarget.id.count())),
						intValue(row.get(pending)),
						intValue(row.get(inProgress)),
						intValue(row.get(partial)),
						intValue(row.get(completed)),
						intValue(row.get(skipped)),
						intValue(row.get(canceled)),
						intValue(row.get(failed)),
						intValue(row.get(totalQuantity)),
						intValue(row.get(processedQuantity)),
						intValue(row.get(skippedQuantity))))
				.toList();
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

	private NumberExpression<Integer> countStatus(WorkTargetExecutionStatus status) {
		return new CaseBuilder()
				.when(workTargetExecution.status.eq(status))
				.then(1)
				.otherwise(0)
				.sum();
	}

	private int intValue(Number value) {
		return value == null ? 0 : value.intValue();
	}
}
