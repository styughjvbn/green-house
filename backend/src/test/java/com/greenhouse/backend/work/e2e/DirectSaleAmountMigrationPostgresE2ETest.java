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
  void copiesStoredFactsAndReportsDiscrepanciesWithoutManufacturingPayments() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          migrate(jdbc, "42");
          var slips = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
          var items = jdbc.queryForList("SELECT * FROM sales_slip_items ORDER BY id");
          var payments = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
          var balances = jdbc.queryForList("SELECT * FROM partner_balance_summaries ORDER BY id");

          assertThat(migrate(jdbc, "43").migrate().migrationsExecuted).isZero();
          assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id")).isEqualTo(slips);
          assertThat(jdbc.queryForList("SELECT * FROM sales_slip_items ORDER BY id"))
              .isEqualTo(items);
          assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
              .isEqualTo(payments);
          assertThat(jdbc.queryForList("SELECT * FROM partner_balance_summaries ORDER BY id"))
              .isEqualTo(balances);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM direct_sales WHERE sales_slip_id = 905", Long.class))
              .isZero();
          assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sales", Long.class))
              .isEqualTo(11);
          assertThat(
                  jdbc.queryForObject(
                      """
                      SELECT count(*) FROM direct_sales sale JOIN sales_slips slip ON slip.id = sale.sales_slip_id
                      WHERE sale.total_amount IS DISTINCT FROM slip.total_amount
                         OR sale.sale_date IS DISTINCT FROM slip.sale_date
                         OR sale.partner_id IS DISTINCT FROM slip.partner_id
                         OR sale.expected_payment_date IS DISTINCT FROM slip.expected_payment_date
                         OR sale.payment_method IS DISTINCT FROM slip.payment_method
                         OR sale.created_at IS DISTINCT FROM slip.created_at
                         OR sale.updated_at IS DISTINCT FROM slip.updated_at
                      """,
                      Long.class))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      """
                      SELECT count(*) FROM direct_sale_prices price JOIN sales_slip_items item
                          ON item.id = price.sales_slip_item_id
                      WHERE price.sales_slip_id <> item.sales_slip_id OR price.priced_quantity <> item.quantity
                          OR price.unit_price <> item.unit_price OR price.amount <> item.amount
                      """,
                      Long.class))
              .isZero();

          assertReview(jdbc, 900, 400, false, false, false, false, false, false);
          assertReview(jdbc, 901, 0, true, false, false, false, false, false);
          assertReview(jdbc, 902, 0, false, false, true, false, false, true);
          assertReview(jdbc, 903, 0, false, true, false, false, false, true);
          assertReview(jdbc, 904, 0, false, false, true, true, false, false);
          assertReview(jdbc, 906, 0, false, false, false, false, false, false);
          assertReview(jdbc, 907, 0, false, false, true, true, true, false);
          // A duplicated link must never count the same received money twice.
          assertReview(jdbc, 908, 400, false, false, false, false, true, false);
          assertReview(jdbc, 909, 0, false, false, true, true, true, false);
          assertReview(jdbc, 910, 0, false, false, true, true, false, false);
          assertReview(jdbc, 911, 0, false, false, false, false, true, false);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT stored_paid_amount FROM direct_sale_amount_reconciliations WHERE sales_slip_id = 902",
                      Long.class))
              .isEqualTo(-100);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT amount FROM direct_sale_prices WHERE sales_slip_item_id = 903",
                      Integer.class))
              .isEqualTo(-1294967296);
          migrate(jdbc, "43").validate();
        });
  }

  @Test
  void protectsDocumentAndItemIdentityWithoutDuplicatingPhysicalAllocations() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          migrate(jdbc, "43");
          jdbc.execute(
              """
              INSERT INTO sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount)
              VALUES (9997, 901, '새 품목', 1, 1000, 1000)
              """);
          assertThatThrownBy(
                  () ->
                      jdbc.execute(
                          """
                      INSERT INTO direct_sale_prices (sales_slip_item_id, sales_slip_id, priced_quantity, unit_price, amount)
                      VALUES (9997, 900, 1, 1000, 1000)
                      """))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("fk_direct_price_document_item");
          assertThatThrownBy(() -> jdbc.update("DELETE FROM sales_slips WHERE id = 900"))
              .isInstanceOf(DataIntegrityViolationException.class);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM sales_slip_item_allocations", Long.class))
              .isZero();
        });
  }

  @Test
  void appliesToAnEmptyDatabaseAndIsNotReapplied() {
    inDatabase(
        jdbc -> {
          migrate(jdbc, "43");
          assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sales", Long.class)).isZero();
          assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sale_prices", Long.class))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM direct_sale_amount_reconciliations", Long.class))
              .isZero();
          assertThat(migrate(jdbc, "43").migrate().migrationsExecuted).isZero();
        });
  }

  @Test
  void preservesLegacyDisplayLabelsWithoutChangingAnyMonetaryFacts() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          jdbc.execute("UPDATE sales_slips SET payment_status='  입금 보류  ' WHERE id=906");
          jdbc.execute("UPDATE sales_slips SET payment_status='과거 정정 검토' WHERE id=904");
          migrate(jdbc, "47");
          var documents = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
          var prices =
              jdbc.queryForList("SELECT * FROM direct_sale_prices ORDER BY sales_slip_item_id");
          var cash = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
          var evidence =
              jdbc.queryForList(
                  "SELECT * FROM direct_sale_amount_reconciliations ORDER BY sales_slip_id");
          migrate(jdbc, "48");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT unpaid_payment_label FROM direct_sales WHERE sales_slip_id=906",
                      String.class))
              .isEqualTo("  입금 보류  ");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT unpaid_payment_label FROM direct_sales WHERE sales_slip_id=904",
                      String.class))
              .isEqualTo("과거 정정 검토");
          assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id"))
              .isEqualTo(documents);
          assertThat(
                  jdbc.queryForList("SELECT * FROM direct_sale_prices ORDER BY sales_slip_item_id"))
              .isEqualTo(prices);
          assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
              .isEqualTo(cash);
          assertThat(
                  jdbc.queryForList(
                      "SELECT * FROM direct_sale_amount_reconciliations ORDER BY sales_slip_id"))
              .isEqualTo(evidence);
          assertThat(migrate(jdbc, "48").migrate().migrationsExecuted).isZero();
        });
  }

  @Test
  void refreshesProjectionsWithoutChangingOwnedFactsOriginalEvidenceOrCash() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          migrate(jdbc, "48");
          var owners = jdbc.queryForList("SELECT * FROM direct_sales ORDER BY sales_slip_id");
          var prices =
              jdbc.queryForList("SELECT * FROM direct_sale_prices ORDER BY sales_slip_item_id");
          var cash = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
          String evidenceSql =
              "SELECT sales_slip_id, stored_paid_amount, stored_remaining_amount, stored_payment_status, stored_item_amount_sum, confirmed_allocation_amount, total_mismatch, price_mismatch, paid_mismatch, remaining_mismatch, ledger_review_required, signed_amount_review_required FROM direct_sale_amount_reconciliations ORDER BY sales_slip_id";
          var original = jdbc.queryForList(evidenceSql);
          jdbc.update(
              "UPDATE sales_slips SET total_amount=700, paid_amount=100, remaining_amount=600 WHERE id=901");
          jdbc.update("UPDATE sales_slip_items SET unit_price=700, amount=700 WHERE id=901");
          var physical =
              jdbc.queryForList(
                  "SELECT id, version, sale_date, partner_id, created_at, updated_at FROM sales_slips ORDER BY id");
          var auction = jdbc.queryForMap("SELECT * FROM sales_slips WHERE id=905");
          migrate(jdbc, "49");
          assertThat(jdbc.queryForList("SELECT * FROM direct_sales ORDER BY sales_slip_id"))
              .isEqualTo(owners);
          assertThat(
                  jdbc.queryForList("SELECT * FROM direct_sale_prices ORDER BY sales_slip_item_id"))
              .isEqualTo(prices);
          assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
              .isEqualTo(cash);
          assertThat(
                  jdbc.queryForList(
                      "SELECT id, version, sale_date, partner_id, created_at, updated_at FROM sales_slips ORDER BY id"))
              .isEqualTo(physical);
          assertThat(jdbc.queryForMap("SELECT * FROM sales_slips WHERE id=905")).isEqualTo(auction);
          assertThat(jdbc.queryForList(evidenceSql)).isEqualTo(original);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT cutover_review_required FROM direct_sale_amount_reconciliations WHERE sales_slip_id=901",
                      Boolean.class))
              .isTrue();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT (cutover_legacy_snapshot->>'paidAmount')::bigint FROM direct_sale_amount_reconciliations WHERE sales_slip_id=901",
                      Long.class))
              .isEqualTo(100);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT (cutover_legacy_snapshot->'items'->0->>'amount')::int FROM direct_sale_amount_reconciliations WHERE sales_slip_id=901",
                      Integer.class))
              .isEqualTo(700);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT total_amount FROM sales_slips WHERE id=901", Integer.class))
              .isEqualTo(777);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT amount FROM sales_slip_items WHERE id=901", Integer.class))
              .isEqualTo(1000);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT paid_amount FROM sales_slips WHERE id=908", Long.class))
              .isEqualTo(400);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT paid_amount FROM sales_slips WHERE id=909", Long.class))
              .isZero();
          assertThat(migrate(jdbc, "49").migrate().migrationsExecuted).isZero();
        });
  }

  @Test
  void missingSourceAbortsCutoverWithoutChangingOriginalFacts() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          migrate(jdbc, "48");
          jdbc.update("DELETE FROM direct_sale_amount_reconciliations WHERE sales_slip_id=900");
          jdbc.update("DELETE FROM direct_sale_prices WHERE sales_slip_id=900");
          jdbc.update("DELETE FROM direct_sales WHERE sales_slip_id=900");
          var documents = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
          var cash = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
          assertThatThrownBy(() -> migrate(jdbc, "49"))
              .hasStackTraceContaining("Direct financial source is missing");
          assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id"))
              .isEqualTo(documents);
          assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
              .isEqualTo(cash);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM information_schema.columns WHERE table_name='direct_sale_amount_reconciliations' AND column_name='cutover_review_required'",
                      Long.class))
              .isZero();
        });
  }

  @Test
  void capturesReviewEvidenceForSalesCreatedAfterTheInitialExpansion() {
    inDatabase(
        jdbc -> {
          seedLegacyFacts(jdbc);
          migrate(jdbc, "48");
          // A post-expansion sale has owner terms but no original migration reconciliation.
          jdbc.update("DELETE FROM direct_sale_amount_reconciliations WHERE sales_slip_id=906");
          jdbc.update("UPDATE sales_slips SET paid_amount=50,remaining_amount=950 WHERE id=906");
          migrate(jdbc, "49");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT cutover_review_required FROM direct_sale_amount_reconciliations WHERE sales_slip_id=906",
                      Boolean.class))
              .isTrue();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT stored_paid_amount FROM direct_sale_amount_reconciliations WHERE sales_slip_id=906",
                      Long.class))
              .isEqualTo(50);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT paid_amount FROM sales_slips WHERE id=906", Long.class))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM partner_payment_events WHERE target_id=906",
                      Long.class))
              .isZero();
        });
  }

  private void assertReview(JdbcTemplate jdbc, long id, long allocation, boolean... flags) {
    var row =
        jdbc.queryForMap(
            "SELECT * FROM direct_sale_amount_reconciliations WHERE sales_slip_id = ?", id);
    assertThat(row.get("confirmed_allocation_amount")).isEqualTo(allocation);
    assertThat(
            new Object[] {
              row.get("total_mismatch"),
              row.get("price_mismatch"),
              row.get("paid_mismatch"),
              row.get("remaining_mismatch"),
              row.get("ledger_review_required"),
              row.get("signed_amount_review_required")
            })
        .containsExactly(flags[0], flags[1], flags[2], flags[3], flags[4], flags[5]);
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
