package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.settlement.application.AuctionSettlementResponseAssembler;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.settlement.application.ManualPaymentCommand;
import com.greenhouse.backend.settlement.application.PaymentService;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("work-e2e")
class SettlementReadQueryPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private AuctionShipmentRepository shipments;
  @Autowired private AuctionSettlementService settlements;
  @Autowired private PaymentService payments;
  @Autowired private AuctionDataReader reader;
  @MockitoBean private Clock clock;
  @MockitoSpyBean private AuctionSettlementResponseAssembler assembler;
  private long house;

  @BeforeEach
  void seed() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-04T09:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    jdbc.execute(
        "TRUNCATE auction_settlements, auction_shipments, partner_payment_events, partner_balance_summaries, audit_events CONTINUE IDENTITY CASCADE");
    house =
        partners
            .saveAndFlush(
                new BusinessPartner("정산 조회 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null))
            .getId();
  }

  @ParameterizedTest
  @CsvSource({
    "REBUILD,1",
    "REBUILD,8",
    "REBUILD,501",
    "EXISTING,1",
    "EXISTING,8",
    "EXISTING,501",
    "DETAIL,1",
    "DETAIL,8",
    "DETAIL,501",
    "PAYMENT,1",
    "PAYMENT,8",
    "PAYMENT,501",
    "REPLAY,1",
    "REPLAY,8",
    "REPLAY,501",
    "INITIALIZER,1",
    "INITIALIZER,8",
    "INITIALIZER,501"
  })
  void financialSnapshotsAndCurrentDisplayKeepTheirContractsWithoutAuctionEntityLoads(
      String action, int size) throws Exception {
    source(size);
    long id =
        action.equals("REBUILD") || action.equals("INITIALIZER")
            ? 0
            : settlements.rebuild(house, DATE).id();
    if (action.equals("REPLAY")) payments.confirmAuctionPayment(id, payment());
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    AuctionSettlementResponse result;
    if (action.equals("INITIALIZER")) {
      assertThat(settlements.rebuildExistingResults()).isEqualTo(1);
      result = null;
    } else result = execute(action, id);
    report(action + "-" + size, stats);
    assertThat(auctionEntityLoads(stats)).isZero();
    if (action.equals("DETAIL"))
      assertThat(stats.getPrepareStatementCount()).isEqualTo(2 + (size + 499) / 500);
    if (action.equals("REPLAY"))
      assertThat(stats.getPrepareStatementCount()).isEqualTo(6 + (size + 499) / 500);
    if (result == null) {
      id =
          jdbc.queryForObject(
              "select id from auction_settlements where auction_house_id = ?", Long.class, house);
      result = settlements.getSettlement(id);
    }
    assertThat(result.lines())
        .hasSize(size)
        .allSatisfy(
            line -> {
              assertThat(line.id()).isPositive();
              assertThat(line.quantity()).isEqualTo(1);
              assertThat(line.unitPrice()).isEqualTo(1000);
              assertThat(line.amount()).isEqualTo(1000);
              assertThat(line.shipmentDate()).isEqualTo(DATE.minusDays(1));
              assertThat(line.varietyName()).isEqualTo("스냅샷 품종");
              assertThat(line.shipmentGrade()).isEqualTo("A");
            });
    assertThat(result.grossAmount()).isEqualTo(1000L * size);
    assertThat(result.paidAmount())
        .isEqualTo(action.equals("PAYMENT") || action.equals("REPLAY") ? 100 : 0);
    var before = snapshot();
    if (action.equals("REPLAY"))
      assertThat(payments.confirmAuctionPayment(id, payment())).isEqualTo(result);
    stats.clear();
    assertThat(settlements.rebuildExistingResults()).isZero();
    assertThat(stats.getPrepareStatementCount()).isEqualTo(2 * ((size + 499) / 500));
    assertThat(auctionEntityLoads(stats)).isZero();
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"REBUILD", "INITIALIZER", "PAYMENT"})
  void laterChangesToSourceAndDisplayDoNotOverwriteFinancialSnapshotsOrPayments(String action) {
    source(8);
    var first = settlements.rebuild(house, DATE);
    payments.confirmAuctionPayment(first.id(), payment());
    jdbc.update("update auction_result_lines set quantity = 2, unit_price = 9000, amount = 18000");
    jdbc.update("update auction_shipment_lots set variety_name = '현재 품종', shipment_grade = 'B'");
    jdbc.update("update business_partners set name = '현재 경매장' where id = ?", house);
    AuctionSettlementResponse result;
    if (action.equals("INITIALIZER")) {
      assertThat(settlements.rebuildExistingResults()).isZero();
      result = settlements.getSettlement(first.id());
    } else result = execute(action, first.id());
    assertThat(result.auctionHouseName()).isEqualTo("현재 경매장");
    assertThat(result.grossAmount()).isEqualTo(8000);
    assertThat(result.paidAmount()).isEqualTo(100);
    assertThat(result.lines())
        .allSatisfy(
            line -> {
              assertThat(line.quantity()).isEqualTo(1);
              assertThat(line.unitPrice()).isEqualTo(1000);
              assertThat(line.amount()).isEqualTo(1000);
              assertThat(line.varietyName()).isEqualTo("현재 품종");
              assertThat(line.shipmentGrade()).isEqualTo("B");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"REBUILD", "PAYMENT"})
  void finalResponseFailureRollsBackAllWritesAndAllowsRetry(String action) {
    source(8);
    long id = action.equals("REBUILD") ? 0 : settlements.rebuild(house, DATE).id();
    var before = snapshot();
    doThrow(new IllegalStateException("final response failure"))
        .when(assembler)
        .assemble(any(AuctionSettlement.class));
    assertThatThrownBy(() -> execute(action, id)).isInstanceOf(IllegalStateException.class);
    assertThat(snapshot()).isEqualTo(before);
    reset(assembler);
    assertThat(execute(action, id).lines()).hasSize(8);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 500, 501})
  void resultIdsAreDeduplicatedAndBatchedWithoutLoadingEntities(int size) {
    source(size);
    var ids = jdbc.queryForList("select id from auction_result_lines order by id", Long.class);
    var requested = new ArrayList<>(ids);
    requested.addAll(ids);
    requested.add(-1L);
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var results = reader.getResults(requested);
    assertThat(results).containsOnlyKeys(ids.toArray(Long[]::new));
    assertThat(auctionEntityLoads(stats)).isZero();
    assertThat(stats.getPrepareStatementCount()).isEqualTo((size + 1 + 499) / 500);
    stats.clear();
    assertThat(reader.getResults(List.of())).isEmpty();
    assertThat(stats.getPrepareStatementCount()).isZero();
  }

  private AuctionSettlementResponse execute(String action, long id) {
    return switch (action) {
      case "REBUILD", "EXISTING" -> settlements.rebuild(house, DATE);
      case "DETAIL" -> settlements.getSettlement(id);
      case "PAYMENT", "REPLAY" -> payments.confirmAuctionPayment(id, payment());
      default -> throw new IllegalArgumentException(action);
    };
  }

  private ManualPaymentCommand payment() {
    return new ManualPaymentCommand(100L, DATE, "payment", "현금", null, null, null);
  }

  private void source(int size) {
    var shipment = new AuctionShipment(DATE.minusDays(1), house, PartnerType.AUCTION_HOUSE);
    for (int i = 0; i < size; i++) {
      var lot = new AuctionShipmentLot("난", "스냅샷 품종", "A", null, 1);
      var attempt = new AuctionAttempt(DATE, 1, AuctionAttemptStatus.SOLD, null, null);
      attempt.addResultLine(
          new AuctionResultLine(DATE, "A", 1, 1000, 1000, null, AuctionInspectionStatus.NORMAL));
      lot.addAttempt(attempt);
      shipment.addLot(lot);
    }
    shipments.saveAndFlush(shipment);
  }

  private long auctionEntityLoads(Statistics stats) {
    return List.of(
            AuctionResultLine.class,
            AuctionAttempt.class,
            AuctionShipmentLot.class,
            AuctionShipment.class)
        .stream()
        .mapToLong(type -> stats.getEntityStatistics(type.getName()).getLoadCount())
        .sum();
  }

  private void report(String scenario, Statistics stats) throws Exception {
    var counts = new LinkedHashMap<String, Long>();
    for (String query : stats.getQueries())
      counts.put(query, stats.getQueryStatistics(query).getExecutionCount());
    var path = Path.of("build/work-query-count/settlement-read-" + scenario + ".json");
    Files.createDirectories(path.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            path.toFile(),
            Map.of(
                "scenario",
                scenario,
                "preparedStatements",
                stats.getPrepareStatementCount(),
                "auctionEntityLoads",
                auctionEntityLoads(stats),
                "queries",
                counts));
  }

  private Map<String, List<String>> snapshot() {
    var result = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "auction_settlements",
            "auction_settlement_lines",
            "partner_payment_events",
            "partner_balance_summaries",
            "audit_events"))
      result.put(
          table,
          jdbc.queryForList(
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    return result;
  }
}
