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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("work-e2e")
@Testcontainers
class AuctionArrivalMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void keepsLegacyReturnFactsWithoutInventingArrivalsAndEnforcesCompleteFarmLinks() {
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    String database = "arrivals_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("CREATE DATABASE " + database);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace(POSTGRES.getDatabaseName(), database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    var jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).target("44").load().migrate();
    jdbc.execute(
        "INSERT INTO business_partners (id, name, partner_type, created_at, updated_at) VALUES (901, '경매장', 'AUCTION_HOUSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    jdbc.execute(
        "INSERT INTO auction_shipments (id, shipment_date, auction_house_id, status, created_at, updated_at) VALUES (902, DATE '2026-10-07', 901, 'SHIPPED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    jdbc.execute(
        "INSERT INTO auction_shipment_lots (id, shipment_id, item_name, variety_name, shipped_quantity, sold_quantity, waiting_quantity, returned_quantity, current_status, return_confirmed_date, created_at, updated_at) VALUES (903, 902, '난', '품종', 40, 0, 10, 30, 'PARTIALLY_RETURNED', DATE '2026-10-07', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    var lots = jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id");
    var mutations = jdbc.queryForList("SELECT * FROM orchid_group_mutations ORDER BY id");
    var migration = Flyway.configure().dataSource(source).target("45").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id"))
        .isEqualTo(lots);
    assertThat(jdbc.queryForList("SELECT * FROM orchid_group_mutations ORDER BY id"))
        .isEqualTo(mutations);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_return_arrivals", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_follow_up_decisions", Long.class))
        .isZero();
    jdbc.execute(
        "INSERT INTO auction_follow_up_decisions (id, lot_id, method, quantity, decided_at) VALUES (905, 903, 'FARM_RETURN', 10, CURRENT_TIMESTAMP)");
    jdbc.execute(
        "INSERT INTO auction_return_arrivals (id, lot_id, decision_id, quantity, arrival_date, created_at) VALUES (904, 903, 905, 10, DATE '2026-10-07', CURRENT_TIMESTAMP)");
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "UPDATE auction_return_arrivals SET canceled_at = CURRENT_TIMESTAMP WHERE id = 904"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.execute("UPDATE auction_return_arrivals SET quantity = 0 WHERE id = 904"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "UPDATE auction_return_arrivals SET orchid_group_id = 999999 WHERE id = 904"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.execute("DELETE FROM auction_shipment_lots WHERE id = 903"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "INSERT INTO auction_follow_up_decisions (id, lot_id, method, quantity, decided_at) VALUES (906, 903, 'SPLIT', 10, CURRENT_TIMESTAMP)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
