package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class ConsolidatedWorkMigrationPostgresE2ETest extends WorkE2ETestBase {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void upgradesV27DataThroughHistoricalV34AndOptionallyLatestSchema(boolean latest) {
    String database = "consolidated_work_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(dataSource).target("27").load().migrate();
      var jdbc = new JdbcTemplate(dataSource);
      seed(jdbc);
      var upgrade = Flyway.configure().dataSource(dataSource).target("34").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(7);
      assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("34");
      assertThat(
              jdbc.queryForList(
                  "SELECT version FROM flyway_schema_history WHERE version::INTEGER > 27 ORDER BY installed_rank",
                  String.class))
          .containsExactly("28", "29", "30", "31", "32", "33", "34");
      if (latest) {
        // The V27 -> V34 assertions above deliberately preserve that historical upgrade boundary.
        upgrade = Flyway.configure().dataSource(dataSource).load();
        var pending =
            Arrays.stream(upgrade.info().pending())
                .filter(migration -> migration.getVersion() != null)
                .count();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(Math.toIntExact(pending));
        assertThat(Arrays.stream(upgrade.info().pending())).isEmpty();
        var resolved =
            Arrays.stream(upgrade.info().all())
                .filter(migration -> migration.getVersion() != null)
                .map(migration -> migration.getVersion().getVersion())
                .toList();
        assertThat(
                jdbc.queryForList(
                    "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                    String.class))
            .containsExactlyElementsOf(resolved);
        assertThat(upgrade.info().current().getVersion().getVersion())
            .isEqualTo(resolved.getLast());
      }
      assertThat(
              jdbc.queryForObject("SELECT status FROM inbound_records WHERE id=20", String.class))
          .isEqualTo("POTTING_PENDING");
      assertThat(
              jdbc.queryForObject(
                  "SELECT inbound_record_id FROM orchid_groups WHERE id=10", Long.class))
          .isEqualTo(20);
      assertThat(
              jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id=10", Integer.class))
          .isEqualTo(100);
      assertThat(jdbc.queryForList("SELECT status FROM work_operations ORDER BY id", String.class))
          .containsExactly("STOPPED", "CANCELED", "CANCELED");
      assertThat(jdbc.queryForList("SELECT title FROM work_operations ORDER BY id", String.class))
          .containsOnly("사용자 작업명");
      assertThat(
              jdbc.queryForList(
                  "SELECT operation_id FROM work_command_receipt_memberships WHERE receipt_key='legacy' ORDER BY operation_id",
                  Long.class))
          .containsExactly(900L, 901L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT result_operation_ids::text FROM work_command_receipts WHERE receipt_key='legacy'",
                  String.class))
          .isEqualTo("[900, 901]");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM work_correction_receipts", Long.class))
          .isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_stock_counts", Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM work_types WHERE code IN ('CORRECTION', 'MULTI_CREATE')",
                  Long.class))
          .isZero();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      upgrade.validate();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  private void seed(JdbcTemplate jdbc) {
    jdbc.execute(
        """
        INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
        VALUES (9001, 'VAR-CONSOLIDATED', '속', '품종', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO orchid_groups (
            id, bed_zone_id, variety_id, genus, variety_name, quantity, reserved_quantity,
            sort_order, status, pot_size_code, version, state_revision, created_at, updated_at
        ) VALUES (10, (SELECT min(id) FROM bed_zones), 9001, '속', '품종', 100, 0, 1,
                  '정상', 'POT_3', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO inbound_records (
            id, variety_id, inbound_date, inbound_type, status, created_orchid_group_id, created_at, updated_at
        ) VALUES (20, 9001, DATE '2026-09-01', 'BOTTLE', 'TEMP_STORED', 10,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO work_operations (
            id, work_type_id, title, status, planned_start_date, source_scope_type,
            target_snapshot_at, details, version, created_at, updated_at
        ) SELECT operation_id, type.id, '사용자 작업명', 'CANCELED', DATE '2026-09-01',
                 'NONE', CURRENT_TIMESTAMP, '{}'::jsonb, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
          FROM work_types type CROSS JOIN generate_series(900, 902) operation_id WHERE type.code='MEMO';
        INSERT INTO work_applied_effects (
            id, work_operation_id, effect_key, effect_kind, handler_code, applied_at, canceled_at,
            command_details, result_details, created_at, updated_at
        ) VALUES (900, 900, 'live', 'RECORD', 'MEMO', CURRENT_TIMESTAMP, NULL,
                  '{}'::jsonb, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                 (902, 902, 'canceled', 'RECORD', 'MEMO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                  '{}'::jsonb, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO work_command_receipts (receipt_key, result_operation_ids, created_at)
        VALUES ('legacy', '[900,901]'::jsonb, CURRENT_TIMESTAMP);
        """);
  }
}
