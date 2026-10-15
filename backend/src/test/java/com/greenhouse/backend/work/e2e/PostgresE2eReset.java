package com.greenhouse.backend.work.e2e;

import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Restores the freshly migrated reference rows without rewinding pooled ID sequences. */
final class PostgresE2eReset {
  private static final List<String> REFERENCE_TABLES =
      List.of("work_types", "houses", "physical_beds", "bed_zones", "bed_zone_capacities");

  private final JdbcTemplate jdbc;
  private final EntityManagerFactory entityManagerFactory;
  private final boolean statisticsEnabled;
  private final List<String> tables;
  private final Map<String, List<Map<String, Object>>> referenceRows = new LinkedHashMap<>();

  PostgresE2eReset(
      JdbcTemplate jdbc, EntityManagerFactory entityManagerFactory, String expectedUrl) {
    this.jdbc = jdbc;
    this.entityManagerFactory = entityManagerFactory;
    statisticsEnabled =
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().isStatisticsEnabled();
    String actualUrl =
        jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
    if (!expectedUrl.equals(actualUrl))
      throw new IllegalStateException("E2E reset requires its isolated Testcontainers database");
    tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname='public' "
                + "AND tablename<>'flyway_schema_history' ORDER BY tablename",
            String.class);
    for (String table : tables) {
      if (REFERENCE_TABLES.contains(table)) {
        referenceRows.put(table, jdbc.queryForList("SELECT * FROM " + quote(table)));
      } else if (jdbc.queryForObject("SELECT count(*) FROM " + quote(table), Long.class) != 0) {
        throw new IllegalStateException("Review newly seeded E2E table before reset: " + table);
      }
    }
  }

  void reset() {
    jdbc.execute(
        "TRUNCATE TABLE "
            + tables.stream().map(PostgresE2eReset::quote).collect(Collectors.joining(","))
            + " CONTINUE IDENTITY");
    for (String table : REFERENCE_TABLES) {
      var rows = referenceRows.get(table);
      if (rows == null || rows.isEmpty()) continue;
      var columns = List.copyOf(rows.getFirst().keySet());
      String insert =
          "INSERT INTO "
              + quote(table)
              + " ("
              + columns.stream().map(PostgresE2eReset::quote).collect(Collectors.joining(","))
              + ") VALUES ("
              + columns.stream().map(column -> "?").collect(Collectors.joining(","))
              + ")";
      jdbc.batchUpdate(
          insert, rows.stream().map(row -> columns.stream().map(row::get).toArray()).toList());
    }
    entityManagerFactory.getCache().evictAll();
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(statisticsEnabled);
    statistics.clear();
  }

  private static String quote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
