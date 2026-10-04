package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class AuctionCommandReceiptMigrationPostgresE2ETest extends WorkE2ETestBase {
  @Test
  void upgradesLegacyAuctionFactsWithoutInventingIdentitiesAndEnforcesReceiptConstraints() {
    String database = "auction_receipt_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
    var source = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(source).target("35").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          """
          INSERT INTO business_partners (id, name, partner_type, created_at, updated_at)
          VALUES (900, '기존 경매장', 'AUCTION_HOUSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      jdbc.execute(
          """
          INSERT INTO auction_shipments (id, shipment_date, auction_house_id, status, created_at, updated_at)
          VALUES (900, DATE '2026-10-03', 900, 'SHIPPED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      jdbc.execute(
          """
          INSERT INTO auction_shipment_lots (id, shipment_id, item_name, variety_name, shipped_quantity,
              sold_quantity, waiting_quantity, returned_quantity, current_status, return_confirmed_date, created_at, updated_at)
          VALUES (900, 900, '기존 난', '품종', 40, 10, 20, 10, 'PARTIALLY_RETURNED', DATE '2026-10-04', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      jdbc.execute(
          """
          INSERT INTO auction_attempts (id, shipment_lot_id, auction_date, attempt_no, attempt_status, created_at, updated_at)
          VALUES (900, 900, DATE '2026-10-03', 1, 'PARTIALLY_SOLD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      jdbc.execute(
          """
          INSERT INTO auction_result_lines (id, auction_attempt_id, auction_date, quantity, unit_price, amount, inspection_status, created_at, updated_at)
          VALUES (900, 900, DATE '2026-10-03', 10, 1000, 10000, 'NORMAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      jdbc.execute(
          """
          INSERT INTO auction_lot_status_history (id, shipment_lot_id, previous_status, new_status, changed_at, reason)
          VALUES (900, 900, 'REAUCTION_WAITING', 'PARTIALLY_RETURNED', CURRENT_TIMESTAMP, '기존 부분 반환')
          """);
      var before = snapshot(jdbc);
      var upgrade = Flyway.configure().dataSource(source).target("36").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("36");
      assertThat(snapshot(jdbc)).isEqualTo(before);
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM auction_command_receipts", Integer.class))
          .isZero();
      insert(jdbc, 1, 900, "RESULT", "key", "a".repeat(64), "{\"id\":900}");
      rejects(
          () -> insert(jdbc, 2, 900, "RESULT", "key", "b".repeat(64), "{\"id\":900}"),
          "uk_auction_command_receipt");
      rejects(
          () -> insert(jdbc, 2, 901, "RESULT", "key", "a".repeat(64), "{\"id\":901}"),
          "auction_command_receipts_lot_id_fkey");
      rejects(
          () -> insert(jdbc, 2, 900, "OTHER", "new", "a".repeat(64), "{\"id\":900}"),
          "ck_auction_command_type");
      rejects(
          () -> insert(jdbc, 2, 900, "RESULT", " ", "a".repeat(64), "{\"id\":900}"),
          "ck_auction_command_key");
      rejects(
          () -> insert(jdbc, 2, 900, "RESULT", "new", "invalid", "{\"id\":900}"),
          "ck_auction_command_fingerprint");
      rejects(
          () -> insert(jdbc, 2, 900, "RESULT", "new", "a".repeat(64), "{}"),
          "ck_auction_command_response");
      rejects(
          () -> insert(jdbc, 2, 900, "RESULT", "new", "a".repeat(64), "{\"id\":901}"),
          "ck_auction_command_response");
      rejects(
          () -> insert(jdbc, 2, 900, "RESULT", "new", "a".repeat(64), null), "response_snapshot");
      insert(jdbc, 2, 900, "RETURN", "key", "a".repeat(64), "{\"id\":900}");
      assertThat(snapshot(jdbc)).isEqualTo(before);
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      upgrade.validate();
      // Remove the legacy facts only inside this disposable database to isolate the new FK.
      jdbc.execute("DELETE FROM auction_result_lines WHERE id = 900");
      jdbc.execute("DELETE FROM auction_attempts WHERE id = 900");
      jdbc.execute("DELETE FROM auction_lot_status_history WHERE id = 900");
      rejects(
          () -> jdbc.execute("DELETE FROM auction_shipment_lots WHERE id = 900"),
          "auction_command_receipts_lot_id_fkey");
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  private void insert(
      JdbcTemplate jdbc,
      long id,
      long lotId,
      String type,
      String key,
      String fingerprint,
      String response) {
    jdbc.update(
        """
        INSERT INTO auction_command_receipts (id, lot_id, command_type, request_key, request_fingerprint, response_snapshot, created_at)
        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CURRENT_TIMESTAMP)
        """,
        id,
        lotId,
        type,
        key,
        fingerprint,
        response);
  }

  private void rejects(Runnable action, String constraint) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  private Map<String, List<String>> snapshot(JdbcTemplate jdbc) {
    var result = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_lot_status_history")) {
      result.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    }
    return result;
  }
}
