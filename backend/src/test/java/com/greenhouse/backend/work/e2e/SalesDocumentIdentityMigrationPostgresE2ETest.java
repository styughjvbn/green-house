package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
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
class SalesDocumentIdentityMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void removesNumberingAndPreservesDocumentIdsReferencesAndHistoricalSnapshots() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(source).target("39").load().migrate();
    var jdbc = new JdbcTemplate(source);
    jdbc.execute(
        """
        INSERT INTO business_partners(id,name,partner_type,created_at,updated_at)
        VALUES(900,'기존 거래처','WHOLESALE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slips(id,slip_number,sale_date,sales_type,partner_id,total_amount,
          paid_amount,remaining_amount,payment_status,sales_status,created_at,updated_at)
        VALUES(901,'S20261014-001',DATE '2026-10-14','DIRECT',900,100,0,100,'미입금','출고 완료',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slip_items(id,sales_slip_id,item_name,quantity,unit_price,amount)
        VALUES(902,901,'기존 품목',1,100,100);
        INSERT INTO direct_sales(sales_slip_id,partner_id,sale_date,total_amount,created_at,updated_at)
        VALUES(901,900,DATE '2026-10-14',100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO direct_sale_prices(sales_slip_item_id,sales_slip_id,priced_quantity,unit_price,amount)
        VALUES(902,901,1,100,100);
        INSERT INTO sales_creation_receipts(request_key,request_fingerprint,sales_slip_id,response_snapshot,created_at)
        VALUES('old-create',repeat('a',64),901,'{"id":901,"slipNumber":"S20261014-001"}',CURRENT_TIMESTAMP);
        INSERT INTO sales_slip_daily_sequences(sale_date,last_value) VALUES(DATE '2026-10-14',1);
        """);
    var tables =
        jdbc.queryForList(
            """
        SELECT tablename FROM pg_tables WHERE schemaname='public'
        AND tablename NOT IN ('flyway_schema_history','sales_slip_daily_sequences') ORDER BY tablename
        """,
            String.class);
    var before = new LinkedHashMap<String, List<String>>();
    for (var table : tables) before.put(table, rows(jdbc, table));
    var migration = Flyway.configure().dataSource(source).target("40").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(
            jdbc.queryForObject("SELECT to_regclass('sales_slip_daily_sequences')", String.class))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                """
        SELECT count(*) FROM information_schema.columns
        WHERE table_schema='public' AND table_name='sales_slips' AND column_name='slip_number'
        """,
                Long.class))
        .isZero();
    for (var table : tables) assertThat(rows(jdbc, table)).as(table).isEqualTo(before.get(table));
  }

  private List<String> rows(JdbcTemplate jdbc, String table) {
    String value =
        switch (table) {
          case "sales_slips" -> "to_jsonb(row) - 'slip_number'";
          case "sales_slip_items" -> "to_jsonb(row) - 'unit' - 'boxes'";
          case "partner_settlement_settings" ->
              "to_jsonb(row) - 'print_template' - 'shipper_code' - 'shipper_name'";
          default -> "to_jsonb(row)";
        };
    return jdbc.queryForList(
        "SELECT (" + value + ")::text FROM " + table + " row ORDER BY 1", String.class);
  }
}
