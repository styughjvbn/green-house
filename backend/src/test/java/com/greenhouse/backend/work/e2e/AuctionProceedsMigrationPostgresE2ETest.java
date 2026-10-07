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
class AuctionProceedsMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void preparesEmptyTargetsWithoutCopyingSettlementsAndProtectsEvidenceReferences() {
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    String database = "proceeds_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("CREATE DATABASE " + database);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace(POSTGRES.getDatabaseName(), database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    var jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).target("43").load().migrate();
    var payments = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
    var settlements = jdbc.queryForList("SELECT * FROM auction_settlements ORDER BY id");
    var migration = Flyway.configure().dataSource(source).target("44").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
        .isEqualTo(payments);
    assertThat(jdbc.queryForList("SELECT * FROM auction_settlements ORDER BY id"))
        .isEqualTo(settlements);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_proceeds", Long.class)).isZero();
    jdbc.execute(
        "INSERT INTO business_partners (id, name, partner_type, is_active, created_at, updated_at) VALUES (901, '경매장', 'AUCTION_HOUSE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    jdbc.execute(
        "INSERT INTO auction_proceeds (id, auction_house_id, source_reference, reported_gross_amount, created_at, updated_at) VALUES (902, 901, '결과 자료', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    assertThat(
            jdbc.queryForObject(
                "SELECT receivable_amount FROM auction_proceeds WHERE id = 902", Long.class))
        .isNull();
    assertThatThrownBy(
            () -> jdbc.execute("UPDATE auction_proceeds SET receivable_amount = -1 WHERE id = 902"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "UPDATE auction_proceeds SET matching_confirmed = true WHERE id = 902"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.execute("INSERT INTO auction_proceeds_results VALUES (999999, 902)"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.execute("DELETE FROM business_partners WHERE id = 901"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
