package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.sales.application.direct.SalesPaymentService;
import com.greenhouse.backend.sales.application.document.SalesQueryService;
import com.greenhouse.backend.sales.application.document.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.document.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.document.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.document.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesType;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class SalesAmountPostgresE2ETest extends WorkE2ETestBase {

  private static final LocalDate DATE = LocalDate.of(2044, 1, 1);

  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private SalesSlipCreationService creation;
  @Autowired private SalesSlipUpdateService updates;
  @Autowired private SalesQueryService queries;
  @Autowired private SalesPaymentService payments;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private JdbcTemplate jdbc;

  private Long groupId;
  private Long partnerId;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    jdbc.execute("TRUNCATE sales_slips CONTINUE IDENTITY CASCADE");
    groupId = seeder.seedContractScenario().orchidGroupId();
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "금액 경계 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
  }

  @ParameterizedTest
  @CsvSource({"2,1500000000,0", "3,1500000000,0", "1,1500000000,1500000000"})
  void rejectsOverflowThroughHttpWithoutPersistingPartialSales(
      int quantity, int price, int secondPrice) throws Exception {
    var before = snapshot();
    var result =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(quantity, price, secondPrice)));
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
    var retry = creation.create(request(quantity, 1000, 1000));
    assertThat(retry.totalAmount()).isEqualTo(quantity * 1000 + 1000);
    assertStock(quantity + 1);
  }

  @ParameterizedTest
  @CsvSource({"2,1500000000,0", "3,1500000000,0", "1,1500000000,1500000000"})
  void rollsBackTheReservationReleaseAndAllDetailsWhenAnEditOverflows(
      int quantity, int price, int secondPrice) {
    var created = creation.create(request(2, 1000, 1000));
    updates.update(created.id(), request(2, 2000, 1000));
    var document = queries.getSalesSlip(created.id());
    var before = snapshot();
    assertThatThrownBy(() -> updates.update(created.id(), request(quantity, price, secondPrice)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("금액");
    assertThat(snapshot()).isEqualTo(before);
    assertThat(queries.getSalesSlip(created.id())).isEqualTo(document);
    assertStock(3);
    var retry = updates.update(created.id(), request(quantity, 2000, 1000));
    assertThat(retry.totalAmount()).isEqualTo(quantity * 2000 + 1000);
    assertStock(quantity + 1);
  }

  @Test
  void persistsTheMaximumTotalAndPaymentWithoutLosingPrecision() {
    var created = creation.create(request(2, 1073741823, 1));
    assertThat(created.totalAmount()).isEqualTo(Integer.MAX_VALUE);
    assertThat(created.remainingAmount()).isEqualTo((long) Integer.MAX_VALUE);
    assertThat(
            jdbc.queryForObject(
                "SELECT receivable_balance FROM partner_balance_summaries WHERE partner_id = ?",
                Long.class,
                partnerId))
        .isEqualTo((long) Integer.MAX_VALUE);
    var command =
        new ManualPaymentCommand(
            (long) Integer.MAX_VALUE, DATE, "maximum-payment", null, null, null, null);
    var paid = payments.confirmPayment(created.id(), command);
    var afterPayment = snapshot();
    var replayed = payments.confirmPayment(created.id(), command);
    assertThat(replayed).isEqualTo(paid);
    assertThat(snapshot()).isEqualTo(afterPayment);
    assertThat(paid.paidAmount()).isEqualTo((long) Integer.MAX_VALUE);
    assertThat(paid.remainingAmount()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM partner_payment_events "
                    + "WHERE target_type = 'SALES_SLIP' AND target_id = ? AND event_type = 'PAYMENT_RECEIVED'",
                Long.class,
                created.id()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT receivable_balance FROM partner_balance_summaries WHERE partner_id = ?",
                Long.class,
                partnerId))
        .isZero();
    assertStock(3);
  }

  @Test
  void preservesZeroPriceForAuctionShipments() {
    var auctionPartner =
        partners.saveAndFlush(
            new BusinessPartner("금액 경계 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
    var created =
        creation.create(
            new SalesSlipCommand(
                DATE,
                SalesType.AUCTION,
                auctionPartner.getId(),
                null,
                "미입금",
                SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED,
                null,
                null,
                List.of(item(2, 0))));
    assertThat(created.totalAmount()).isZero();
    assertThat(created.remainingAmount()).isZero();
    assertThat(created.auctionShipmentId()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, groupId))
        .isEqualTo(98);
    assertThat(
            jdbc.queryForObject(
                "SELECT reserved_quantity FROM orchid_groups WHERE id = ?", Integer.class, groupId))
        .isZero();
  }

  @ParameterizedTest
  @CsvSource({
    "2,1500000000,-1294967296",
    "3,1500000000,205032704",
    "-2,1500000000,1294967296",
    "-3,1500000000,-205032704",
    "1,1000,999",
    "1,-100,-100",
    "0,100,0"
  })
  void rejectsInvalidItemMoneyWhenSqlBypassesDomainGuards(int quantity, int price, int amount) {
    var created = creation.create(request(1, 1000, 1000));
    var before = snapshot();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE sales_slip_items SET quantity = ?, unit_price = ?, amount = ? WHERE sales_slip_id = ?",
                    quantity,
                    price,
                    amount,
                    created.id()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_sales_slip_items_amount");
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void preservesHistoricalSignedReturnAmountsWithoutRelaxingTheApplicationContract() {
    var created = creation.create(request(1, 1000, 1000));
    jdbc.update(
        "UPDATE sales_slip_items SET quantity = -1, amount = -1000 WHERE sales_slip_id = ?",
        created.id());
    jdbc.update("UPDATE sales_slips SET total_amount = -2000 WHERE id = ?", created.id());
    var before = snapshot();
    jdbc.execute("ALTER TABLE sales_slip_items VALIDATE CONSTRAINT ck_sales_slip_items_amount");
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT total_amount FROM sales_slips WHERE id = ?", Integer.class, created.id()))
        .isEqualTo(-2000);
    assertThatThrownBy(() -> creation.create(request(-1, 1000, 1000)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  private SalesSlipCommand request(int quantity, int price, int secondPrice) {
    return new SalesSlipCommand(
        DATE,
        SalesType.DIRECT,
        partnerId,
        null,
        "미입금",
        SalesSlip.STATUS_DRAFT,
        null,
        "금액 경계",
        List.of(item(quantity, price), item(1, secondPrice)));
  }

  private SalesSlipItemInput item(int quantity, int price) {
    return new SalesSlipItemInput(
        "E2E 난",
        "팔레놉시스",
        "규격",
        quantity,
        price,
        "금액 경계",
        List.of(new SalesSlipAllocationInput(groupId, quantity)));
  }

  private void assertStock(int reserved) {
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, groupId))
        .isEqualTo(100);
    assertThat(
            jdbc.queryForObject(
                "SELECT reserved_quantity FROM orchid_groups WHERE id = ?", Integer.class, groupId))
        .isEqualTo(reserved);
  }

  private Map<String, List<String>> snapshot() {
    var rows = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "sales_slips",
            "sales_slip_items",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "sales_inventory_movements",
            "partner_balance_summaries",
            "partner_settlement_settings",
            "partner_payment_events",
            "audit_events",
            "sales_slip_daily_sequences")) {
      // JSON compares SQL arrays and JSONB by value rather than JDBC object identity.
      rows.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    }
    return rows;
  }
}
