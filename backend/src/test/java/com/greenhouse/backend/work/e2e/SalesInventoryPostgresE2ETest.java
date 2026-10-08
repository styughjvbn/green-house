package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.document.SalesQueryService;
import com.greenhouse.backend.sales.application.document.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.document.SalesSlipInventoryService;
import com.greenhouse.backend.sales.application.document.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.document.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.document.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.document.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.document.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.document.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipItem;
import com.greenhouse.backend.sales.domain.document.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.dto.document.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.repository.document.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import com.greenhouse.backend.support.DirectSaleFixtures;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import jakarta.persistence.EntityManager;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class SalesInventoryPostgresE2ETest extends WorkE2ETestBase {

  private static final LocalDate DATE = LocalDate.of(2043, 1, 1);

  @Autowired private WorkTestDataSeeder seeder;

  @Autowired private OrchidGroupRepository groups;

  @Autowired private BusinessPartnerRepository partners;

  @Autowired private SalesSlipRepository slips;

  @Autowired private SalesInventoryMovementRepository movements;

  @Autowired private SalesSlipCreationService creation;

  @Autowired private SalesSlipUpdateService updates;

  @Autowired private SalesSlipInventoryService inventory;

  @Autowired private SalesSlipStatusService statuses;

  @Autowired private SalesQueryService queries;

  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;

  @Autowired private OrchidGroupLedgerReconciliationService reconciliation;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbc;

  @MockitoSpyBean private OrchidGroupReader reader;

  private Long groupId;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    groupId = seeder.seedContractScenario().orchidGroupId();
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(SalesType.class)
  void persistsDirectTermsWithDocumentItemIdsOnlyForDirectSales(SalesType type) {
    activate();
    var created = creation.create(request(partner(type), type, DATE, 3, 2));
    long count =
        jdbc.queryForObject(
            "select count(*) from direct_sales where sales_slip_id = ?", Long.class, created.id());
    assertThat(count).isEqualTo(type == SalesType.DIRECT ? 1 : 0);
    if (type == SalesType.DIRECT) {
      assertThat(
              jdbc.queryForObject(
                  "select total_amount from direct_sales where sales_slip_id = ?",
                  Integer.class,
                  created.id()))
          .isEqualTo(created.totalAmount());
      assertThat(
              jdbc.queryForList(
                  "select sales_slip_item_id from direct_sale_prices where sales_slip_id = ? order by sales_slip_item_id",
                  Long.class,
                  created.id()))
          .containsExactlyElementsOf(
              created.items().stream().map(item -> item.id()).sorted().toList());
      for (var item : created.items()) {
        var price =
            jdbc.queryForMap(
                "select * from direct_sale_prices where sales_slip_item_id = ?", item.id());
        assertThat(price.get("priced_quantity")).isEqualTo(item.quantity());
        assertThat(price.get("unit_price")).isEqualTo(item.unitPrice());
        assertThat(price.get("amount")).isEqualTo(item.amount());
      }
    }
  }

  @Test
  void directTermsFailureRollsBackDocumentReceiptAndReservationAndAllowsRetry() {
    activate();
    var request = request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2);
    String key = "direct-terms-failure-" + UUID.randomUUID();
    long beforeSlips = slips.count();
    long beforeDirect = jdbc.queryForObject("select count(*) from direct_sales", Long.class);
    long beforeMovements = movements.count();
    long beforeMutations =
        jdbc.queryForObject("select count(*) from orchid_group_mutations", Long.class);
    jdbc.execute(
        "ALTER TABLE direct_sales ADD CONSTRAINT test_direct_failure CHECK (total_amount < 0) NOT VALID");
    try {
      assertThatThrownBy(() -> creation.create(request, key))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_direct_failure");
    } finally {
      jdbc.execute("ALTER TABLE direct_sales DROP CONSTRAINT test_direct_failure");
    }
    assertThat(slips.count()).isEqualTo(beforeSlips);
    assertThat(jdbc.queryForObject("select count(*) from direct_sales", Long.class))
        .isEqualTo(beforeDirect);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sales_creation_receipts where request_key = ?",
                Long.class,
                key))
        .isZero();
    assertThat(movements.count()).isEqualTo(beforeMovements);
    assertThat(jdbc.queryForObject("select count(*) from orchid_group_mutations", Long.class))
        .isEqualTo(beforeMutations);
    assertStock(100, 0);
    var retried = creation.create(request, key);
    assertThat(creation.create(request, key)).isEqualTo(retried);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from direct_sales where sales_slip_id = ?",
                Long.class,
                retried.id()))
        .isEqualTo(1);
    assertStock(100, 5);
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(SalesType.class)
  void preservesSnapshotsAndPerAllocationHistoryAcrossCompletionAndCancellation(SalesType type) {
    activate();
    var partner = partner(type);
    var created = creation.create(request(partner, type, DATE, 3, 2));
    assertStock(100, 5);
    assertMovement(created.id(), SalesInventoryMovementType.SALES_RESERVE, 3, 2);
    assertThat(created.items())
        .allSatisfy(
            item ->
                assertThat(item.allocations())
                    .singleElement()
                    .satisfies(
                        line -> {
                          assertThat(line.creationSnapshot().quantity()).isEqualTo(100);
                          assertThat(line.creationSnapshot().reservedQuantity()).isZero();
                          assertThat(line.availableQuantity()).isEqualTo(95);
                        }));
    var persistedCreation = queries.getSalesSlip(created.id());

    String completedStatus =
        type == SalesType.DIRECT
            ? SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED
            : SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED;
    var completed =
        statuses.updateStatus(
            created.id(), new SalesSlipStatusUpdateRequest(completedStatus, null));
    statuses.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(completedStatus, null));
    assertStock(95, 0);
    assertMovement(created.id(), SalesInventoryMovementType.SALES_OUTBOUND, -3, -2);
    assertThat(completed.items())
        .allSatisfy(
            item -> {
              var line = item.allocations().getFirst();
              assertThat(line.outboundSnapshot().quantity()).isEqualTo(100);
              assertThat(line.outboundSnapshot().reservedQuantity()).isEqualTo(5);
              assertThat(line.creationSnapshot().quantity()).isEqualTo(100);
              assertThat(line.creationSnapshot().reservedQuantity()).isZero();
            });
    if (type == SalesType.AUCTION) {
      assertThat(completed.auctionShipmentId()).isNotNull();
      assertThat(completed.items())
          .allSatisfy(item -> assertThat(item.auctionShipmentLotId()).isNotNull());
    }
    var persistedCompletion = queries.getSalesSlip(created.id());

    statuses.updateStatus(
        created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertStock(100, 0);
    assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND, 3, 2);
    var canceled = queries.getSalesSlip(created.id());
    for (int index = 0; index < canceled.items().size(); index++) {
      var line = canceled.items().get(index).allocations().getFirst();
      assertThat(line.availableQuantity()).isEqualTo(100);
      assertThat(line.creationSnapshot())
          .isEqualTo(
              persistedCreation.items().get(index).allocations().getFirst().creationSnapshot());
      assertThat(line.outboundSnapshot())
          .isEqualTo(
              persistedCompletion.items().get(index).allocations().getFirst().outboundSnapshot());
    }

    Long outboundId =
        movements
            .findBySalesSlipIdAndChangeType(created.id(), SalesInventoryMovementType.SALES_OUTBOUND)
            .getFirst()
            .getMutationId();
    Long restoreId =
        movements
            .findBySalesSlipIdAndChangeType(
                created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND)
            .getFirst()
            .getMutationId();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from orchid_group_mutation_relations "
                    + "where mutation_id = ? and related_mutation_id = ? and relation_type = 'COMPENSATES'",
                Long.class,
                restoreId,
                outboundId))
        .isEqualTo(1L);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void editsReleaseAndReserveTogetherAndFailedEditsRollBack() {
    activate();
    var partner = partner(SalesType.DIRECT);
    var created = creation.create(request(partner, SalesType.DIRECT, DATE, 3, 2));
    updates.update(created.id(), request(partner, SalesType.DIRECT, DATE, 4, 2));
    assertStock(100, 6);
    assertMovement(created.id(), SalesInventoryMovementType.SALES_RELEASE, -3, -2);
    long before = movements.count();
    var updated = queries.getSalesSlip(created.id());

    assertThatThrownBy(
            () -> updates.update(created.id(), request(partner, SalesType.DIRECT, DATE, 99, 2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("가용 수량");
    assertStock(100, 6);
    assertThat(movements.count()).isEqualTo(before);
    assertThat(queries.getSalesSlip(created.id())).isEqualTo(updated);
    statuses.updateStatus(
        created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertStock(100, 0);
    assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_RESERVE, -4, -2);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void callerFailureRollsBackParticipatingStockSnapshotsShipmentsAndMovements() {
    activate();
    var created =
        creation.create(request(partner(SalesType.AUCTION), SalesType.AUCTION, DATE, 3, 2));
    long before = movements.count();
    long shipmentsBefore =
        jdbc.queryForObject("select count(*) from auction_shipments", Long.class);
    var beforeSlip = queries.getSalesSlip(created.id());
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          statuses.updateStatus(
                              created.id(),
                              new SalesSlipStatusUpdateRequest(
                                  SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED, null));
                          entityManager.flush();
                          throw new IllegalStateException("later failure");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("later failure");
    assertStock(100, 5);
    assertThat(movements.count()).isEqualTo(before);
    assertThat(jdbc.queryForObject("select count(*) from auction_shipments", Long.class))
        .isEqualTo(shipmentsBefore);
    assertThat(queries.getSalesSlip(created.id())).isEqualTo(beforeSlip);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void standaloneShipmentAuditFailureRollsBackStockSnapshotsAndLedgerThenAllowsRetry() {
    activate();
    var created =
        creation.create(request(partner(SalesType.AUCTION), SalesType.AUCTION, DATE, 3, 2));
    var request =
        new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED, null);
    var before = shipmentState();
    // Status audit is written after inventory mutation and shipment creation.
    jdbc.execute(
        "ALTER TABLE audit_events ADD CONSTRAINT test_shipment_audit CHECK "
            + "(entity_type <> 'SALES_SLIP' OR entity_id <> "
            + created.id()
            + " OR action <> 'UPDATED')");
    try {
      PostgresWriteTestSupport.assertStandaloneCheckFailure(
          () -> statuses.updateStatus(created.id(), request), "test_shipment_audit");
    } finally {
      jdbc.execute("ALTER TABLE audit_events DROP CONSTRAINT test_shipment_audit");
    }
    assertThat(shipmentState()).isEqualTo(before);
    assertStock(100, 5);
    statuses.updateStatus(created.id(), request);
    assertStock(95, 0);
    assertThat(reconciliation.reconcile().ready()).isTrue();
    var committed = shipmentState();
    statuses.updateStatus(created.id(), request);
    assertThat(shipmentState()).isEqualTo(committed);
  }

  private Map<String, List<String>> shipmentState() {
    return PostgresWriteTestSupport.snapshot(
        jdbc,
        transactionManager,
        List.of(
            "sales_slips",
            "sales_slip_items",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots",
            "sales_inventory_movements",
            "sales_creation_receipts",
            "auction_shipments",
            "auction_shipment_lots",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "audit_events"));
  }

  @ParameterizedTest
  @CsvSource({
    "spec,false",
    "spec,true",
    "itemMemo,false",
    "itemMemo,true",
    "slipMemo,false",
    "slipMemo,true"
  })
  void metadataEditsKeepReservationsThroughCompletionAndCancellation(
      String field, boolean completeBeforeCancel) {
    activate();
    var request = request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2);
    var created = creation.create(request);

    for (int edit = 1; edit <= 2; edit++) {
      request = withMetadata(request, field, "수정 " + edit);
      var updated = updates.update(created.id(), request);
      assertStock(100, 5);
      assertThat(updated.totalAmount()).isEqualTo(created.totalAmount());
      assertThat(updated.items())
          .allSatisfy(
              item ->
                  assertThat(item.allocations().getFirst().creationSnapshot().reservedQuantity())
                      .isZero());
      assertThat(reconciliation.reconcile().ready()).isTrue();
    }

    assertThat(
            movements.findBySalesSlipIdAndChangeType(
                created.id(), SalesInventoryMovementType.SALES_RESERVE))
        .hasSize(6)
        .extracting(SalesInventoryMovement::getQuantityDelta)
        .containsExactlyInAnyOrder(3, 2, 3, 2, 3, 2);
    assertThat(
            jdbc.queryForObject(
                "select count(distinct mutation_id) from sales_inventory_movements where sales_slip_id = ? and change_type = 'SALES_RESERVE'",
                Long.class,
                created.id()))
        .isEqualTo(3L);
    assertThat(
            jdbc.queryForObject(
                "select count(distinct mutation_id) from sales_inventory_movements where sales_slip_id = ? and change_type = 'SALES_RELEASE'",
                Long.class,
                created.id()))
        .isEqualTo(2L);

    if (completeBeforeCancel) {
      statuses.updateStatus(
          created.id(),
          new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
      assertStock(95, 0);
      assertMovement(created.id(), SalesInventoryMovementType.SALES_OUTBOUND, -3, -2);
    }
    statuses.updateStatus(
        created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertStock(100, 0);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void sameAmountAllocationEditsCanSwitchGroupsRepeatedlyAndThenShip() {
    Long secondId = seedSecondGroup();
    activate();
    Long partnerId = partner(SalesType.DIRECT);
    var created = creation.create(singleGroupRequest(partnerId, groupId, 5));

    for (Long selectedId : List.of(secondId, groupId, secondId)) {
      var updated = updates.update(created.id(), singleGroupRequest(partnerId, selectedId, 5));
      assertStock(100, selectedId.equals(groupId) ? 5 : 0);
      assertThat(groups.findById(secondId).orElseThrow().getReservedQuantity())
          .isEqualTo(selectedId.equals(secondId) ? 5 : 0);
      assertThat(updated.totalAmount()).isEqualTo(created.totalAmount());
      assertThat(updated.items().getFirst().allocations().getFirst().orchidGroupId())
          .isEqualTo(selectedId);
      assertThat(reconciliation.reconcile().ready()).isTrue();
    }

    statuses.updateStatus(
        created.id(),
        new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
    assertStock(100, 0);
    assertThat(groups.findById(secondId).orElseThrow().getQuantity()).isEqualTo(95);
    assertThat(groups.findById(secondId).orElseThrow().getReservedQuantity()).isZero();
    statuses.updateStatus(
        created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertThat(groups.findById(secondId).orElseThrow().getQuantity()).isEqualTo(100);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void metadataEditsDoNotConsumeAnotherSlipsReservation() {
    activate();
    var other = creation.create(singleGroupRequest(partner(SalesType.DIRECT), groupId, 7));
    var request = singleGroupRequest(partner(SalesType.DIRECT), groupId, 5);
    var created = creation.create(request);
    var updated = updates.update(created.id(), withMetadata(request, "spec", "특품"));
    assertStock(100, 12);
    assertThat(
            updated
                .items()
                .getFirst()
                .allocations()
                .getFirst()
                .creationSnapshot()
                .reservedQuantity())
        .isEqualTo(7);

    statuses.updateStatus(
        created.id(),
        new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
    assertStock(95, 7);
    statuses.updateStatus(
        created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertStock(100, 7);
    statuses.updateStatus(
        other.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertStock(100, 0);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void replayingReservationDoesNotDuplicateMovements() {
    activate();
    var created = creation.create(request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2));
    long beforeMutations =
        jdbc.queryForObject("select count(*) from orchid_group_mutations", Long.class);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              var slip = slips.findForUpdateById(created.id()).orElseThrow();
              inventory.reserve(slip);
              inventory.reserve(slip);
              entityManager.flush();
            });
    assertStock(100, 5);
    assertMovement(created.id(), SalesInventoryMovementType.SALES_RESERVE, 3, 2);
    assertThat(jdbc.queryForObject("select count(*) from orchid_group_mutations", Long.class))
        .isEqualTo(beforeMutations);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void reservationFailureRollsBackTheEditAndAllowsRetry() {
    activate();
    var request = request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2);
    var created = creation.create(request);
    var before = queries.getSalesSlip(created.id());
    var beforeDirect =
        jdbc.queryForList("select * from direct_sales where sales_slip_id = ?", created.id());
    var beforePrices =
        jdbc.queryForList(
            "select * from direct_sale_prices where sales_slip_id = ? order by sales_slip_item_id",
            created.id());
    long beforeMovements = movements.count();
    long beforeMutations =
        jdbc.queryForObject("select count(*) from orchid_group_mutations", Long.class);
    long beforeAudits = jdbc.queryForObject("select count(*) from audit_events", Long.class);
    jdbc.execute(
        "ALTER TABLE sales_inventory_movements ADD CONSTRAINT test_edit_reservation_failure "
            + "CHECK (change_type <> 'SALES_RESERVE') NOT VALID");
    try {
      assertThatThrownBy(
              () -> updates.update(created.id(), withMetadata(request, "spec", "실패할 수정")))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_edit_reservation_failure");
    } finally {
      jdbc.execute(
          "ALTER TABLE sales_inventory_movements DROP CONSTRAINT test_edit_reservation_failure");
    }
    assertStock(100, 5);
    assertThat(queries.getSalesSlip(created.id())).isEqualTo(before);
    assertThat(
            jdbc.queryForList("select * from direct_sales where sales_slip_id = ?", created.id()))
        .isEqualTo(beforeDirect);
    assertThat(
            jdbc.queryForList(
                "select * from direct_sale_prices where sales_slip_id = ? order by sales_slip_item_id",
                created.id()))
        .isEqualTo(beforePrices);
    assertThat(movements.count()).isEqualTo(beforeMovements);
    assertThat(jdbc.queryForObject("select count(*) from orchid_group_mutations", Long.class))
        .isEqualTo(beforeMutations);
    assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class))
        .isEqualTo(beforeAudits);

    var retried = updates.update(created.id(), withMetadata(request, "spec", "재시도"));
    assertThat(retried.items()).allSatisfy(item -> assertThat(item.spec()).isEqualTo("재시도"));
    assertStock(100, 5);
    assertThat(movements.count()).isEqualTo(beforeMovements + 4);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void concurrentReservationsLockGroupsInIdOrderAndRejectOverbooking() throws Exception {
    Long secondId = seedSecondGroup();
    activate();
    var first = request(partner(SalesType.DIRECT), List.of(groupId, secondId), DATE);
    var second = request(partner(SalesType.DIRECT), List.of(secondId, groupId), DATE.plusDays(1));
    long slipsBefore = slips.count();
    var arrivals = new CountDownLatch(2);
    doAnswer(
            invocation -> {
              arrivals.countDown();
              assertThat(arrivals.await(10, TimeUnit.SECONDS)).isTrue();
              return invocation.callRealMethod();
            })
        .when((OrchidGroupReader) AopTestUtils.getUltimateTargetObject(reader))
        .lockStates(anyCollection());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one = executor.submit(() -> tryCreate(first));
      var two = executor.submit(() -> tryCreate(second));
      assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertStock(100, 70);
    assertThat(groups.findById(secondId).orElseThrow().getReservedQuantity()).isEqualTo(70);
    assertThat(slips.count()).isEqualTo(slipsBefore + 1);
    assertThat(movements.count()).isEqualTo(2L);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void restoresPreCutoverOutboundWithoutInventingACompensationLink() {
    activate();
    var created = creation.create(request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2));
    statuses.updateStatus(
        created.id(),
        new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
    jdbc.update("UPDATE sales_inventory_movements SET mutation_id = NULL, correlation_id = NULL");
    jdbc.execute(
        "TRUNCATE orchid_group_mutation_relations, orchid_group_mutation_entries, orchid_group_mutations, orchid_group_ledger_coverages CASCADE");
    jdbc.update("UPDATE orchid_groups SET state_revision = NULL");
    activate();
    statuses.updateStatus(
        created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertStock(100, 0);
    assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND, 3, 2);
    var mutationId =
        movements
            .findBySalesSlipIdAndChangeType(
                created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND)
            .getFirst()
            .getMutationId();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from orchid_group_mutation_relations where mutation_id = ?",
                Long.class,
                mutationId))
        .isZero();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void salesHttpContractsExposeCurrentValuesSeparatelyFromHistoricalSnapshots() throws Exception {
    activate();
    var result =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(
                request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2)));
    assertThat(result.status()).isEqualTo(201);
    var detail = get("/api/sales-slips/" + result.data().path("id").asLong());
    assertThat(detail.status()).isEqualTo(200);
    var line = detail.data().path("items").get(0).path("allocations").get(0);
    assertThat(line.path("orchidGroupId").asLong()).isEqualTo(groupId);
    assertThat(line.path("allocatedQuantity").asInt()).isEqualTo(3);
    assertThat(line.path("availableQuantity").asInt()).isEqualTo(95);
    assertThat(line.path("creationSnapshot").path("reservedQuantity").asInt()).isZero();
    assertThat(line.path("outboundSnapshot").isNull()).isTrue();
    var search =
        get(
            "/api/sales/orchid-groups/search?status="
                + URLEncoder.encode("정상", StandardCharsets.UTF_8));
    assertThat(search.status()).isEqualTo(200);
    assertThat(search.data()).hasSize(1);
    assertThat(search.data().get(0).path("id").asLong()).isEqualTo(groupId);
    assertThat(search.data().get(0).path("reservedQuantity").asInt()).isEqualTo(5);
    assertThat(search.data().get(0).path("availableQuantity").asInt()).isEqualTo(95);
    assertThat(search.data().get(0).path("houseNumber")).isEqualTo(line.path("houseNumber"));
  }

  @Test
  void scalarGroupIdsStillRequireExistingFarmRowsInPostgres() {
    var slip =
        new SalesSlip(
            "FK-SALES",
            DATE,
            SalesType.DIRECT,
            null,
            partner(SalesType.DIRECT),
            "미입금",
            SalesSlip.STATUS_DRAFT,
            null,
            null);
    var item = new SalesSlipItem(null, "E2E 난", null, null, 1, 100, null);
    item.addAllocation(new SalesSlipItemAllocation(-1L, 1));
    slip.addItem(item);
    DirectSaleFixtures.refreshProjection(slip);
    assertThatThrownBy(() -> slips.saveAndFlush(slip))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("foreign key");
    var saved =
        slips.saveAndFlush(
            new SalesSlip(
                "FK-MOVEMENT",
                DATE,
                SalesType.DIRECT,
                null,
                slip.getPartnerId(),
                "미입금",
                SalesSlip.STATUS_DRAFT,
                null,
                null));
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              var managed = slips.findById(saved.getId()).orElseThrow();
              managed.addItem(new SalesSlipItem(null, "E2E 난", null, null, 1, 100, null));
              DirectSaleFixtures.refreshProjection(managed);
              entityManager.flush();
            });
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          var managed = slips.findWithDetailsById(saved.getId()).orElseThrow();
                          movements.saveAndFlush(
                              new SalesInventoryMovement(
                                  -1L,
                                  managed,
                                  managed.getItems().getFirst(),
                                  SalesInventoryMovementType.SALES_RESERVE,
                                  1,
                                  null));
                        }))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("foreign key");
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(SalesType.class)
  void cancellationCannotRestoreOutboundStockIntoAnOccupiedPlacement(SalesType type)
      throws Exception {
    activate();
    var slip = creation.create(request(partner(type), type, DATE, 50, 50));
    statuses.updateStatus(
        slip.id(),
        new SalesSlipStatusUpdateRequest(type == SalesType.DIRECT ? "출고 완료" : "출하 완료", null));
    assertStock(0, 0);
    var group = groups.findById(groupId).orElseThrow();
    var occupied =
        post(
            "/api/orchid-groups",
            """
				{"bedZoneId":%d,"varietyId":%d,"quantity":10,"potSize":"4치","ageYear":3,
				 "status":"정상","startPosition":0,"endPosition":5}
				"""
                .formatted(group.getBedZone().getId(), group.getVariety().getId()));
    assertThat(occupied.status()).as(occupied.body().toString()).isEqualTo(201);
    var before = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    long mutations = jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class);
    assertThatThrownBy(
            () -> statuses.updateStatus(slip.id(), new SalesSlipStatusUpdateRequest("취소", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("겹칩니다");
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id")).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class))
        .isEqualTo(mutations);
    assertThat(slips.findById(slip.id()).orElseThrow().isOutboundCompleted()).isTrue();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private void activate() {
    // Import a pre-cutover fixture, then exercise the real PostgreSQL write fence.
    assertThat(reconciliation.reconcile().issues()).isEmpty();
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
  }

  @Test
  void changingItemCountStillRollsBackTheReleasedReservation() {
    activate();
    var request = request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2);
    var created = creation.create(request);
    var before = queries.getSalesSlip(created.id());
    long beforeMovements = movements.count();
    long beforeMutations =
        jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class);
    long beforeAudits = jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class);
    var invalid =
        new SalesSlipCommand(
            DATE.plusDays(1),
            SalesType.DIRECT,
            request.partnerId(),
            null,
            null,
            null,
            null,
            null,
            List.of(request.items().getFirst()));
    assertThatThrownBy(() -> updates.update(created.id(), invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("품목 개수 변경 수정은 아직 지원하지 않습니다.");
    assertStock(100, 5);
    assertThat(queries.getSalesSlip(created.id())).isEqualTo(before);
    assertThat(movements.count()).isEqualTo(beforeMovements);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class))
        .isEqualTo(beforeMutations);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Long.class))
        .isEqualTo(beforeAudits);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private Long seedSecondGroup() {
    return jdbc.queryForObject(
        """
        insert into orchid_groups (created_at, updated_at, age_year, genus, placement_type, pot_size, pot_size_code,
          quantity, sort_order, status, variety_name, bed_zone_id, split_placement_allowed,
          variety_id, start_position, end_position, reserved_quantity)
        select created_at, updated_at, age_year, genus, placement_type, pot_size, pot_size_code,
          100, 2, status, variety_name, bed_zone_id, split_placement_allowed, variety_id, 5, 10, 0
        from orchid_groups where id = ? returning id
        """,
        Long.class,
        groupId);
  }

  private SalesSlipCommand singleGroupRequest(Long partnerId, Long selectedId, int quantity) {
    return new SalesSlipCommand(
        DATE,
        SalesType.DIRECT,
        partnerId,
        null,
        "미입금",
        SalesSlip.STATUS_DRAFT,
        null,
        null,
        List.of(item(List.of(new SalesSlipAllocationInput(selectedId, quantity)))));
  }

  private SalesSlipCommand withMetadata(SalesSlipCommand request, String field, String value) {
    var items =
        request.items().stream()
            .map(
                item ->
                    new SalesSlipItemInput(
                        item.itemName(),
                        item.genus(),
                        field.equals("spec") ? value : item.spec(),
                        item.quantity(),
                        item.unitPrice(),
                        field.equals("itemMemo") ? value : item.memo(),
                        item.allocations()))
            .toList();
    return new SalesSlipCommand(
        request.saleDate(),
        request.salesType(),
        request.partnerId(),
        request.auctionShipmentId(),
        request.paymentStatus(),
        request.salesStatus(),
        request.paymentMethod(),
        field.equals("slipMemo") ? value : request.memo(),
        items);
  }

  private Long partner(SalesType type) {
    return partners
        .saveAndFlush(
            new BusinessPartner(
                "재고 경계 " + UUID.randomUUID(),
                type == SalesType.DIRECT ? PartnerType.WHOLESALE : PartnerType.AUCTION_HOUSE,
                null,
                null,
                null,
                null))
        .getId();
  }

  private SalesSlipCommand request(
      Long partner, SalesType type, LocalDate date, int first, int second) {
    return new SalesSlipCommand(
        date,
        type,
        partner,
        null,
        "미입금",
        SalesSlip.STATUS_DRAFT,
        null,
        null,
        List.of(
            item(
                List.of(
                    new SalesSlipAllocationInput(groupId, 1),
                    new SalesSlipAllocationInput(groupId, first - 1))),
            item(List.of(new SalesSlipAllocationInput(groupId, second)))));
  }

  private SalesSlipCommand request(Long partner, List<Long> ids, LocalDate date) {
    return new SalesSlipCommand(
        date,
        SalesType.DIRECT,
        partner,
        null,
        "미입금",
        SalesSlip.STATUS_DRAFT,
        null,
        null,
        List.of(item(ids.stream().map(id -> new SalesSlipAllocationInput(id, 70)).toList())));
  }

  private SalesSlipItemInput item(List<SalesSlipAllocationInput> allocations) {
    return new SalesSlipItemInput(
        "E2E 난",
        "팔레놉시스",
        null,
        allocations.stream().mapToInt(SalesSlipAllocationInput::quantity).sum(),
        100,
        null,
        allocations);
  }

  private boolean tryCreate(SalesSlipCommand request) {
    try {
      creation.create(request);
      return true;
    } catch (IllegalArgumentException exception) {
      assertThat(exception).hasMessageContaining("가용 수량");
      return false;
    }
  }

  private void assertStock(int quantity, int reserved) {
    var group = groups.findById(groupId).orElseThrow();
    assertThat(group.getQuantity()).isEqualTo(quantity);
    assertThat(group.getReservedQuantity()).isEqualTo(reserved);
  }

  private void assertMovement(Long slipId, SalesInventoryMovementType type, Integer... deltas) {
    var rows = movements.findBySalesSlipIdAndChangeType(slipId, type);
    assertThat(rows)
        .extracting(SalesInventoryMovement::getQuantityDelta)
        .containsExactlyInAnyOrder(deltas);
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(row.getOrchidGroupId()).isEqualTo(groupId);
              assertThat(row.getMutationId() != null).isTrue();
              assertThat(row.getCorrelationId() != null).isTrue();
            });
    assertThat(rows.stream().map(SalesInventoryMovement::getMutationId).distinct().count())
        .isEqualTo(1);
  }
}
