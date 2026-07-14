package com.greenhouse.backend.work.repository;

import static com.greenhouse.backend.work.domain.QWorkRecord.workRecord;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.domain.WorkRecordStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public class WorkRecordRepositoryImpl implements WorkRecordRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	public WorkRecordRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public List<WorkRecord> search(
			String targetType,
			Long targetId,
			String workType,
			LocalDate from,
			LocalDate to,
			boolean includeCanceled,
			WorkRecordStatus canceledStatus) {
		return queryFactory
				.selectFrom(workRecord)
				.where(searchConditions(targetType, targetId, workType, from, to, includeCanceled, canceledStatus))
				.orderBy(workRecord.workDate.desc(), workRecord.id.desc())
				.fetch();
	}

	@Override
	public List<WorkDateRow> findLatestWorkDates(
			String targetType,
			Collection<Long> targetIds,
			WorkRecordStatus status) {
		if (targetIds == null || targetIds.isEmpty()) {
			return List.of();
		}

		var latestWorkDate = workRecord.workDate.max();
		return queryFactory
				.select(workRecord.targetId, latestWorkDate)
				.from(workRecord)
				.where(
						workRecord.targetType.eq(targetType),
						workRecord.targetId.in(targetIds),
						workRecord.status.eq(status))
				.groupBy(workRecord.targetId)
				.fetch()
				.stream()
				.map(tuple -> (WorkDateRow) new LatestWorkDateRow(tuple.get(workRecord.targetId), tuple.get(latestWorkDate)))
				.toList();
	}

	private BooleanBuilder searchConditions(
			String targetType,
			Long targetId,
			String workType,
			LocalDate from,
			LocalDate to,
			boolean includeCanceled,
			WorkRecordStatus canceledStatus) {
		return new BooleanBuilder()
				.and(targetTypeEq(targetType))
				.and(targetIdEq(targetId))
				.and(workTypeEq(workType))
				.and(workDateGoe(from))
				.and(workDateLoe(to))
				.and(includeCanceled ? null : workRecord.status.ne(canceledStatus));
	}

	private BooleanExpression targetTypeEq(String targetType) {
		return targetType == null ? null : workRecord.targetType.eq(targetType);
	}

	private BooleanExpression targetIdEq(Long targetId) {
		return targetId == null ? null : workRecord.targetId.eq(targetId);
	}

	private BooleanExpression workTypeEq(String workType) {
		return workType == null ? null : workRecord.workType.eq(workType);
	}

	private BooleanExpression workDateGoe(LocalDate from) {
		return from == null ? null : workRecord.workDate.goe(from);
	}

	private BooleanExpression workDateLoe(LocalDate to) {
		return to == null ? null : workRecord.workDate.loe(to);
	}

	private record LatestWorkDateRow(Long targetId, LocalDate latestWorkDate) implements WorkDateRow {
		@Override
		public Long getTargetId() {
			return targetId;
		}

		@Override
		public LocalDate getLatestWorkDate() {
			return latestWorkDate;
		}
	}
}
