package com.greenhouse.backend.work.application;

import static com.greenhouse.backend.work.domain.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.domain.QWorkOperationTarget.workOperationTarget;

import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkOperationMetricsReader {

	private final JPAQueryFactory queryFactory;

	public WorkOperationMetricsReader(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
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
						workOperation.status.in(
								WorkOperationStatus.COMPLETED,
								WorkOperationStatus.CORRECTED))
				.groupBy(workOperationTarget.orchidGroupId)
				.fetch()
				.stream()
				.collect(Collectors.toMap(
						tuple -> tuple.get(workOperationTarget.orchidGroupId),
						tuple -> tuple.get(latestWorkDate)));
	}
}
