package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
class AuctionDocumentAmountMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void backfillsOnlyLinkedAuctionGrossPreservingEveryOtherFact() {
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    String name = "gross_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("CREATE DATABASE " + name);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace(POSTGRES.getDatabaseName(), name),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    Flyway.configure().dataSource(source).target("55").load().migrate();
    var jdbc = new JdbcTemplate(source);
    jdbc.execute(
        """
      INSERT INTO business_partners (id,name,partner_type,created_at,updated_at)
      VALUES (99000,'집계 경매장','AUCTION_HOUSE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
      INSERT INTO auction_shipments (id,auction_house_id,shipment_date,status,created_at,updated_at)
      VALUES (99001,99000,DATE '2026-10-01','SHIPPED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
      INSERT INTO auction_shipment_lots (id,shipment_id,item_name,variety_name,shipped_quantity,sold_quantity,waiting_quantity,returned_quantity,current_status,created_at,updated_at)
      VALUES (99002,99001,'난','품종1',10,4,6,0,'PARTIALLY_SOLD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99003,99001,'난','품종2',10,10,0,0,'SOLD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99004,99001,'난','미연결',10,10,0,0,'SOLD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
      INSERT INTO auction_attempts (id,shipment_lot_id,auction_date,attempt_no,attempt_status,created_at,updated_at)
      VALUES (99005,99002,DATE '2026-10-02',1,'PARTIALLY_SOLD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99006,99003,DATE '2026-10-03',1,'SOLD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99007,99004,DATE '2026-10-03',1,'SOLD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
      INSERT INTO auction_result_lines (id,auction_attempt_id,auction_date,quantity,unit_price,amount,inspection_status,created_at,updated_at)
      VALUES (99008,99005,DATE '2026-10-02',4,1000,4000,'NORMAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99009,99005,DATE '2026-10-02',6,0,0,'NORMAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99010,99006,DATE '2026-10-03',10,2000,20000,'NORMAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99011,99007,DATE '2026-10-03',10,9000,90000,'NORMAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
      INSERT INTO sales_slips (id,slip_number,sale_date,sales_type,auction_shipment_id,partner_id,total_amount,paid_amount,remaining_amount,payment_status,sales_status,historical_auction_import,created_at,updated_at)
      VALUES (99012,'HIST-AUC-99001',DATE '2026-10-01','AUCTION',99001,99000,0,123,456,'정산 대기','출하 완료',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99013,'EMPTY',DATE '2026-10-01','AUCTION',NULL,99000,1234,0,0,'정산 대기','작성중',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
             (99014,'DIRECT',DATE '2026-10-01','DIRECT',NULL,99000,3000,1000,2000,'부분입금','출고 완료',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
      INSERT INTO sales_slip_items (id,sales_slip_id,auction_shipment_lot_id,item_name,quantity,unit_price,amount)
      VALUES (99015,99012,99002,'품종1',10,0,0),(99016,99012,99003,'품종2',10,0,0);
      """);
    var documents = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
    var tables =
        List.of(
            "sales_slip_items",
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines");
    var facts =
        tables.stream()
            .map(table -> jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"))
            .toList();
    var migration = Flyway.configure().dataSource(source).load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    documents.get(0).put("total_amount", 24000);
    documents.get(1).put("total_amount", 0);
    assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id")).isEqualTo(documents);
    for (int i = 0; i < tables.size(); i++)
      assertThat(jdbc.queryForList("SELECT * FROM " + tables.get(i) + " ORDER BY id"))
          .isEqualTo(facts.get(i));
  }
}
