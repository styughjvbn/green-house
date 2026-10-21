package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
@IsolatedPostgresContext
class SalesItemUnitMigrationPostgresE2ETest extends WorkE2ETestBase {
  @Test
  void convertsValidUnitsInPlaceAndPreservesOtherFactsAndCreationReceipts() {
    String database = "sales_unit_" + UUID.randomUUID().toString().replace("-", "");
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
      Flyway.configure().dataSource(source).target("43").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          """
          INSERT INTO business_partners(id,name,partner_type,is_active,created_at,updated_at)
          VALUES(900,'거래처','WHOLESALE',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
          INSERT INTO sales_slips(id,sale_date,sales_type,partner_id,total_amount,paid_amount,
              remaining_amount,payment_status,sales_status,created_at,updated_at)
          VALUES(901,DATE '2026-10-14','DIRECT',900,100,0,100,'미입금','작성중',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
          INSERT INTO sales_creation_receipts(request_key,request_fingerprint,sales_slip_id,response_snapshot,created_at)
          VALUES('old-unit',repeat('a',64),901,'{"id":901,"items":[{"unit":"분"}]}',CURRENT_TIMESTAMP);
          """);
      var values = Arrays.asList(null, "10", " 0010 ", "2147483647");
      for (int i = 0; i < values.size(); i++)
        jdbc.update(
            """
          INSERT INTO sales_slip_items(id,sales_slip_id,item_name,unit,boxes,quantity,unit_price,amount)
          VALUES(?,901,'품종',?,2,20,100,2000)
          """,
            1000 + i,
            values.get(i));
      var facts =
          jdbc.queryForList(
              "SELECT (to_jsonb(i)-'unit')::text FROM sales_slip_items i ORDER BY id",
              String.class);
      var receipts =
          jdbc.queryForList(
              "SELECT to_jsonb(r)::text FROM sales_creation_receipts r ORDER BY request_key",
              String.class);

      var migration = Flyway.configure().dataSource(source).target("44").load();
      assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(jdbc.queryForList("SELECT unit FROM sales_slip_items ORDER BY id", Integer.class))
          .containsExactly(null, 10, 10, Integer.MAX_VALUE);
      assertThat(
              jdbc.queryForObject(
                  """
          SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
          AND table_name='sales_slip_items' AND column_name='legacy_unit_text'
          """,
                  Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForList(
                  "SELECT (to_jsonb(i)-'unit')::text FROM sales_slip_items i ORDER BY id",
                  String.class))
          .isEqualTo(facts);
      assertThat(
              jdbc.queryForList(
                  "SELECT to_jsonb(r)::text FROM sales_creation_receipts r ORDER BY request_key",
                  String.class))
          .isEqualTo(receipts);
      for (int invalid : new int[] {0, -1})
        assertThatThrownBy(
                () -> jdbc.update("UPDATE sales_slip_items SET unit=? WHERE id=1000", invalid))
            .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(migration.migrate().migrationsExecuted).isZero();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"분", "1.5", "0", "-1", "2147483648"})
  void rejectsInvalidUnitsWithoutChangingTheirValuesOrColumnType(String unit) {
    String database = "invalid_unit_" + UUID.randomUUID().toString().replace("-", "");
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
      Flyway.configure().dataSource(source).target("43").load().migrate();
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          """
          INSERT INTO business_partners(id,name,partner_type,is_active,created_at,updated_at)
          VALUES(900,'거래처','WHOLESALE',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
          INSERT INTO sales_slips(id,sale_date,sales_type,partner_id,total_amount,paid_amount,
              remaining_amount,payment_status,sales_status,created_at,updated_at)
          VALUES(901,DATE '2026-10-14','DIRECT',900,100,0,100,'미입금','작성중',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
          """);
      jdbc.update(
          """
          INSERT INTO sales_slip_items(id,sales_slip_id,item_name,unit,quantity,unit_price,amount)
          VALUES(1000,901,'품종',?,20,100,2000)
          """,
          unit);
      var before =
          jdbc.queryForList(
              "SELECT to_jsonb(i)::text FROM sales_slip_items i ORDER BY id", String.class);
      var migration = Flyway.configure().dataSource(source).target("44").load();
      assertThatThrownBy(migration::migrate).isInstanceOf(FlywayException.class);
      assertThat(
              jdbc.queryForList(
                  "SELECT to_jsonb(i)::text FROM sales_slip_items i ORDER BY id", String.class))
          .isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  """
          SELECT data_type FROM information_schema.columns WHERE table_schema='public'
          AND table_name='sales_slip_items' AND column_name='unit'
          """,
                  String.class))
          .isEqualTo("character varying");
      assertThat(
              jdbc.queryForObject(
                  """
          SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
          AND table_name='sales_slip_items' AND column_name='legacy_unit_text'
          """,
                  Integer.class))
          .isZero();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }
}
