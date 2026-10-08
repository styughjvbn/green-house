package com.greenhouse.backend.work.api.operation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Work-owned read contracts; storage and query implementation remain internal. */
public interface WorkOperationMetricsApi {

  Summary getSummary(LocalDate from, LocalDate to);

  Map<Long, LocalDate> getLatestWorkDates(Collection<Long> orchidGroupIds);

  public record Summary(
      long totalCount,
      long movementCount,
      long statusCount,
      LocalDate latestWorkDate,
      List<TypeCount> typeCounts,
      List<RecentRecord> recentRecords) {
    public Summary {
      typeCounts = List.copyOf(typeCounts);
      recentRecords = List.copyOf(recentRecords);
    }
  }

  public record TypeCount(String name, long count) {}

  public record RecentRecord(
      Long id,
      LocalDate workDate,
      String workType,
      WorkTypeTemplate workTypeTemplate,
      String title,
      WorkSourceScopeType sourceScopeType,
      String worker,
      String memo,
      WorkOperationStatus status) {}
}
