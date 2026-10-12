package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("work-e2e")
@Testcontainers
class HistoricalAuctionDocumentMigrationPostgresE2ETest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void connectsHistoricalDispatchesWithoutReplayingInventoryOrChangingExistingDocuments() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    jdbc.execute(
        """
        INSERT INTO sales_slips (id, created_at, updated_at, slip_number, sale_date, sales_type,
          auction_shipment_id, partner_id, total_amount, payment_status, sales_status)
        VALUES (99001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'EXISTING', DATE '2026-10-01',
          'AUCTION', 99002, 99000, 0, '정산 대기', '작성중')
        """);
    jdbc.execute(
        """
        INSERT INTO auction_shipment_lots (id,shipment_id,item_name,variety_name,shipped_quantity,
          sold_quantity,waiting_quantity,returned_quantity,current_status,created_at,updated_at)
        VALUES (99007,99002,'현재 출하','품종',5,0,5,0,'WAITING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slip_items (id,sales_slip_id,auction_shipment_lot_id,item_name,quantity,unit_price,amount)
        VALUES (99008,99001,99007,'현재 출하',5,0,0)
        """);
    var existingItems =
        jdbc.queryForList("SELECT * FROM sales_slip_items WHERE sales_slip_id=99001");
    var existing = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
    var lots = jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id");
    var shipments = jdbc.queryForList("SELECT * FROM auction_shipments ORDER BY id");
    var migration = Flyway.configure().dataSource(source).target("52").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(jdbc.queryForList("SELECT * FROM auction_shipments ORDER BY id"))
        .isEqualTo(shipments);
    assertThat(jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id"))
        .isEqualTo(lots);
    var expected = new LinkedHashMap<>(existing.getFirst());
    expected.put("historical_auction_import", false);
    assertThat(jdbc.queryForMap("SELECT * FROM sales_slips WHERE id=99001")).isEqualTo(expected);
    assertThat(jdbc.queryForList("SELECT * FROM sales_slip_items WHERE sales_slip_id=99001"))
        .isEqualTo(existingItems);
    var imported = jdbc.queryForMap("SELECT * FROM sales_slips WHERE historical_auction_import");
    assertThat(imported)
        .containsEntry("slip_number", "HIST-AUC-99003")
        .containsEntry("auction_shipment_id", 99003L)
        .containsEntry("sales_status", "출하 완료")
        .containsEntry("total_amount", 0)
        .containsEntry("paid_amount", 0L)
        .containsEntry("remaining_amount", 0L);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(i.quantity) FROM sales_slip_items i JOIN sales_slips s ON s.id=i.sales_slip_id WHERE s.historical_auction_import",
                Long.class))
        .isEqualTo(40);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_slip_items WHERE auction_shipment_lot_id IN (99004,99005)",
                Integer.class))
        .isEqualTo(2);
    for (var table :
        List.of(
            "sales_slip_item_allocations",
            "sales_inventory_movements",
            "sales_orchid_group_snapshots",
            "orchid_group_mutations",
            "partner_payment_events",
            "sales_creation_receipts")) {
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
          .as(table)
          .isZero();
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sales", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT nextval('sales_slips_id_seq')", Long.class))
        .isGreaterThan((Long) imported.get("id"));
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "UPDATE sales_slips SET sales_status='취소' WHERE historical_auction_import"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void repairsImportedVarietyAndGenusWithoutChangingOtherItemFactsOrSourceLots() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    jdbc.execute(
        """
        INSERT INTO auction_shipment_lots (id,shipment_id,item_name,variety_name,
          shipped_quantity,sold_quantity,waiting_quantity,returned_quantity,current_status,created_at,updated_at)
        VALUES (99006,99003,'品目','  ',5,0,5,0,'WAITING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
               (99007,99003,'품목','원본 품종',5,0,5,0,'WAITING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """);
    Flyway.configure().dataSource(source).target("52").load().migrate();
    jdbc.execute(
        """
        UPDATE sales_slip_items SET item_name='별도 보정 품종',genus='확인된 속' WHERE auction_shipment_lot_id=99007;
        INSERT INTO auction_shipment_lots (id,shipment_id,item_name,variety_name,
          shipped_quantity,sold_quantity,waiting_quantity,returned_quantity,current_status,created_at,updated_at)
        VALUES (99008,99002,'현재 품목','현재 품종',5,0,5,0,'WAITING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO sales_slips (id,created_at,updated_at,slip_number,sale_date,sales_type,
          auction_shipment_id,partner_id,total_amount,payment_status,sales_status)
        VALUES (99020,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'CURRENT',DATE '2026-10-01',
          'AUCTION',99002,99000,0,'정산 대기','작성중');
        INSERT INTO sales_slip_items (id,sales_slip_id,auction_shipment_lot_id,item_name,quantity,unit_price,amount)
        VALUES (99021,99020,99008,'현재 품목',5,0,0)
        """);
    var lots = jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id");
    var slips = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
    var before = jdbc.queryForList("SELECT * FROM sales_slip_items ORDER BY id");
    var migration = Flyway.configure().dataSource(source).target("53").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    var expected =
        before.stream()
            .map(
                row -> {
                  var value = new LinkedHashMap<>(row);
                  long lotId = (Long) row.get("auction_shipment_lot_id");
                  if (lotId == 99004L || lotId == 99005L) {
                    value.put("item_name", lotId == 99004L ? "품종" : "품종2");
                    value.put("genus", lotId == 99004L ? "난" : "난2");
                  }
                  return value;
                })
            .toList();
    assertThat(jdbc.queryForList("SELECT * FROM sales_slip_items ORDER BY id")).isEqualTo(expected);
    assertThat(jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id"))
        .isEqualTo(lots);
    assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id")).isEqualTo(slips);
  }

  @Test
  void invalidPhysicalQuantityRollsBackAllDocumentsAndTheMarkerColumn() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    jdbc.execute("UPDATE auction_shipment_lots SET shipped_quantity=31 WHERE id=99004");
    assertThatThrownBy(() -> Flyway.configure().dataSource(source).load().migrate())
        .hasStackTraceContaining("invalid quantity or conflicting lot ownership");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_slips", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name='sales_slips' AND column_name='historical_auction_import'",
                Integer.class))
        .isZero();
  }

  @Test
  void conflictingLotOwnershipIsRejectedRatherThanReassigned() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    jdbc.execute(
        """
        INSERT INTO sales_slips (id, created_at, updated_at, slip_number, sale_date, sales_type,
          partner_id, total_amount, payment_status, sales_status)
        VALUES (99001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'OTHER', DATE '2026-10-01',
          'AUCTION', 99000, 0, '정산 대기', '작성중');
        INSERT INTO sales_slip_items (id, sales_slip_id, auction_shipment_lot_id,
          item_name, quantity, unit_price, amount)
        VALUES (99006, 99001, 99004, '기존', 30, 0, 0)
        """);
    assertThatThrownBy(() -> Flyway.configure().dataSource(source).load().migrate())
        .hasStackTraceContaining("invalid quantity or conflicting lot ownership");
    assertThat(
            jdbc.queryForObject(
                "SELECT sales_slip_id FROM sales_slip_items WHERE id=99006", Long.class))
        .isEqualTo(99001);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_slips", Integer.class)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NUMBER", "PARTNER"})
  void rejectsAmbiguousNumberOrWrongPartnerInsteadOfInventingAnotherMapping(String scenario) {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    if (scenario.equals("PARTNER")) {
      jdbc.execute("UPDATE business_partners SET partner_type='WHOLESALE' WHERE id=99000");
    } else {
      jdbc.execute(
          """
          INSERT INTO sales_slips (id,created_at,updated_at,slip_number,sale_date,sales_type,partner_id,total_amount,payment_status,sales_status)
          VALUES (99001,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'HIST-AUC-99003',DATE '2026-10-01','AUCTION',99000,0,'정산 대기','작성중')
          """);
    }
    assertThatThrownBy(() -> Flyway.configure().dataSource(source).load().migrate())
        .hasStackTraceContaining(
            scenario.equals("PARTNER")
                ? "partner is not an auction house"
                : "document number conflict");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_slip_items", Integer.class))
        .isZero();
  }

  private DriverManagerDataSource database() {
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    String name = "historical_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("CREATE DATABASE " + name);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace(POSTGRES.getDatabaseName(), name),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    Flyway.configure().dataSource(source).target("51").load().migrate();
    return source;
  }

  @Test
  void removesOnlyTheImportFlagAndConstraintWithoutChangingHistoricalFacts() {
    var source = database();
    var jdbc = new JdbcTemplate(source);
    seed(jdbc);
    Flyway.configure().dataSource(source).target("56").load().migrate();
    var tables =
        List.of(
            "sales_slips",
            "sales_slip_items",
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "sales_creation_receipts",
            "sales_inventory_movements",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots",
            "orchid_group_mutations",
            "audit_events");
    var before = new LinkedHashMap<String, List<String>>();
    for (var table : tables)
      before.put(
          table,
          jdbc.queryForList(
              "SELECT (to_jsonb(row) - 'historical_auction_import')::text FROM "
                  + table
                  + " row ORDER BY 1",
              String.class));
    var migration = Flyway.configure().dataSource(source).target("57").load();
    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(migration.migrate().migrationsExecuted).isZero();
    migration.validate();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name='sales_slips' AND column_name='historical_auction_import'",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname='ck_sales_slips_historical_auction_import'",
                Integer.class))
        .isZero();
    for (var table : tables)
      assertThat(
              jdbc.queryForList(
                  "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class))
          .as(table)
          .isEqualTo(before.get(table));
    // The old import-specific CHECK no longer blocks ordinary cancellation state.
    assertThat(
            jdbc.update(
                "UPDATE sales_slips SET sales_status='취소' WHERE slip_number='HIST-AUC-99003'"))
        .isEqualTo(1);
  }

  private void seed(JdbcTemplate jdbc) {
    jdbc.execute(
        """
        INSERT INTO business_partners (id,name,partner_type,created_at,updated_at)
        VALUES (99000,'이관 경매장','AUCTION_HOUSE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO auction_shipments (id,auction_house_id,shipment_date,status,created_at,updated_at)
        VALUES (99002,99000,DATE '2026-10-01','WAITING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
               (99003,99000,DATE '2026-10-01','WAITING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        INSERT INTO auction_shipment_lots (id,shipment_id,item_name,variety_name,shipment_grade,
          shipped_quantity,sold_quantity,waiting_quantity,returned_quantity,current_status,created_at,updated_at)
        VALUES (99004,99003,'난','품종','A',30,20,0,10,'RETURNED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
               (99005,99003,'난2','품종2','B',10,0,10,0,'WAITING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """);
  }
}
