package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class WorkCorrectionMigrationPostgresE2ETest extends WorkE2ETestBase {

  @ParameterizedTest
  @ValueSource(strings = {"operation", "status", "audit"})
  void refusesHistoricalCorrectionsWithoutChangingTheirData(String fixture) {
    String database = "work_correction_guard_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
    var dataSource =
        new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(dataSource).target("32").load().migrate();
      var jdbc = new JdbcTemplate(dataSource);
      jdbc.update(
          """
					INSERT INTO work_operations (
					    id, work_type_id, title, status, planned_start_date, planned_end_date,
					    source_scope_type, source_condition_snapshot, target_snapshot_at, details,
					    worker, version, created_at, updated_at
					)
					SELECT 900, id, '기존 작업', ?, DATE '2026-09-01', DATE '2026-09-01',
					       'NONE', '{}'::jsonb, CURRENT_TIMESTAMP, '{}'::jsonb,
					       '작업자', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
					FROM work_types WHERE code = ?
					""",
          fixture.equals("status") ? "CORRECTED" : "COMPLETED",
          fixture.equals("operation") ? "CORRECTION" : "REPOT");
      if (fixture.equals("audit")) {
        jdbc.update(
            """
						INSERT INTO work_operations (
						    id, work_type_id, title, status, planned_start_date, planned_end_date,
						    source_scope_type, source_condition_snapshot, target_snapshot_at, details,
						    worker, version, created_at, updated_at
						)
						SELECT 901, work_type_id, title, status, planned_start_date, planned_end_date,
						       source_scope_type, source_condition_snapshot, target_snapshot_at, details,
						       worker, version, created_at, updated_at FROM work_operations WHERE id = 900
						""");
        jdbc.update(
            """
						INSERT INTO work_operation_corrections (
						    original_work_operation_id, correction_work_operation_id, reason, created_at
						) VALUES (900, 901, '기존 사유', CURRENT_TIMESTAMP)
						""");
      }
      assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).load().migrate())
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("Existing work corrections require a separate migration");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operations", Long.class))
          .isEqualTo(fixture.equals("audit") ? 2 : 1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operation_corrections", Long.class))
          .isEqualTo(fixture.equals("audit") ? 1 : 0);
      assertThat(
              jdbc.queryForObject(
                  """
					SELECT count(*) FROM information_schema.columns
					WHERE table_name = 'work_operation_corrections' AND column_name = 'correction_work_operation_id'
					""",
                  Long.class))
          .isEqualTo(1);
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
