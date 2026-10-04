package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class AuctionQuantityHistoryMigrationPostgresE2ETest extends WorkE2ETestBase {
  @Test
  void upgradesWithoutInventingLegacyQuantitiesAndChecksCompleteNonNegativeSnapshots() {
    String database = "auction_history_" + UUID.randomUUID().toString().replace("-", "");
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
      Flyway.configure().dataSource(source).target("36").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          "INSERT INTO business_partners (id, name, partner_type, created_at, updated_at) VALUES (900, '기존 경매장', 'AUCTION_HOUSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      jdbc.execute(
          "INSERT INTO auction_shipments (id, shipment_date, auction_house_id, status, created_at, updated_at) VALUES (900, DATE '2026-10-04', 900, 'SHIPPED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      jdbc.execute(
          "INSERT INTO auction_shipment_lots (id, shipment_id, item_name, variety_name, shipped_quantity, sold_quantity, waiting_quantity, returned_quantity, current_status, created_at, updated_at) VALUES (900, 900, '난', '품종', 40, 0, 20, 20, 'PARTIALLY_RETURNED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      jdbc.execute(
          "INSERT INTO auction_lot_status_history (id, shipment_lot_id, previous_status, new_status, changed_at, reason) VALUES (900, 900, 'PARTIALLY_RETURNED', 'PARTIALLY_RETURNED', CURRENT_TIMESTAMP, '기존 이력')");
      jdbc.execute(
          "INSERT INTO auction_command_receipts (id, lot_id, command_type, request_key, request_fingerprint, response_snapshot, created_at) VALUES (900, 900, 'RETURN', 'old', repeat('a', 64), '{\"id\":900,\"statusHistory\":[]}', CURRENT_TIMESTAMP)");
      var lotBefore =
          jdbc.queryForObject(
              "SELECT to_jsonb(r)::text FROM auction_shipment_lots r WHERE id = 900", String.class);
      var receiptBefore =
          jdbc.queryForObject(
              "SELECT to_jsonb(r)::text FROM auction_command_receipts r WHERE id = 900",
              String.class);
      var historyBefore =
          jdbc.queryForObject(
              "SELECT to_jsonb(r)::text FROM auction_lot_status_history r WHERE id = 900",
              String.class);
      var upgrade = Flyway.configure().dataSource(source).target("37").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT to_jsonb(r)::text FROM auction_shipment_lots r WHERE id = 900",
                  String.class))
          .isEqualTo(lotBefore);
      assertThat(
              jdbc.queryForObject(
                  "SELECT to_jsonb(r)::text FROM auction_command_receipts r WHERE id = 900",
                  String.class))
          .isEqualTo(receiptBefore);
      assertThat(
              jdbc.queryForObject(
                  "SELECT (to_jsonb(r) - 'previous_sold_quantity' - 'new_sold_quantity' - 'previous_waiting_quantity' - 'new_waiting_quantity' - 'previous_returned_quantity' - 'new_returned_quantity')::text FROM auction_lot_status_history r WHERE id = 900",
                  String.class))
          .isEqualTo(historyBefore);
      assertThat(
              jdbc.queryForObject(
                  "SELECT num_nonnulls(previous_sold_quantity, new_sold_quantity, previous_waiting_quantity, new_waiting_quantity, previous_returned_quantity, new_returned_quantity) FROM auction_lot_status_history WHERE id = 900",
                  Integer.class))
          .isZero();
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE auction_lot_status_history SET previous_sold_quantity = 0 WHERE id = 900"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_auction_history_quantities");
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE auction_lot_status_history SET previous_sold_quantity = 0, new_sold_quantity = 0, previous_waiting_quantity = 30, new_waiting_quantity = 20, previous_returned_quantity = 10, new_returned_quantity = -1 WHERE id = 900"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_auction_history_quantities");
      jdbc.update(
          "UPDATE auction_lot_status_history SET previous_sold_quantity = 0, new_sold_quantity = 0, previous_waiting_quantity = 30, new_waiting_quantity = 20, previous_returned_quantity = 10, new_returned_quantity = 20 WHERE id = 900");
      // An explicit mismatch fact may have different totals; snapshots record facts, not repairs.
      jdbc.update("UPDATE auction_lot_status_history SET new_sold_quantity = 50 WHERE id = 900");
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      upgrade.validate();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
