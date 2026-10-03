package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class SalesAmountMigrationPostgresE2ETest extends WorkE2ETestBase {

  @ParameterizedTest
  @CsvSource({"2,-1294967296", "3,205032704"})
  void upgradesWithoutRewritingHistoricalOverflowAndProtectsNewWrites(int quantity, int amount) {
    String database = "sales_amount_guard_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
    var dataSource =
        new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(dataSource).target("34").load().migrate();
      var jdbc = new JdbcTemplate(dataSource);
      jdbc.update(
          """
          INSERT INTO business_partners (id, name, created_at, updated_at)
          VALUES (900, '기존 금액 거래처', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      jdbc.update(
          """
          INSERT INTO sales_slips (id, slip_number, sale_date, sales_type, partner_id,
              total_amount, paid_amount, remaining_amount, payment_status, sales_status, created_at, updated_at)
          VALUES (900, 'LEGACY-OVERFLOW', DATE '2026-10-03', 'DIRECT', 900,
              ?, 0, ?, '미입금', '작성중', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """,
          amount,
          Math.max(0L, amount));
      jdbc.update(
          """
          INSERT INTO sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount)
          VALUES (900, 900, '기존 품목', ?, 1500000000, ?)
          """,
          quantity,
          amount);
      var oldSlip = jdbc.queryForList("SELECT * FROM sales_slips WHERE id = 900");
      var oldItems = jdbc.queryForList("SELECT * FROM sales_slip_items WHERE id = 900");

      var upgrade = Flyway.configure().dataSource(dataSource).target("35").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("35");
      assertThat(jdbc.queryForList("SELECT * FROM sales_slips WHERE id = 900")).isEqualTo(oldSlip);
      assertThat(jdbc.queryForList("SELECT * FROM sales_slip_items WHERE id = 900"))
          .isEqualTo(oldItems);
      assertThat(
              jdbc.queryForList(
                  """
          SELECT convalidated FROM pg_constraint
          WHERE conname IN ('ck_sales_slip_items_amount', 'ck_sales_slips_total_amount')
          """,
                  Boolean.class))
          .containsExactly(false, false);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
          INSERT INTO sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount)
          VALUES (901, 900, '잘못된 새 품목', ?, 1500000000, ?)
          """,
                      quantity,
                      amount))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_sales_slip_items_amount");
      assertThatThrownBy(
              () -> jdbc.update("UPDATE sales_slip_items SET memo = '변경' WHERE id = 900"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_sales_slip_items_amount");
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
          INSERT INTO sales_slips (id, slip_number, sale_date, partner_id, total_amount,
              payment_status, sales_status, created_at, updated_at)
          VALUES (901, 'NEW-NEGATIVE', DATE '2026-10-03', 900, -1,
              '미입금', '작성중', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_sales_slips_total_amount");
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "ALTER TABLE sales_slip_items VALIDATE CONSTRAINT ck_sales_slip_items_amount"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(jdbc.queryForList("SELECT * FROM sales_slips WHERE id = 900")).isEqualTo(oldSlip);
      assertThat(jdbc.queryForList("SELECT * FROM sales_slip_items WHERE id = 900"))
          .isEqualTo(oldItems);
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      upgrade.validate();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
