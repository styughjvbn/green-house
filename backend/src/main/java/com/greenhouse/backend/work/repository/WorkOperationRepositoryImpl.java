package com.greenhouse.backend.work.repository;

import static com.greenhouse.backend.work.domain.operation.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.domain.operation.QWorkType.workType;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkOperationSearchView;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
	public Page<WorkOperation> search(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			LocalDateTime todayStartedAt,
			WorkSourceScopeType scopeType,
			Long scopeId,
			String keyword,
			Pageable pageable) {
		BooleanBuilder conditions = searchConditions(
				fromDate, toDate, status, view, todayStartedAt, scopeType, scopeId, keyword);
		var content = queryFactory
				.selectFrom(workOperation)
				.join(workOperation.workType, workType).fetchJoin()
				.where(conditions)
				.orderBy(workOperation.plannedStartDate.desc(), workOperation.id.desc())
				.offset(pageable.getOffset())
				.limit(pageable.getPageSize())
				.fetch();
		Long total = queryFactory
				.select(workOperation.count())
				.from(workOperation)
				.join(workOperation.workType, workType)
				.where(conditions)
				.fetchOne();
		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	private BooleanBuilder searchConditions(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			LocalDateTime todayStartedAt,
			WorkSourceScopeType scopeType,
			Long scopeId,
			String keyword) {
		return new BooleanBuilder()
				.and(statusEq(status))
				.and(viewCondition(view, todayStartedAt))
				.and(scopeTypeEq(scopeType))
				.and(scopeIdEq(scopeId))
				.and(keywordContains(keyword))
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

	private BooleanExpression keywordContains(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		String normalized = keyword.trim();
		return workOperation.title.containsIgnoreCase(normalized)
				.or(workType.name.containsIgnoreCase(normalized))
				.or(workType.code.containsIgnoreCase(normalized))
				.or(workOperation.worker.containsIgnoreCase(normalized))
				.or(workOperation.memo.containsIgnoreCase(normalized));
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
