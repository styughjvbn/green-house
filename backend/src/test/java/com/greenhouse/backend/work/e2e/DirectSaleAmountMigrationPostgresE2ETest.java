package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.function.Consumer;
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
class DirectSaleAmountMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void copiesOriginalTermsAndPricesProjectsValidAllocationsAndNeverCreatesReviewOrCashFacts() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          migrate(jdbc, "36");
          jdbc.execute("UPDATE sales_slips SET payment_status='  입금 보류  ' WHERE id=906");
          var prices =
              jdbc.queryForList(
                  "SELECT id,sales_slip_id,quantity,unit_price,amount FROM sales_slip_items ORDER BY id");
          var cash = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
          var times =
              jdbc.queryForList("SELECT id,created_at,updated_at FROM sales_slips ORDER BY id");
          migrate(jdbc, "37").validate();
          assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sales", Long.class))
              .isEqualTo(11);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM direct_sales WHERE sales_slip_id=905", Long.class))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT to_regclass('direct_sale_amount_reconciliations')::text",
                      String.class))
              .isNull();
          assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
              .isEqualTo(cash);
          assertThat(
                  jdbc.queryForList("SELECT id,created_at,updated_at FROM sales_slips ORDER BY id"))
              .isEqualTo(times);
          assertThat(
                  jdbc.queryForList(
                      "SELECT id,sales_slip_id,quantity,unit_price,amount FROM sales_slip_items ORDER BY id"))
              .isEqualTo(prices);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM direct_sales sale JOIN sales_slips slip ON slip.id=sale.sales_slip_id WHERE sale.total_amount IS DISTINCT FROM slip.total_amount OR sale.partner_id IS DISTINCT FROM slip.partner_id OR sale.sale_date IS DISTINCT FROM slip.sale_date OR sale.created_at IS DISTINCT FROM slip.created_at OR sale.updated_at IS DISTINCT FROM slip.updated_at",
                      Long.class))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM direct_sale_prices p JOIN sales_slip_items i ON i.id=p.sales_slip_item_id WHERE p.priced_quantity IS DISTINCT FROM i.quantity OR p.unit_price IS DISTINCT FROM i.unit_price OR p.amount IS DISTINCT FROM i.amount",
                      Long.class))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT unpaid_payment_label FROM direct_sales WHERE sales_slip_id=906",
                      String.class))
              .isEqualTo("  입금 보류  ");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT payment_status FROM sales_slips WHERE id=906", String.class))
              .isEqualTo("  입금 보류  ");
          for (long id : new long[] {900, 908})
            assertThat(
                    jdbc.queryForObject(
                        "SELECT paid_amount FROM sales_slips WHERE id=?", Long.class, id))
                .isEqualTo(400);
          for (long id : new long[] {901, 902, 903, 904, 906, 907, 909, 910, 911})
            assertThat(
                    jdbc.queryForObject(
                        "SELECT paid_amount FROM sales_slips WHERE id=?", Long.class, id))
                .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT total_amount FROM direct_sales WHERE sales_slip_id=902",
                      Integer.class))
              .isEqualTo(-100);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT amount FROM direct_sale_prices WHERE sales_slip_item_id=903",
                      Integer.class))
              .isEqualTo(-1294967296);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM sales_slip_item_allocations", Long.class))
              .isZero();
          assertThat(migrate(jdbc, "37").migrate().migrationsExecuted).isZero();
        });
  }

  @Test
  void protectsDocumentAndItemIdentityWithoutDuplicatingPhysicalAllocations() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          migrate(jdbc, "37");
          jdbc.execute(
              "INSERT INTO sales_slip_items(id,sales_slip_id,item_name,quantity,unit_price,amount) VALUES(9997,901,'새 품목',1,1000,1000)");
          assertThatThrownBy(
                  () -> jdbc.execute("INSERT INTO direct_sale_prices VALUES(9997,900,1,1000,1000)"))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("fk_direct_price_document_item");
          assertThatThrownBy(() -> jdbc.execute("DELETE FROM sales_slips WHERE id=900"))
              .isInstanceOf(DataIntegrityViolationException.class);
        });
  }

  @Test
  void appliesToEmptyDatabaseWithoutInventingSources() {
    inDatabase(
        jdbc -> {
          migrate(jdbc, "37");
          assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sales", Long.class)).isZero();
          assertThat(migrate(jdbc, "37").migrate().migrationsExecuted).isZero();
        });
  }

  private void seedLegacyFacts(JdbcTemplate jdbc) {
    jdbc.execute(
        """
        INSERT INTO business_partners (id, name, created_at, updated_at)
        VALUES (900, '금액 이전 거래처', TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2021-01-01 00:00:00');
        INSERT INTO sales_slips (id, slip_number, sale_date, sales_type, partner_id,
            total_amount, paid_amount, remaining_amount, payment_status, sales_status,
            payment_method, expected_payment_date, created_at, updated_at)
        SELECT n, 'MIGRATION-' || n, DATE '2020-01-01', CASE WHEN n = 905 THEN 'AUCTION' WHEN n = 906 THEN NULL ELSE 'DIRECT' END,
            900, CASE n WHEN 901 THEN 777 WHEN 902 THEN -100 WHEN 903 THEN -1294967296 ELSE 1000 END,
            CASE n WHEN 902 THEN -100 WHEN 904 THEN 100 WHEN 910 THEN NULL WHEN 900 THEN 400 WHEN 907 THEN 400 WHEN 908 THEN 400 WHEN 909 THEN 400 ELSE 0 END,
            CASE n WHEN 901 THEN 777 WHEN 902 THEN 0 WHEN 903 THEN 0 WHEN 904 THEN 900 WHEN 910 THEN NULL WHEN 900 THEN 600 WHEN 907 THEN 600 WHEN 908 THEN 600 WHEN 909 THEN 600 ELSE 1000 END,
            '미입금', '작성중', CASE WHEN n = 906 THEN NULL ELSE '계좌이체' END, DATE '2020-02-01',
            TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2021-01-01 00:00:00'
        FROM generate_series(900, 911) n;
        INSERT INTO sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount)
        SELECT n, n, '기존 품목', CASE WHEN n = 902 THEN -1 WHEN n = 903 THEN 2 ELSE 1 END,
            CASE WHEN n = 902 THEN 100 WHEN n = 903 THEN 1500000000 ELSE 1000 END,
            CASE WHEN n = 902 THEN -100 WHEN n = 903 THEN -1294967296 ELSE 1000 END
        FROM generate_series(900, 911) n;
        INSERT INTO partner_payment_events (id, partner_id, event_type, event_date, amount, unapplied_amount,
            target_type, target_id, parent_event_id, status, external_uid, created_at, updated_at)
        VALUES (9000, 900, 'PAYMENT_RECEIVED', DATE '2020-01-02', 400, 0, 'SALES_SLIP', 900, NULL, 'FULLY_APPLIED', 'legacy-900', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9001, 900, 'MANUAL_MATCH_CONFIRMED', DATE '2020-01-02', 400, 0, 'SALES_SLIP', 900, 9000, 'CONFIRMED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9070, 900, 'PAYMENT_RECEIVED', DATE '2020-01-02', 400, 0, 'SALES_SLIP', 907, NULL, 'FULLY_APPLIED', 'legacy-907', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9080, 900, 'PAYMENT_RECEIVED', DATE '2020-01-02', 400, 0, 'SALES_SLIP', 908, NULL, 'FULLY_APPLIED', 'legacy-908', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9081, 900, 'MANUAL_MATCH_CONFIRMED', DATE '2020-01-02', 400, 0, 'SALES_SLIP', 908, 9080, 'CONFIRMED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9082, 900, 'MANUAL_MATCH_CONFIRMED', DATE '2020-01-02', 400, 0, 'SALES_SLIP', 908, 9080, 'CONFIRMED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9090, 900, 'PAYMENT_RECEIVED', DATE '2020-01-02', 300, 0, 'SALES_SLIP', 909, NULL, 'FULLY_APPLIED', 'legacy-909', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9091, 900, 'MANUAL_MATCH_CONFIRMED', DATE '2020-01-02', 400, 0, 'SALES_SLIP', 909, 9090, 'CONFIRMED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            (9110, 900, 'ADJUSTMENT', DATE '2020-01-02', 100, 0, 'SALES_SLIP', 911, NULL, 'CONFIRMED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        """);
  }

  private Flyway migrate(JdbcTemplate jdbc, String target) {
    var flyway = Flyway.configure().dataSource(jdbc.getDataSource()).target(target).load();
    flyway.migrate();
    return flyway;
  }

  private void inDatabase(Consumer<JdbcTemplate> test) {
    String database = "direct_sale_migration_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    try {
      migrate(jdbc, "34");
      test.accept(jdbc);
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
