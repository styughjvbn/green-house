package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("work-e2e")
@Testcontainers
class WorkCreationBatchMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void renamesCreationGroupingWithoutChangingReceiptsOperationsOrMemberships() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(source).target("34").load().migrate();
    var jdbc = new JdbcTemplate(source);
    jdbc.execute(
        """
        INSERT INTO work_operations(id,work_type_id,title,status,planned_start_date,source_scope_type,
          target_snapshot_at,created_at,updated_at)
        SELECT n,id,'기존 작업 '||n,'PLANNED',DATE '2026-10-14','NONE',
          CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
        FROM work_types CROSS JOIN generate_series(901,903) n WHERE code='PESTICIDE';
        INSERT INTO work_command_receipts(receipt_key,request_fingerprint,result_operation_ids,created_at)
        VALUES('STRUCTURE_RECORD:old',repeat('a',64),'[901,902]',CURRENT_TIMESTAMP),
          ('IMMEDIATE:legacy',NULL,'[903]',CURRENT_TIMESTAMP);
        INSERT INTO work_command_receipt_memberships(receipt_key,operation_id)
        VALUES('STRUCTURE_RECORD:old',901),('STRUCTURE_RECORD:old',902),('IMMEDIATE:legacy',903);
        """);
    var tables =
        jdbc.queryForList(
            """
        SELECT tablename FROM pg_tables WHERE schemaname='public'
        AND tablename<>'flyway_schema_history' ORDER BY tablename
        """,
            String.class);
    var before = new LinkedHashMap<String, List<String>>();
    for (var table : tables) before.put(table, rows(jdbc, table));
    var upgrade = Flyway.configure().dataSource(source).target("35").load();
    assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(upgrade.migrate().migrationsExecuted).isZero();
    upgrade.validate();
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('work_command_receipt_memberships')", String.class))
        .isNull();
    for (var table : tables) {
      var current =
          table.equals("work_command_receipt_memberships")
              ? "work_creation_batch_memberships"
              : table;
      assertThat(rows(jdbc, current)).as(table).isEqualTo(before.get(table));
    }
    assertThat(
            jdbc.queryForList(
                """
        SELECT indexname FROM pg_indexes WHERE schemaname='public'
        AND tablename='work_creation_batch_memberships' ORDER BY indexname
        """,
                String.class))
        .containsExactly(
            "idx_work_creation_batch_operation", "work_creation_batch_memberships_pkey");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        INSERT INTO work_creation_batch_memberships(receipt_key,operation_id)
        VALUES('IMMEDIATE:legacy',901)
        """))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        INSERT INTO work_creation_batch_memberships(receipt_key,operation_id)
        VALUES('missing-request',904)
        """))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        INSERT INTO work_creation_batch_memberships(receipt_key,operation_id)
        VALUES('IMMEDIATE:legacy',904)
        """))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(rows(jdbc, "work_creation_batch_memberships"))
        .isEqualTo(before.get("work_command_receipt_memberships"));
  }

  private List<String> rows(JdbcTemplate jdbc, String table) {
    String value =
        table.equals("work_command_receipts")
            ? "to_jsonb(row) - 'response_snapshot'"
            : "to_jsonb(row)";
    return jdbc.queryForList(
        "SELECT (" + value + ")::text FROM " + table + " row ORDER BY 1", String.class);
  }
}
