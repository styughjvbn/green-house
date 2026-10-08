package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.sales.application.direct.SalesPaymentService;
import com.greenhouse.backend.sales.application.document.SalesQueryService;
import com.greenhouse.backend.sales.application.document.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.application.document.SalesSlipDocumentAssembler;
import com.greenhouse.backend.sales.application.document.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.document.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.document.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.document.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesType;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.dto.document.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
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
class SalesWriteQueryPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private SalesSlipCreationService creation;
  @Autowired private SalesSlipUpdateService updates;
  @Autowired private SalesSlipStatusService statuses;
  @Autowired private SalesPaymentService payments;
  @Autowired private SalesQueryService queries;
  @MockitoBean private Clock clock;
  @MockitoSpyBean private SalesSlipDocumentAssembler assembler;
  private long firstGroup;
  private long secondGroup;
  private long partner;

  @BeforeEach
  void seed() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-04T09:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    seeder.resetKeepingSequences();
    jdbc.execute(
        "TRUNCATE sales_creation_receipts, sales_slips, auction_shipments, partner_payment_events, partner_balance_summaries CONTINUE IDENTITY CASCADE");
    firstGroup = seeder.seedContractScenario().orchidGroupId();
    secondGroup =
        jdbc.queryForObject(
            """
      insert into orchid_groups (created_at, updated_at, age_year, genus, placement_type,
        pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
        split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
      select created_at, updated_at, age_year, genus, placement_type,
        pot_size, pot_size_code, quantity, sort_order, status, variety_name,
        (select min(id) from bed_zones where id <> source.bed_zone_id),
        split_placement_allowed, variety_id, start_position, end_position, reserved_quantity
      from orchid_groups source where id = ? returning id
      """,
            Long.class,
            firstGroup);
    seeder.baselineGroups();
    partner =
        partners
            .saveAndFlush(
                new BusinessPartner("쓰기 조회 회귀", PartnerType.WHOLESALE, null, null, null, null))
            .getId();
  }

  @ParameterizedTest
  @CsvSource({
    "EDIT,1",
    "EDIT,8",
    "OUTBOUND,1",
    "OUTBOUND,8",
    "DRAFT_CANCEL,1",
    "DRAFT_CANCEL,8",
    "OUTBOUND_CANCEL,1",
    "OUTBOUND_CANCEL,8",
    "UNCHANGED,1",
    "UNCHANGED,8",
    "PAYMENT,1",
    "PAYMENT,8",
    "PAYMENT_REPLAY,1",
    "PAYMENT_REPLAY,8"
  })
  void writesKeepSnapshotsStockAndResponseWithBoundedCollectionQueries(String action, int size)
      throws Exception {
    var first = creation.create(request(size, false), "original");
    prepare(action, first.id());
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var response = execute(action, first.id(), size);
    report(action + "-" + size);
    assertThat(stats.getCollectionFetchCount()).isEqualTo(1);
    long queryLimit =
        switch (action) {
          // Partner discovery and the mandatory partner-before-document lock stay constant
          // for 1 and 8 items. Existing explicit duplicate partner locks were removed.
          case "EDIT" -> 33;
          case "OUTBOUND" -> 16;
          case "PAYMENT" -> 25;
          case "DRAFT_CANCEL" -> 18;
          case "OUTBOUND_CANCEL" -> 23;
          case "PAYMENT_REPLAY" -> 12;
          case "UNCHANGED" -> 11;
          default -> throw new IllegalArgumentException(action);
        };
    assertThat(stats.getQueryExecutionCount()).isLessThanOrEqualTo(queryLimit);
    if (action.equals("UNCHANGED")) assertThat(stats.getPrepareStatementCount()).isEqualTo(12);
    if (action.equals("PAYMENT_REPLAY")) assertThat(stats.getPrepareStatementCount()).isEqualTo(13);
    assertThat(response).isEqualTo(queries.getSalesSlip(first.id()));
    assertThat(response.items()).hasSize(size);
    assertThat(response.items())
        .allSatisfy(
            item -> {
              assertThat(item.id()).isPositive();
              assertThat(item.allocations())
                  .hasSize(2)
                  .allSatisfy(
                      allocation -> {
                        assertThat(allocation.id()).isPositive();
                        assertThat(allocation.creationSnapshot().quantity()).isEqualTo(100);
                        assertThat(allocation.creationSnapshot().reservedQuantity()).isZero();
                        if (action.equals("OUTBOUND") || action.equals("OUTBOUND_CANCEL")) {
                          assertThat(allocation.outboundSnapshot().quantity()).isEqualTo(100);
                          assertThat(allocation.outboundSnapshot().reservedQuantity())
                              .isEqualTo(size * (allocation.orchidGroupId() == firstGroup ? 2 : 3));
                        } else assertThat(allocation.outboundSnapshot()).isNull();
                      });
            });
    boolean canceled = action.endsWith("CANCEL");
    boolean outbound = action.equals("OUTBOUND");
    int firstAmount = size * (action.equals("EDIT") ? 3 : 2);
    int secondAmount = size * (action.equals("EDIT") ? 2 : 3);
    assertStock(
        firstGroup, outbound ? 100 - firstAmount : 100, canceled || outbound ? 0 : firstAmount);
    assertStock(
        secondGroup, outbound ? 100 - secondAmount : 100, canceled || outbound ? 0 : secondAmount);
    if (action.startsWith("PAYMENT")) {
      assertThat(response.paidAmount()).isEqualTo(100);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from partner_payment_events where target_id = ? and event_type = 'PAYMENT_RECEIVED'",
                  Integer.class,
                  first.id()))
          .isEqualTo(1);
    }
    var beforeReplay = snapshot();
    assertThat(creation.create(request(size, false), "original")).isEqualTo(first);
    assertThat(snapshot()).isEqualTo(beforeReplay);
  }

  @ParameterizedTest
  @ValueSource(strings = {"EDIT", "OUTBOUND", "DRAFT_CANCEL", "OUTBOUND_CANCEL", "PAYMENT"})
  void finalResponseFailureRollsBackAllWritesAndAllowsRetry(String action) {
    var first = creation.create(request(8, false), "original");
    prepare(action, first.id());
    var before = snapshot();
    doThrow(new IllegalStateException("final response failure"))
        .when(assembler)
        .assemble(any(SalesSlip.class));
    assertThatThrownBy(() -> execute(action, first.id(), 8))
        .isInstanceOf(IllegalStateException.class);
    assertThat(snapshot()).isEqualTo(before);
    reset(assembler);
    assertThat(execute(action, first.id(), 8)).isEqualTo(queries.getSalesSlip(first.id()));
  }

  private void prepare(String action, long id) {
    if (action.equals("OUTBOUND_CANCEL"))
      statuses.updateStatus(id, new SalesSlipStatusUpdateRequest("출고 완료", null));
    if (action.equals("PAYMENT_REPLAY")) payments.confirmPayment(id, payment());
  }

  private SalesSlipDocument execute(String action, long id, int size) {
    return switch (action) {
      case "EDIT" -> updates.update(id, request(size, true));
      case "OUTBOUND" -> statuses.updateStatus(id, new SalesSlipStatusUpdateRequest("출고 완료", null));
      case "DRAFT_CANCEL", "OUTBOUND_CANCEL" ->
          statuses.updateStatus(id, new SalesSlipStatusUpdateRequest("취소", null));
      case "UNCHANGED" -> statuses.updateStatus(id, new SalesSlipStatusUpdateRequest("작성중", null));
      case "PAYMENT", "PAYMENT_REPLAY" -> payments.confirmPayment(id, payment());
      default -> throw new IllegalArgumentException(action);
    };
  }

  private ManualPaymentCommand payment() {
    return new ManualPaymentCommand(100L, DATE, "payment", "현금", null, null, null);
  }

  private SalesSlipCommand request(int size, boolean edit) {
    return new SalesSlipCommand(
        DATE,
        SalesType.DIRECT,
        partner,
        null,
        null,
        "작성중",
        "현금",
        null,
        IntStream.range(0, size)
            .mapToObj(
                i ->
                    new SalesSlipItemInput(
                        "E2E 난",
                        null,
                        edit ? "변경" : "기존",
                        5,
                        100,
                        null,
                        List.of(
                            new SalesSlipAllocationInput(firstGroup, edit ? 3 : 2),
                            new SalesSlipAllocationInput(secondGroup, edit ? 2 : 3))))
            .toList());
  }

  private void assertStock(long id, int quantity, int reserved) {
    assertThat(
            jdbc.queryForObject(
                "select quantity from orchid_groups where id = ?", Integer.class, id))
        .isEqualTo(quantity);
    assertThat(
            jdbc.queryForObject(
                "select reserved_quantity from orchid_groups where id = ?", Integer.class, id))
        .isEqualTo(reserved);
  }

  private void report(String scenario) throws Exception {
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    var counts = new LinkedHashMap<String, Long>();
    for (String query : stats.getQueries())
      counts.put(query, stats.getQueryStatistics(query).getExecutionCount());
    var path = Path.of("build/work-query-count/sales-write-" + scenario + ".json");
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
                "collectionFetches",
                stats.getCollectionFetchCount(),
                "queries",
                counts));
  }

  private Map<String, List<String>> snapshot() {
    var result = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "sales_slips",
            "sales_slip_items",
            "direct_sales",
            "direct_sale_prices",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots",
            "sales_inventory_movements",
            "sales_creation_receipts",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
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
