package com.greenhouse.backend.work.repository;

import static com.greenhouse.backend.work.domain.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.domain.QWorkType.workType;

import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkOperationSearchView;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class WorkOperationRepositoryImpl implements WorkOperationRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	public WorkOperationRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public Optional<WorkOperation> findWithWorkTypeById(Long id) {
		return Optional.ofNullable(queryFactory
				.selectFrom(workOperation)
				.join(workOperation.workType, workType).fetchJoin()
				.where(workOperation.id.eq(id))
				.fetchOne());
	}

	@Override
	public Optional<WorkOperation> findByRequestKey(String requestKey) {
		return Optional.ofNullable(queryFactory
				.selectFrom(workOperation)
				.join(workOperation.workType, workType).fetchJoin()
				.where(workOperation.requestKey.eq(requestKey))
				.fetchOne());
	}

	@Override
	public List<WorkOperation> search(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			LocalDateTime todayStartedAt,
			WorkSourceScopeType scopeType,
			Long scopeId) {
		return queryFactory
				.selectFrom(workOperation)
				.join(workOperation.workType, workType).fetchJoin()
				.where(searchConditions(
						fromDate, toDate, status, view, todayStartedAt, scopeType, scopeId))
				.orderBy(workOperation.plannedStartDate.desc(), workOperation.id.desc())
				.fetch();
	}

	private BooleanBuilder searchConditions(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			LocalDateTime todayStartedAt,
			WorkSourceScopeType scopeType,
			Long scopeId) {
		return new BooleanBuilder()
				.and(statusEq(status))
				.and(viewCondition(view, todayStartedAt))
				.and(scopeTypeEq(scopeType))
				.and(scopeIdEq(scopeId))
				.and(periodEndsOnOrAfter(fromDate))
				.and(periodStartsOnOrBefore(toDate));
	}

	private BooleanExpression viewCondition(WorkOperationSearchView view, LocalDateTime todayStartedAt) {
		if (view == null || view == WorkOperationSearchView.ALL) {
			return null;
		}
		BooleanExpression active = workOperation.status.in(
				WorkOperationStatus.PLANNED,
				WorkOperationStatus.IN_PROGRESS,
				WorkOperationStatus.PAUSED);
		if (view == WorkOperationSearchView.MANAGEMENT) {
			return active.or(workOperation.updatedAt.goe(todayStartedAt));
		}
		return active.not();
	}

	private BooleanExpression statusEq(WorkOperationStatus status) {
		return status == null ? null : workOperation.status.eq(status);
	}

	private BooleanExpression scopeTypeEq(WorkSourceScopeType scopeType) {
		return scopeType == null ? null : workOperation.sourceScopeType.eq(scopeType);
	}

	private BooleanExpression scopeIdEq(Long scopeId) {
		return scopeId == null ? null : workOperation.sourceScopeId.eq(scopeId);
	}

	private BooleanExpression periodEndsOnOrAfter(LocalDate fromDate) {
		if (fromDate == null) {
			return null;
		}
		return workOperation.plannedEndDate.goe(fromDate)
				.or(workOperation.plannedEndDate.isNull()
						.and(workOperation.plannedStartDate.goe(fromDate)));
	}

	private BooleanExpression periodStartsOnOrBefore(LocalDate toDate) {
		return toDate == null ? null : workOperation.plannedStartDate.loe(toDate);
	}
}
