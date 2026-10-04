package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class WorkCreationSnapshotMigrationPostgresE2ETest extends WorkE2ETestBase {
  @Test
  void preservesLegacyIdOnlyReceiptsAndConstrainsNewCreationSnapshots() {
    String database = "work_snapshots_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(source).target("39").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          """
          insert into work_operations (id, work_type_id, title, status, planned_start_date,
            source_scope_type, target_snapshot_at, created_at, updated_at)
          select 900, id, '과거 작업', 'COMPLETED', DATE '2026-10-04', 'NONE',
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
          from work_types where code = 'PESTICIDE'
          """);
      jdbc.execute(
          "insert into work_command_receipts (receipt_key, request_fingerprint, result_operation_ids, created_at) values ('IMMEDIATE:legacy', null, '[900]', CURRENT_TIMESTAMP), ('STRUCTURE_RECORD:known', repeat('a',64), '[900]', CURRENT_TIMESTAMP)");
      jdbc.execute(
          "insert into work_command_receipt_memberships (receipt_key, operation_id) values ('IMMEDIATE:legacy', 900)");
      var before =
          jdbc.queryForList(
              "select to_jsonb(r)::text from work_command_receipts r order by receipt_key",
              String.class);
      var workBefore =
          jdbc.queryForList(
              "select to_jsonb(r)::text from work_operations r order by id", String.class);
      var membershipBefore =
          jdbc.queryForList(
              "select to_jsonb(r)::text from work_command_receipt_memberships r order by 1",
              String.class);
      var upgrade = Flyway.configure().dataSource(source).target("40").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForList(
                  "select (to_jsonb(r) - 'response_snapshot')::text from work_command_receipts r order by receipt_key",
                  String.class))
          .isEqualTo(before);
      assertThat(
              jdbc.queryForList(
                  "select to_jsonb(r)::text from work_operations r order by id", String.class))
          .isEqualTo(workBefore);
      assertThat(
              jdbc.queryForList(
                  "select to_jsonb(r)::text from work_command_receipt_memberships r order by 1",
                  String.class))
          .isEqualTo(membershipBefore);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from work_command_receipts where response_snapshot is not null",
                  Integer.class))
          .isZero();
      for (String scope : List.of("GENERAL_PLAN", "GENERAL_PLAN_BATCH", "GENERAL_RECORD")) {
        String key = scope + ":new";
        jdbc.update(
            "insert into work_command_receipts (receipt_key, request_fingerprint, created_at) values (?, repeat('b',64), CURRENT_TIMESTAMP)",
            key);
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "update work_command_receipts set result_operation_ids = '[900]' where receipt_key = ?",
                        key))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_general_work_creation_complete");
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "update work_command_receipts set response_snapshot = '[{\"id\":900}]' where receipt_key = ?",
                        key))
            .isInstanceOf(DataIntegrityViolationException.class);
        for (String invalid :
            List.of(
                "{}",
                "null",
                "[]",
                "[{}]",
                "[{\"id\":901}]",
                "[{\"id\":null}]",
                "[{\"id\":\"900\"}]",
                "[900]",
                "[{\"id\":900},{\"id\":901}]")) {
          assertThatThrownBy(
                  () ->
                      jdbc.update(
                          "update work_command_receipts set result_operation_ids = '[900]', response_snapshot = ?::jsonb where receipt_key = ?",
                          invalid,
                          key))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("ck_work_creation_response");
        }
        jdbc.update(
            "update work_command_receipts set result_operation_ids = '[900,901]', response_snapshot = '[{\"id\":900},{\"id\":901}]' where receipt_key = ?",
            key);
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "update work_command_receipts set result_operation_ids = '[901,900]' where receipt_key = ?",
                        key))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_work_creation_response");
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "update work_command_receipts set request_fingerprint = null where receipt_key = ?",
                        key))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_work_creation_response");
      }
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      upgrade.validate();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
