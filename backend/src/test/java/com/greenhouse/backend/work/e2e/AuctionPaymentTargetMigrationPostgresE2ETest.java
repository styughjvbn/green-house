package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("work-e2e")
@Testcontainers
class AuctionPaymentTargetMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void preservesActualCashAndResultIdentifiersWithoutCopyingDerivedAmounts() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    var facts =
        jdbc.queryForList(
            "select to_jsonb(e)-'target_type'-'target_id' as fact from partner_payment_events e order by id");
    var results = jdbc.queryForList("select * from auction_result_lines order by id");
    var legacy = jdbc.queryForList("select * from auction_settlements order by id");
    var migration = Flyway.configure().dataSource(source).target("47").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(
            jdbc.queryForList(
                "select to_jsonb(e)-'target_type'-'target_id' as fact from partner_payment_events e order by id"))
        .isEqualTo(facts);
    assertThat(jdbc.queryForList("select * from auction_result_lines order by id"))
        .isEqualTo(results);
    assertThat(jdbc.queryForList("select * from auction_settlements order by id"))
        .isEqualTo(legacy);
    var alias = jdbc.queryForMap("select * from payment_target_aliases");
    assertThat(alias.get("original_target_id")).isEqualTo(906L);
    Long target = ((Number) alias.get("target_id")).longValue();
    var root = jdbc.queryForMap("select * from auction_proceeds where id=?", target);
    assertThat(root.get("source_reference")).isNull();
    assertThat(root.get("reported_gross_amount")).isNull();
    assertThat(root.get("receivable_amount")).isNull();
    assertThat(root.get("matching_confirmed")).isEqualTo(false);
    assertThat(root.get("confirmed_at")).isNull();
    assertThat(root.get("confirmed_by")).isNull();
    assertThat(
            jdbc.queryForList(
                "select target_type,target_id from partner_payment_events where id in (908,909)"))
        .allSatisfy(
            row -> {
              assertThat(row.get("target_type")).isEqualTo("AUCTION_PROCEEDS");
              assertThat(row.get("target_id")).isEqualTo(target);
            });
    assertThat(
            jdbc.queryForObject(
                "select auction_result_line_id from auction_proceeds_results where auction_proceeds_id=?",
                Long.class,
                target))
        .isEqualTo(905L);
    // A settlement with only a paid summary produces no cash, alias or copied target.
    assertThat(jdbc.queryForObject("select count(*) from auction_proceeds", Long.class))
        .isEqualTo(1);
  }

  @Test
  void missingOldTargetsAbortWithoutChangingAnyCash() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    jdbc.execute("update partner_payment_events set target_id=999999 where id=908");
    var before = jdbc.queryForList("select * from partner_payment_events order by id");
    assertThatThrownBy(() -> Flyway.configure().dataSource(source).target("47").load().migrate())
        .hasStackTraceContaining("Auction payment target is missing");
    assertThat(jdbc.queryForList("select * from partner_payment_events order by id"))
        .isEqualTo(before);
    assertThat(jdbc.queryForObject("select count(*) from auction_proceeds", Long.class)).isZero();
    assertThat(
            jdbc.queryForObject("select to_regclass('payment_target_aliases')::text", String.class))
        .isNull();
  }

  @Test
  void alreadyOwnedResultsAbortInsteadOfInferringAMerge() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    jdbc.execute(
        "insert into auction_proceeds (id,auction_house_id,created_at,updated_at) values (10000,901,current_timestamp,current_timestamp)");
    jdbc.execute("insert into auction_proceeds_results values (905,10000)");
    var before = jdbc.queryForList("select * from partner_payment_events order by id");
    assertThatThrownBy(() -> Flyway.configure().dataSource(source).target("47").load().migrate())
        .hasStackTraceContaining("already belong to a proceeds target");
    assertThat(jdbc.queryForList("select * from partner_payment_events order by id"))
        .isEqualTo(before);
    assertThat(jdbc.queryForObject("select count(*) from auction_proceeds", Long.class))
        .isEqualTo(1);
  }

  private DriverManagerDataSource database() {
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    String name = "payment_targets_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("create database " + name);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace(POSTGRES.getDatabaseName(), name),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    Flyway.configure().dataSource(source).target("46").load().migrate();
    return source;
  }

  private void seed(JdbcTemplate jdbc) {
    jdbc.execute(
        "insert into business_partners (id,name,partner_type,created_at,updated_at) values (901,'경매장','AUCTION_HOUSE',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into auction_shipments (id,shipment_date,auction_house_id,status,created_at,updated_at) values (902,date '2026-10-07',901,'SHIPPED',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into auction_shipment_lots (id,shipment_id,item_name,variety_name,shipped_quantity,sold_quantity,waiting_quantity,returned_quantity,current_status,created_at,updated_at) values (903,902,'난','품종',10,10,0,0,'SOLD',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into auction_attempts (id,shipment_lot_id,auction_date,attempt_no,attempt_status,created_at,updated_at) values (904,903,date '2026-10-07',1,'SOLD',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into auction_result_lines (id,auction_attempt_id,auction_date,quantity,unit_price,amount,inspection_status,created_at,updated_at) values (905,904,date '2026-10-07',10,100,1000,'NORMAL',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into auction_settlements (id,auction_house_id,auction_date,gross_amount,fee_amount,deduction_amount,expected_deposit_amount,paid_amount,remaining_amount,status,created_at,updated_at) values (906,901,date '2026-10-07',1000,7,13,980,900,80,'PARTIALLY_PAID',current_timestamp,current_timestamp),(907,901,date '2026-10-08',500,0,0,500,500,0,'PAID',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into auction_settlement_lines (id,settlement_id,auction_result_line_id,auction_shipment_lot_id,quantity,unit_price,amount,status,created_at,updated_at) values (910,906,905,903,10,100,1000,'UNPAID',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into partner_payment_events (id,partner_id,event_type,event_date,amount,unapplied_amount,target_type,target_id,status,external_uid,raw_payload,created_by,created_at,updated_at) values (908,901,'PAYMENT_RECEIVED',date '2026-10-07',400,0,'AUCTION_SETTLEMENT',906,'FULLY_APPLIED','MANUAL:AUCTION_SETTLEMENT:906:original-key','{\"original\":true}', '기존 담당자',current_timestamp,current_timestamp)");
    jdbc.execute(
        "insert into partner_payment_events (id,partner_id,event_type,event_date,amount,unapplied_amount,target_type,target_id,status,parent_event_id,created_at,updated_at) values (909,901,'MANUAL_MATCH_CONFIRMED',date '2026-10-07',400,0,'AUCTION_SETTLEMENT',906,'CONFIRMED',908,current_timestamp,current_timestamp)");
  }
}
