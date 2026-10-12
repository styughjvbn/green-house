package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
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
class DirectSaleReconciliationRemovalPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void removesOnlyMigrationReviewEvidenceAndPreservesEveryOtherTable() {
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    String name = "review_removal_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("CREATE DATABASE " + name);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    Flyway.configure().dataSource(source).target("57").load().migrate();
    var jdbc = new JdbcTemplate(source);
    jdbc.execute(
        """
        INSERT INTO business_partners (id,name,partner_type,created_at,updated_at)
        VALUES (900,'완료 기록 거래처','WHOLESALE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slips (id,slip_number,sale_date,sales_type,partner_id,total_amount,paid_amount,remaining_amount,payment_status,sales_status,created_at,updated_at)
        VALUES (901,'PAST-RECORD',DATE '2020-01-01','DIRECT',900,1000,1000,0,'입금 완료','출고 완료',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slip_items (id,sales_slip_id,item_name,quantity,unit_price,amount)
        VALUES (902,901,'원본 품종',10,100,1000);
        INSERT INTO direct_sales (sales_slip_id,partner_id,sale_date,total_amount,created_at,updated_at)
        VALUES (901,900,DATE '2020-01-01',1000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO direct_sale_prices (sales_slip_item_id,sales_slip_id,priced_quantity,unit_price,amount)
        VALUES (902,901,10,100,1000);
        INSERT INTO direct_sale_amount_reconciliations (sales_slip_id,stored_paid_amount,stored_remaining_amount,stored_payment_status,stored_item_amount_sum,confirmed_allocation_amount,total_mismatch,price_mismatch,paid_mismatch,remaining_mismatch,ledger_review_required,signed_amount_review_required,cutover_review_required,cutover_legacy_snapshot)
        VALUES (901,1000,0,'입금 완료',1000,0,false,false,true,true,false,false,true,'{"paidAmount":1000}');
        INSERT INTO partner_payment_events (id,partner_id,event_type,event_date,amount,unapplied_amount,target_type,target_id,parent_event_id,status,external_uid,created_at,updated_at)
        VALUES (903,900,'PAYMENT_RECEIVED',DATE '2020-01-02',400,0,'SALES_SLIP',901,NULL,'FULLY_APPLIED','actual-cash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
               (904,900,'MANUAL_MATCH_CONFIRMED',DATE '2020-01-02',400,0,'SALES_SLIP',901,903,'CONFIRMED',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_creation_receipts (request_key,request_fingerprint,sales_slip_id,response_snapshot,created_at)
        VALUES ('original-key',repeat('a',64),901,'{"id":901,"financialReviewRequired":true}',CURRENT_TIMESTAMP);
        """);
    List<String> tables =
        jdbc.queryForList(
            """
        SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'
          AND table_name NOT IN ('direct_sale_amount_reconciliations','flyway_schema_history') ORDER BY table_name
        """,
            String.class);
    var before = new LinkedHashMap<String, List<String>>();
    for (var table : tables)
      before.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    var migration = Flyway.configure().dataSource(source).target("58").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('public.direct_sale_amount_reconciliations')", String.class))
        .isNull();
    for (var table : tables)
      assertThat(
              jdbc.queryForList(
                  "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class))
          .as(table)
          .isEqualTo(before.get(table));
  }
}
