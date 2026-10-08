package com.greenhouse.backend.work.operation.application;

import static com.greenhouse.backend.work.operation.domain.QWorkOperation.workOperation;
import static com.greenhouse.backend.work.operation.domain.QWorkType.workType;
import static com.greenhouse.backend.work.target.domain.QWorkOperationTarget.workOperationTarget;

import com.greenhouse.backend.work.api.operation.WorkOperationMetricsApi;
import com.greenhouse.backend.work.api.operation.WorkOperationMetricsApi.RecentRecord;
import com.greenhouse.backend.work.api.operation.WorkOperationMetricsApi.Summary;
import com.greenhouse.backend.work.api.operation.WorkOperationMetricsApi.TypeCount;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationMetricsReader implements WorkOperationMetricsApi {

  private final JPAQueryFactory queryFactory;

  @Override
  public Summary getSummary(LocalDate from, LocalDate to) {
    var completedInPeriod =
        completedWorkOperations().and(workOperation.plannedStartDate.between(from, to));
    var count = workOperation.id.count();
    var latestDate = workOperation.plannedStartDate.max();
    var groups =
        queryFactory
            .select(workType.name, workType.template, count, latestDate)
            .from(workOperation)
            .join(workOperation.workType, workType)
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
    var typeCounts =
        countsByName.entrySet().stream()
            .map(entry -> new TypeCount(entry.getKey(), entry.getValue()))
            .sorted(
                Comparator.comparingLong(TypeCount::count)
                    .reversed()
                    .thenComparing(TypeCount::name))
            .toList();
    var recentRecords =
        queryFactory
            .select(
                Projections.constructor(
                    RecentRecord.class,
                    workOperation.id,
                    workOperation.plannedStartDate,
                    workType.name,
                    workType.template,
                    workOperation.title,
                    workOperation.sourceScopeType,
                    workOperation.worker,
                    workOperation.memo,
                    workOperation.status))
            .from(workOperation)
            .join(workOperation.workType, workType)
            .where(completedInPeriod)
            .orderBy(workOperation.plannedStartDate.desc(), workOperation.id.desc())
            .limit(10)
            .fetch();
    return new Summary(
        totalCount, movementCount, statusCount, latestWorkDate, typeCounts, recentRecords);
  }

  @Override
  public Map<Long, LocalDate> getLatestWorkDates(Collection<Long> orchidGroupIds) {
    if (orchidGroupIds == null || orchidGroupIds.isEmpty()) {
      return Map.of();
    }
    var ids = new ArrayList<>(new LinkedHashSet<>(orchidGroupIds));
    var result = new HashMap<Long, LocalDate>();
    for (int start = 0; start < ids.size(); start += 500) {
      result.putAll(latestWorkDateBatch(ids.subList(start, Math.min(start + 500, ids.size()))));
    }
    return result;
  }

  private Map<Long, LocalDate> latestWorkDateBatch(Collection<Long> orchidGroupIds) {
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
        .collect(
            Collectors.toMap(
                tuple -> tuple.get(workOperationTarget.orchidGroupId),
                tuple -> tuple.get(latestWorkDate)));
  }

  private BooleanExpression completedWorkOperations() {
    return workOperation.status.in(WorkOperationStatus.COMPLETED);
  }
}
