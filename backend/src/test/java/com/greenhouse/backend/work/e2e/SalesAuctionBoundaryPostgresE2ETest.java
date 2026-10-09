package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.audit.application.AuditEvent;
import com.greenhouse.backend.audit.application.AuditEvent.Identity;
import com.greenhouse.backend.audit.application.AuditEvent.Target;
import com.greenhouse.backend.audit.application.AuditRecorder;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.auction.application.AuctionTrackingService;
import com.greenhouse.backend.sales.auction.domain.AuctionShipment;
import com.greenhouse.backend.sales.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.sales.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.sales.document.application.SalesQueryService;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.domain.SalesSlipItem;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.DirectSaleFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class SalesAuctionBoundaryPostgresE2ETest extends WorkE2ETestBase {

  private static final LocalDate DATE = LocalDate.of(2045, 1, 2);

  @Autowired BusinessPartnerRepository partners;

  @Autowired AuctionShipmentRepository shipments;

  @Autowired SalesSlipRepository slips;

  @Autowired SalesQueryService sales;

  @Autowired AuctionTrackingService auctions;

  @Autowired TransactionTemplate transactions;

  @Autowired AuditRecorder auditRecorder;

  @Autowired JdbcTemplate jdbc;

  @Autowired EntityManagerFactory entityManagerFactory;

  private long slipId;

  private long lotId;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE TABLE business_partners, sales_slips CONTINUE IDENTITY CASCADE");
    transactions.executeWithoutResult(
        tx -> {
          var partner =
              partners.save(
                  new BusinessPartner(
                      "Alpha%_ Market",
                      PartnerType.AUCTION_HOUSE,
                      "Owner Needle",
                      "010-5555",
                      "AddressOnly",
                      "PrivateMemo"));
          var shipment = new AuctionShipment(DATE, partner.getId(), PartnerType.AUCTION_HOUSE);
          var lot = new AuctionShipmentLot("Orchid", "Snow White", "A", null, 10);
          shipment.addLot(lot);
          shipments.save(shipment);
          lotId = lot.getId();
          var slip =
              new SalesSlip(
                  "BOUNDARY-001",
                  DATE,
                  SalesType.DIRECT,
                  null,
                  partner.getId(),
                  "미입금",
                  "작성중",
                  "계좌",
                  "Slip Memo");
          slip.addItem(new SalesSlipItem(null, "Snow White", "Orchid", "A", 5, 1000, "item memo"));
          DirectSaleFixtures.refreshProjection(slip);
          slipId = slips.save(slip).getId();
        });
    DirectSaleFixtures.copyTerms(jdbc, slipId);
    jdbc.update("UPDATE business_partners SET is_active = FALSE");
  }

  @Test
  void salesSearchKeepsContactFieldsAndLiteralWildcards() {
    for (String term : List.of("owner needle", "010-5555", "alpha%_", "boundary", "Slip Memo")) {
      assertThat(sales.getSalesSlipPage(null, null, null, null, null, term, 0, 10).totalElements())
          .as(term)
          .isEqualTo(1);
    }
    for (String term : List.of("AddressOnly", "PrivateMemo", "absent")) {
      assertThat(sales.getSalesSlipPage(null, null, null, null, null, term, 0, 10).totalElements())
          .as(term)
          .isZero();
    }
  }

  @Test
  void auctionSearchKeepsConcatenatedPhraseAndExactMarket() {
    for (String term :
        List.of("Orchid Snow", "White Alpha%_", "Snow White Alpha%_ Market", "Alpha%_")) {
      assertThat(
              auctions
                  .getLots(null, null, null, null, null, null, false, false, false, term, 0, 10)
                  .content())
          .as(term)
          .singleElement()
          .satisfies(lot -> assertThat(lot.id()).isEqualTo(lotId));
    }
    assertThat(
            auctions
                .getLots(
                    null,
                    null,
                    "alpha%_ market",
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    "White Alpha%_",
                    0,
                    10)
                .totalElements())
        .isEqualTo(1);
    assertThat(
            auctions
                .getLots(null, null, "Market", null, null, null, false, false, false, null, 0, 10)
                .totalElements())
        .isZero();
  }

  @Test
  void partnerMatchesDoNotTruncateGlobalPagesAtTheBatchBoundary() {
    transactions.executeWithoutResult(
        tx -> {
          for (int i = 0; i < 501; i++) {
            var partner =
                partners.save(
                    new BusinessPartner(
                        "Partner " + i, PartnerType.RETAIL, "Batch Contact", null, null, null));
            slips.save(
                new SalesSlip(
                    "BATCH-" + i,
                    DATE,
                    SalesType.DIRECT,
                    null,
                    partner.getId(),
                    "미입금",
                    "작성중",
                    null,
                    null));
          }
        });
    DirectSaleFixtures.copyAllTerms(jdbc);
    var lastPage = sales.getSalesSlipPage(null, DATE, DATE, null, null, "Batch Contact", 5, 100);
    assertThat(lastPage.totalElements()).isEqualTo(501);
    assertThat(lastPage.content()).hasSize(1);
  }

  @Test
  void salesTypeAndGroupedCompletionFilterBeforePaginationAndKeepLegacyStatusQueries()
      throws Exception {
    var date = LocalDate.of(2046, 6, 7);
    transactions.executeWithoutResult(
        tx -> {
          for (var type : SalesType.values()) {
            var partner =
                partners.save(
                    new BusinessPartner(
                        "필터 " + type,
                        type == SalesType.AUCTION ? PartnerType.AUCTION_HOUSE : PartnerType.RETAIL,
                        null,
                        null,
                        null,
                        null));
            for (int index = 0; index < 3; index++) {
              var status =
                  index == 1
                      ? (type == SalesType.AUCTION
                          ? SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED
                          : SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED)
                      : SalesSlip.STATUS_DRAFT;
              var slip =
                  new SalesSlip(
                      "FILTER-" + type + "-" + index,
                      date,
                      type,
                      null,
                      partner.getId(),
                      "미입금",
                      status,
                      null,
                      null);
              if (index == 2) slip.updateSalesStatus(SalesSlip.STATUS_CANCELED);
              slips.save(slip);
            }
          }
        });
    DirectSaleFixtures.copyAllTerms(jdbc);
    var base = "/api/sales-slips/page?from=" + date + "&to=" + date;
    var completed = URLEncoder.encode("완료", StandardCharsets.UTF_8);
    var all = get(base + "&salesStatus=" + completed + "&size=1");
    assertThat(all.status()).isEqualTo(200);
    assertThat(all.data().path("totalElements").asInt()).isEqualTo(2);
    assertThat(all.data().path("content")).hasSize(1);
    for (var type : SalesType.values()) {
      var response = get(base + "&salesType=" + type + "&salesStatus=" + completed);
      assertThat(response.status()).isEqualTo(200);
      assertThat(response.data().path("totalElements").asInt()).isEqualTo(1);
      assertThat(response.data().path("content").get(0).path("salesType").asText())
          .isEqualTo(type.name());
      assertThat(get(base + "&salesType=" + type).data().path("totalElements").asInt())
          .isEqualTo(3);
    }
    for (var status : List.of("작성중", "취소", "출고 완료", "출하 완료")) {
      var response =
          get(base + "&salesStatus=" + URLEncoder.encode(status, StandardCharsets.UTF_8));
      assertThat(response.status()).isEqualTo(200);
      assertThat(response.data().path("totalElements").asInt())
          .isEqualTo(status.endsWith("완료") ? 1 : 2);
    }
    assertThat(get(base + "&salesType=INVALID").status()).isEqualTo(400);
    for (boolean auction : List.of(true, false)) {
      var options = get("/api/business-partners/options?auctionHouse=" + auction);
      assertThat(options.status()).isEqualTo(200);
      assertThat(options.data().path("content")).isNotEmpty();
      var expectedIds =
          partners.findAll().stream()
              .filter(partner -> (partner.getPartnerType() == PartnerType.AUCTION_HOUSE) == auction)
              .map(BusinessPartner::getId)
              .toList();
      var actualIds = new ArrayList<Long>();
      options.data().path("content").forEach(option -> actualIds.add(option.path("id").asLong()));
      assertThat(actualIds).containsExactlyInAnyOrderElementsOf(expectedIds);
    }
  }

  @Test
  void printKeepsItsExistingDocumentAndListJson() throws Exception {
    var detail = get("/api/sales-slips/" + slipId + "/print");
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data()).isEqualTo(get("/api/sales-slips/" + slipId).data());
    var list = get("/api/sales-slips/print").data();
    var actual = objectMapper.createObjectNode().set("detail", normalizeIds(detail.data()));
    ((ObjectNode) actual).set("list", normalizeIds(list));
    try (var input = getClass().getResourceAsStream("/sales/print-contract.json")) {
      assertThat(actual).isEqualTo(objectMapper.readTree(input));
    }
  }

  @Test
  void dashboardReadsFiveAggregatesWithoutLoadingEntities() throws Exception {
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var response = get("/api/dashboard/summary");
    assertThat(response.status()).isEqualTo(200);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
    assertThat(stats.getEntityLoadCount()).isZero();
    assertThat(response.data().path("repotDueCount").asLong()).isZero();
    assertThat(response.data().path("latestWorkDate").isNull()).isTrue();
    assertThat(response.data().path("houseCount").asLong())
        .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM houses", Long.class));
  }

  @Test
  void cliAuditRequiresCallerTransactionAndRollsBackWithIt() {
    var event =
        new AuditEvent(
            new Identity("cli-user", null, null, "job-1"),
            AuditAction.UPDATED,
            AuditSource.VARIETY_MANAGEMENT,
            new Target("CLI_TEST", 1L),
            List.of("quantity"),
            Map.of("quantity", 1),
            Map.of("quantity", 2),
            null);
    assertThatThrownBy(() -> auditRecorder.record(event))
        .isInstanceOf(IllegalTransactionStateException.class);
    Long id = transactions.execute(tx -> auditRecorder.record(event));
    assertThat(
            jdbc.queryForObject("SELECT actor_id FROM audit_events WHERE id = ?", String.class, id))
        .isEqualTo("cli-user");
    assertThat(
            jdbc.queryForObject(
                "SELECT after_data ->> 'quantity' FROM audit_events WHERE id = ?",
                String.class,
                id))
        .isEqualTo("2");
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    tx -> {
                      auditRecorder.record(event);
                      throw new IllegalStateException("후속 실패");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE entity_type = 'CLI_TEST'", Long.class))
        .isEqualTo(1);
  }

  private JsonNode normalizeIds(JsonNode value) {
    if (value instanceof ObjectNode object) {
      object
          .properties()
          .forEach(
              entry -> {
                if ((entry.getKey().equals("id") || entry.getKey().endsWith("Id"))
                    && entry.getValue().isNumber()) {
                  object.put(entry.getKey(), 99);
                } else normalizeIds(entry.getValue());
              });
    } else if (value.isArray()) value.forEach(this::normalizeIds);
    return value;
  }
}
