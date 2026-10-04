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
class InboundCreationReceiptMigrationPostgresE2ETest extends WorkE2ETestBase {
  @Test
  void upgradesWithoutInventingLegacyInboundIdentitiesAndConstrainsNewReceipts() {
    String database = "inbound_receipts_" + UUID.randomUUID().toString().replace("-", "");
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
      Flyway.configure().dataSource(source).target("38").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          "INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at) VALUES (900, '기존 품종', '속', '품종', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      jdbc.execute(
          "INSERT INTO inbound_records (id, variety_id, inbound_date, inbound_type, status, estimated_quantity, worker, memo, created_at, updated_at) VALUES (900, 900, DATE '2026-10-04', 'FLASK_SEEDLING', 'POTTING_PENDING', 10, '기존 담당', '기존 이력', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      var inboundBefore =
          jdbc.queryForList(
              "select to_jsonb(r)::text from inbound_records r order by id", String.class);
      var varietyBefore =
          jdbc.queryForList("select to_jsonb(r)::text from varieties r order by id", String.class);
      var upgrade = Flyway.configure().dataSource(source).target("39").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForList(
                  "select to_jsonb(r)::text from inbound_records r order by id", String.class))
          .isEqualTo(inboundBefore);
      assertThat(
              jdbc.queryForList(
                  "select to_jsonb(r)::text from varieties r order by id", String.class))
          .isEqualTo(varietyBefore);
      assertThat(
              jdbc.queryForObject("select count(*) from inbound_creation_receipts", Integer.class))
          .isZero();
      assertThat(jdbc.queryForObject("select count(*) from work_operations", Integer.class))
          .isZero();
      assertThat(jdbc.queryForObject("select count(*) from orchid_groups", Integer.class)).isZero();

      jdbc.execute(
          "insert into inbound_creation_receipts (request_key, request_fingerprint, created_at) values ('claim', repeat('a',64), CURRENT_TIMESTAMP)");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "insert into inbound_creation_receipts (request_key, request_fingerprint, created_at) values ('claim', repeat('a',64), CURRENT_TIMESTAMP)"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("inbound_creation_receipts_pkey");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "insert into inbound_creation_receipts (request_key, request_fingerprint, created_at) values (' ', repeat('a',64), CURRENT_TIMESTAMP)"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_inbound_creation_key");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "insert into inbound_creation_receipts (request_key, request_fingerprint, created_at) values ('bad-hash', 'invalid', CURRENT_TIMESTAMP)"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_inbound_creation_fingerprint");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "update inbound_creation_receipts set inbound_record_id = 900 where request_key = 'claim'"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_inbound_creation_response");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "update inbound_creation_receipts set response_snapshot = '{\"id\":900}'::jsonb where request_key = 'claim'"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_inbound_creation_response");
      for (String json : List.of("{}", "{\"id\":901}", "{\"id\":null}", "[]", "null")) {
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "update inbound_creation_receipts set inbound_record_id = 900, response_snapshot = ?::jsonb where request_key = 'claim'",
                        json))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_inbound_creation_response");
      }
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "update inbound_creation_receipts set inbound_record_id = 901, response_snapshot = '{\"id\":901}'::jsonb where request_key = 'claim'"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("inbound_creation_receipts_inbound_record_id_fkey");
      jdbc.execute(
          "update inbound_creation_receipts set inbound_record_id = 900, response_snapshot = '{\"id\":900}'::jsonb where request_key = 'claim'");
      assertThatThrownBy(() -> jdbc.execute("delete from inbound_records where id = 900"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      upgrade.validate();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
