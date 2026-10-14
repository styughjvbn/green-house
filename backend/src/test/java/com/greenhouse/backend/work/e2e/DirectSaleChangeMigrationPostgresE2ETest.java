package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("work-e2e")
@Testcontainers
class DirectSaleChangeMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void addsEmptyCommercialAndPhysicalLedgersAndPreservesAllExistingRows() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(source).target("40").load().migrate();
    var jdbc = new JdbcTemplate(source);
    jdbc.execute(
        """
        INSERT INTO business_partners(id,name,partner_type,created_at,updated_at)
        VALUES(900,'과거 거래처','WHOLESALE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slips(id,slip_number,sale_date,sales_type,partner_id,total_amount,paid_amount,remaining_amount,payment_status,sales_status,created_at,updated_at)
        VALUES(901,'SIGNED',DATE '2020-01-01','DIRECT',900,-100,0,0,'미입금','출고 완료',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slip_items(id,sales_slip_id,item_name,quantity,unit_price,amount) VALUES(902,901,'반품 표현',-1,100,-100);
        INSERT INTO direct_sales(sales_slip_id,partner_id,sale_date,total_amount,created_at,updated_at)
        VALUES(901,900,DATE '2020-01-01',-100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO direct_sale_prices(sales_slip_item_id,sales_slip_id,priced_quantity,unit_price,amount) VALUES(902,901,-1,100,-100);
        """);
    var tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename",
            String.class);
    var before = new LinkedHashMap<String, List<String>>();
    for (var table : tables)
      before.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    var migration = Flyway.configure().dataSource(source).target("41").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_return_arrivals", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM direct_return_command_receipts", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sale_changes", Long.class))
        .isZero();
    for (var table : tables)
      assertThat(
              jdbc.queryForList(
                  "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class))
          .as(table)
          .isEqualTo(before.get(table));
  }
}
