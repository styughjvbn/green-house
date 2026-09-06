package com.greenhouse.backend.work.application.operation;

import static com.greenhouse.backend.work.domain.operation.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.domain.operation.QWorkType.workType;
import static com.greenhouse.backend.work.domain.target.QWorkOperationTarget.workOperationTarget;

import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationMetricsReader {

	private final JPAQueryFactory queryFactory;

	public Summary getSummary(LocalDate from, LocalDate to) {
		var completedInPeriod = completedWorkOperations().and(workOperation.plannedStartDate.between(from, to));
		var count = workOperation.id.count();
		var latestDate = workOperation.plannedStartDate.max();
		var groups = queryFactory.select(workType.name, workType.template, count, latestDate)
				.from(workOperation).join(workOperation.workType, workType)
				.where(completedInPeriod)
				.groupBy(workType.name, workType.template)
				.fetch();

		long totalCount = 0;
		long movementCount = 0;
		long statusCount = 0;
		LocalDate latestWorkDate = null;
		Map<String, Long> countsByName = new HashMap<>();
		for (var group : groups) {
			long groupCount = group.get(count);
			totalCount += groupCount;
			if (group.get(workType.template) == WorkTypeTemplate.MOVEMENT) {
				movementCount += groupCount;
			}
			if (group.get(workType.template) == WorkTypeTemplate.STATUS) {
				statusCount += groupCount;
			}
			LocalDate groupDate = group.get(latestDate);
			if (latestWorkDate == null || groupDate.isAfter(latestWorkDate)) {
				latestWorkDate = groupDate;
			}
			countsByName.merge(group.get(workType.name), groupCount, Long::sum);
		}
		var typeCounts = countsByName.entrySet().stream()
				.map(entry -> new TypeCount(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparingLong(TypeCount::count).reversed().thenComparing(TypeCount::name))
				.toList();
		var recentRecords = queryFactory.select(Projections.constructor(RecentRecord.class,
						workOperation.id, workOperation.plannedStartDate, workType.name, workType.template,
						workOperation.title, workOperation.sourceScopeType, workOperation.worker,
						workOperation.memo, workOperation.status))
				.from(workOperation).join(workOperation.workType, workType)
				.where(completedInPeriod)
				.orderBy(workOperation.plannedStartDate.desc(), workOperation.id.desc())
				.limit(10)
				.fetch();
		return new Summary(totalCount, movementCount, statusCount, latestWorkDate, typeCounts, recentRecords);
	}

	public Map<Long, LocalDate> getLatestWorkDates(Collection<Long> orchidGroupIds) {
		if (orchidGroupIds == null || orchidGroupIds.isEmpty()) {
			return Map.of();
		}
		var latestWorkDate = workOperation.plannedStartDate.max();
		return queryFactory
				.select(workOperationTarget.orchidGroupId, latestWorkDate)
				.from(workOperationTarget)
				.join(workOperationTarget.workOperation, workOperation)
				.where(
						workOperationTarget.orchidGroupId.in(orchidGroupIds),
						workOperationTarget.excludedAt.isNull(),
						completedWorkOperations())
				.groupBy(workOperationTarget.orchidGroupId)
				.fetch()
				.stream()
				.collect(Collectors.toMap(
						tuple -> tuple.get(workOperationTarget.orchidGroupId),
						tuple -> tuple.get(latestWorkDate)));
	}

	private BooleanExpression completedWorkOperations() {
		return workOperation.status.in(WorkOperationStatus.COMPLETED, WorkOperationStatus.CORRECTED);
	}

	public record Summary(long totalCount, long movementCount, long statusCount, LocalDate latestWorkDate,
			List<TypeCount> typeCounts, List<RecentRecord> recentRecords) {
		public Summary {
			typeCounts = List.copyOf(typeCounts);
			recentRecords = List.copyOf(recentRecords);
		}
	}

	public record TypeCount(String name, long count) {
	}

	public record RecentRecord(Long id, LocalDate workDate, String workType, WorkTypeTemplate workTypeTemplate,
			String title, WorkSourceScopeType sourceScopeType, String worker, String memo, WorkOperationStatus status) {
	}
}
