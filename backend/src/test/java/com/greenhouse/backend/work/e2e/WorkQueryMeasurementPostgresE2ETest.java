package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.support.JdbcMeasurement;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
@Import(JdbcMeasurement.Configuration.class)
class WorkQueryMeasurementPostgresE2ETest extends WorkE2ETestBase {
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcMeasurement jdbcMeasurement;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManagerFactory entityManagerFactory;

  @Test
  void summaryCostStaysBoundedWhenOnlyTargetFanOutGrows() throws Exception {
    JdbcMeasurement.Sample first = null;
    for (int targets : new int[] {1, 40, 100}) {
      seeder.resetKeepingSequences();
      seeder.seedBenchmark(4, targets);
      var path = "/api/work-operations?view=ALL&size=4";
      assertThat(get(path).status()).isEqualTo(200);
      var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
      statistics.clear();
      jdbcMeasurement.start();
      ApiResult response;
      JdbcMeasurement.Sample sample;
      try {
        response = get(path);
      } finally {
        sample = jdbcMeasurement.stop();
      }
      assertThat(response.status()).isEqualTo(200);
      assertThat(response.data().path("content")).hasSize(4);
      assertThat(response.data().path("content").get(0).path("progress").path("total").asInt())
          .isEqualTo(targets);
      assertThat(statistics.getEntityStatistics(WorkOperationTarget.class.getName()).getLoadCount())
          .isZero();
      assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(7);
      assertThat(sample.executions()).isBetween(1L, 7L);
      assertThat(sample.rows()).isBetween(1L, 48L);
      assertThat(sample.failures()).isZero();
      if (first == null) first = sample;
      else {
        assertThat(sample.executions())
            .as("fixed root count, targets=%s", targets)
            .isEqualTo(first.executions());
        assertThat(sample.rows())
            .as("summary must not return target snapshot rows")
            .isEqualTo(first.rows());
      }
    }
  }

  @Test
  void jdbcTemplateRowsAreVisibleEvenWhenHibernateReportsNoStatements() {
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    jdbcMeasurement.start();
    JdbcMeasurement.Sample sample;
    try {
      assertThat(jdbc.queryForList("select n from generate_series(1, 17) n", Integer.class))
          .hasSize(17);
    } finally {
      sample = jdbcMeasurement.stop();
    }
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(sample.executions()).isOne();
    assertThat(sample.rows()).isEqualTo(17);
  }

  @Test
  void measuresStandaloneWriteThroughCommitAndSeparatesReplayCost() throws Exception {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    seeder.baselineGroups();
    String body =
        """
        {"workTypeId": %d, "title": "측정용 기록", "plannedStartDate": "2026-07-15",
         "sourceScopeType": "ORCHID_GROUP", "sourceScopeId": %d,
         "details": {}, "worker": "측정"}
        """
            .formatted(scenario.pesticideWorkTypeId(), scenario.orchidGroupId());
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    jdbcMeasurement.start();
    ApiResult written;
    JdbcMeasurement.Sample write;
    try {
      written =
          post(
              "/api/work-operations/record", body, Map.of("Idempotency-Key", "measurement-record"));
    } finally {
      write = jdbcMeasurement.stop();
    }
    assertThat(written.status()).isEqualTo(201);
    assertThat(written.data().path("status").asText()).isEqualTo("COMPLETED");
    assertThat(write.commits()).isOne();
    assertThat(write.rollbacks()).isZero();
    assertThat(write.failures()).isZero();
    assertThat(statistics.getFlushCount()).isPositive();
    long writeFlushes = statistics.getFlushCount();
    long preparedStatements = statistics.getPrepareStatementCount();
    jdbcMeasurement.start();
    JdbcMeasurement.Sample replay;
    try {
      var repeated =
          post(
              "/api/work-operations/record", body, Map.of("Idempotency-Key", "measurement-record"));
      assertThat(repeated.body()).isEqualTo(written.body());
    } finally {
      replay = jdbcMeasurement.stop();
    }
    assertThat(replay.executions()).isLessThan(write.executions());
    assertThat(replay.commits()).isOne();
    assertThat(replay.failures()).isZero();
    Path report = Path.of("build/test-measurements/work-write.json");
    Files.createDirectories(report.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            report.toFile(),
            Map.of(
                "write",
                write,
                "replay",
                replay,
                "writeHibernateFlushes",
                writeFlushes,
                "writeHibernatePreparedStatements",
                preparedStatements));
  }
}
