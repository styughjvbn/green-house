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
class SalesCreationReceiptMigrationPostgresE2ETest extends WorkE2ETestBase {
  @Test
  void upgradesWithoutChangingLegacySalesAndConstrainsNewReceipts() {
    String database = "sales_receipts_" + UUID.randomUUID().toString().replace("-", "");
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
      Flyway.configure().dataSource(source).target("37").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          "INSERT INTO business_partners (id, name, partner_type, created_at, updated_at) VALUES (900, '기존 거래처', 'WHOLESALE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      jdbc.execute(
          "INSERT INTO sales_slips (id, slip_number, sale_date, sales_type, partner_id, sales_status, payment_status, total_amount, paid_amount, remaining_amount, created_at, updated_at) VALUES (900, '기존 전표', DATE '2026-10-04', 'DIRECT', 900, '작성중', '미입금', 5000, 0, 5000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      jdbc.execute(
          "INSERT INTO sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount) VALUES (900, 900, '기존 난', 5, 1000, 5000)");
      var slipBefore =
          jdbc.queryForList(
              "select to_jsonb(r)::text from sales_slips r order by id", String.class);
      var itemBefore =
          jdbc.queryForList(
              "select to_jsonb(r)::text from sales_slip_items r order by id", String.class);
      var upgrade = Flyway.configure().dataSource(source).target("38").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForList(
                  "select to_jsonb(r)::text from sales_slips r order by id", String.class))
          .isEqualTo(slipBefore);
      assertThat(
              jdbc.queryForList(
                  "select to_jsonb(r)::text from sales_slip_items r order by id", String.class))
          .isEqualTo(itemBefore);
      assertThat(jdbc.queryForObject("select count(*) from sales_creation_receipts", Integer.class))
          .isZero();

      jdbc.execute(
          "insert into sales_creation_receipts (request_key, request_fingerprint, created_at) values ('claim', repeat('a',64), CURRENT_TIMESTAMP)");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "insert into sales_creation_receipts (request_key, request_fingerprint, created_at) values ('claim', repeat('a',64), CURRENT_TIMESTAMP)"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("sales_creation_receipts_pkey");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "insert into sales_creation_receipts (request_key, request_fingerprint, created_at) values (' ', repeat('a',64), CURRENT_TIMESTAMP)"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_sales_creation_key");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "insert into sales_creation_receipts (request_key, request_fingerprint, created_at) values ('bad-hash', 'invalid', CURRENT_TIMESTAMP)"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_sales_creation_fingerprint");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "update sales_creation_receipts set sales_slip_id = 900 where request_key = 'claim'"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_sales_creation_response");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "update sales_creation_receipts set response_snapshot = '{\"id\":900}'::jsonb where request_key = 'claim'"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_sales_creation_response");
      for (String json : List.of("{}", "{\"id\":901}", "{\"id\":null}", "[]", "null")) {
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "update sales_creation_receipts set sales_slip_id = 900, response_snapshot = ?::jsonb where request_key = 'claim'",
                        json))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_sales_creation_response");
      }
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "update sales_creation_receipts set sales_slip_id = 901, response_snapshot = '{\"id\":901}'::jsonb where request_key = 'claim'"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("sales_creation_receipts_sales_slip_id_fkey");
      jdbc.execute(
          "update sales_creation_receipts set sales_slip_id = 900, response_snapshot = '{\"id\":900}'::jsonb where request_key = 'claim'");
      assertThatThrownBy(() -> jdbc.execute("delete from sales_slips where id = 900"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      upgrade.validate();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
