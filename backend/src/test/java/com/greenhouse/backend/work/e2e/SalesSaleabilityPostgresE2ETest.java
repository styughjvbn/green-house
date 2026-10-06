package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.DerivedOrchidGroupService;
import com.greenhouse.backend.farm.application.orchid.FarmWorkTargetResolver;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupQuantityMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.ReserveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.status.FarmMetricsReader;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesOrchidGroupQueryService;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.application.target.WorkTargetSelection;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import jakarta.persistence.EntityManager;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class SalesSaleabilityPostgresE2ETest extends WorkE2ETestBase {

  private static final LocalDate DATE = LocalDate.of(2045, 1, 1);

  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private OrchidGroupRepository groups;
  @Autowired private OrchidGroupReader reader;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private OrchidGroupMutationEngine engine;
  @Autowired private SalesSlipCreationService creation;
  @Autowired private SalesSlipUpdateService updates;
  @Autowired private SalesSlipStatusService statuses;
  @Autowired private SalesQueryService queries;
  @Autowired private SalesOrchidGroupQueryService search;
  @Autowired private FarmMetricsReader metrics;
  @Autowired private FarmWorkTargetResolver workTargets;
  @Autowired private DerivedOrchidGroupService derivedGroups;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  private Long firstId;
  private Long secondId;
  private Long partnerId;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    jdbc.execute("TRUNCATE sales_slips CONTINUE IDENTITY CASCADE");
    firstId = seeder.seedContractScenario().orchidGroupId();
    secondId =
        jdbc.queryForObject(
            """
        INSERT INTO orchid_groups (created_at, updated_at, age_year, genus, placement_type,
            pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
            split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
        SELECT created_at, updated_at, age_year, genus, placement_type,
            pot_size, pot_size_code, 100, 2, status, variety_name, bed_zone_id,
            split_placement_allowed, variety_id, 5, 10, 0 FROM orchid_groups WHERE id = ? RETURNING id
        """,
            Long.class,
            firstId);
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "판매 상태 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
  }

  @ParameterizedTest
  @ValueSource(strings = {"주의", "이상", "병해충", "종료", "폐기", "판매 완료", "생성 취소"})
  void excludesUnavailableStatusesEvenWithAnExplicitStatusFilter(String status) throws Exception {
    legacyStatus(secondId, status);
    activate();
    var before = snapshot();
    var all = get("/api/sales/orchid-groups/search");
    assertThat(all.status()).isEqualTo(200);
    assertThat(all.data().size()).isEqualTo(1);
    assertThat(all.data().get(0).path("id").asLong()).isEqualTo(firstId);
    var filtered =
        get(
            "/api/sales/orchid-groups/search?status="
                + URLEncoder.encode(status, StandardCharsets.UTF_8));
    assertThat(filtered.status()).isEqualTo(200);
    assertThat(filtered.data().size()).isZero();
    var varietyId = reader.getStates(List.of(firstId)).get(firstId).varietyId();
    assertThat(search.search("E2E 난", varietyId, status)).isEmpty();
    assertThat(metrics.getInventorySummary().saleableQuantity()).isEqualTo(100);
    assertWorkMembership(status);
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"주의", "이상", "병해충", "종료", "폐기", "판매 완료", "생성 취소"})
  void rejectsForgedCreationWithoutKeepingPartialReservationOrSales(String status)
      throws Exception {
    legacyStatus(secondId, status);
    activate();
    var before = snapshot();
    var response = post("/api/sales-slips", objectMapper.writeValueAsString(request(3, 2)));
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
    assertStock(firstId, 100, 0);
    assertStock(secondId, 100, 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rollsBackFailedEditsAndAllowsReallocationToHealthyStock(boolean currentGroupBlocked) {
    activate();
    var created = creation.create(request(5, 0));
    updates.update(created.id(), request(5, 0));
    changeStatus(currentGroupBlocked ? firstId : secondId, "병해충");
    var document = queries.getSalesSlip(created.id());
    var before = snapshot();
    assertThatThrownBy(
            () ->
                updates.update(
                    created.id(),
                    request(currentGroupBlocked ? 5 : 3, currentGroupBlocked ? 0 : 2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("판매 불가");
    assertThat(snapshot()).isEqualTo(before);
    assertThat(queries.getSalesSlip(created.id())).isEqualTo(document);
    assertStock(firstId, 100, 5);
    assertStock(secondId, 100, 0);
    if (currentGroupBlocked) {
      updates.update(created.id(), request(0, 5));
      assertStock(firstId, 100, 0);
      assertStock(secondId, 100, 5);
    } else {
      changeStatus(secondId, "정상");
      updates.update(created.id(), request(3, 2));
      assertStock(firstId, 100, 3);
      assertStock(secondId, 100, 2);
    }
  }

  @ParameterizedTest
  @CsvSource({"주의,false", "이상,false", "병해충,false", "주의,true", "이상,true", "병해충,true"})
  void preservesExistingReservationReleaseOutboundAndSnapshots(String status, boolean complete) {
    activate();
    var created = creation.create(request(5, 0));
    changeStatus(firstId, status);
    var before = snapshot();
    assertThatThrownBy(() -> creation.create(request(1, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("판매 불가");
    assertThat(snapshot()).isEqualTo(before);
    if (complete) {
      var completed =
          statuses.updateStatus(
              created.id(),
              new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
      assertStock(firstId, 95, 0);
      var allocation = completed.items().getFirst().allocations().getFirst();
      assertThat(allocation.creationSnapshot().status()).isEqualTo("정상");
      assertThat(allocation.outboundSnapshot().status()).isEqualTo(status);
    }
    statuses.updateStatus(
        created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    assertStock(firstId, 100, 0);
    assertThat(reader.getStates(List.of(firstId)).get(firstId).status()).isEqualTo(status);
  }

  @Test
  void replaysAnAlreadyAppliedReservationAfterAStatusChangeWithoutNewSideEffects() {
    activate();
    var command =
        new ReserveOrchidGroupsMutationCommand(
            source(), List.of(new OrchidGroupQuantityMutationItem(firstId, 5)), DATE, "기존 예약");
    var applied = transaction().execute(ignored -> engine.reserve(command));
    changeStatus(firstId, "병해충");
    var before = snapshot();
    var replayed = transaction().execute(ignored -> engine.reserve(command));
    assertThat(replayed.mutationId()).isEqualTo(applied.mutationId());
    assertThat(replayed.replayed()).isTrue();
    assertThat(snapshot()).isEqualTo(before);
    assertThatThrownBy(
            () ->
                transaction()
                    .execute(
                        ignored ->
                            engine.reserve(
                                new ReserveOrchidGroupsMutationCommand(
                                    source(), command.items(), DATE, "새 예약"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("판매 불가");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void serializesStatusChangesAndReservationsUnderTheSameGroupLock(boolean statusFirst)
      throws Exception {
    activate();
    var winningSnapshot = new AtomicReference<Map<String, List<String>>>();
    Runnable setStatus = () -> changeStatus(firstId, "병해충");
    Runnable reserve = () -> creation.create(request(5, 0));
    Throwable failure =
        compete(
            () -> {
              (statusFirst ? setStatus : reserve).run();
              entityManager.flush();
              winningSnapshot.set(snapshot());
            },
            statusFirst ? reserve : setStatus);
    if (statusFirst) {
      assertThat(failure)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("판매 불가");
      assertThat(snapshot()).isEqualTo(winningSnapshot.get());
      assertStock(firstId, 100, 0);
    } else {
      assertThat(failure).isNull();
      assertStock(firstId, 100, 5);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_slips", Long.class)).isEqualTo(1L);
    }
    assertThat(reader.getStates(List.of(firstId)).get(firstId).status()).isEqualTo("병해충");
  }

  @Test
  void keepsCustomStatusesCompatibleAndExcludesExhaustedAvailability() {
    legacyStatus(firstId, "기존 사용자 상태");
    legacyStatus(secondId, "분갈이 완료");
    activate();
    assertThat(search.search(null, null, null)).hasSize(2);
    assertThat(metrics.getInventorySummary().saleableQuantity()).isEqualTo(200);
    creation.create(request(100, 0));
    assertThat(search.search(null, null, null))
        .extracting(row -> row.id())
        .containsExactly(secondId);
    assertThat(metrics.getInventorySummary().saleableQuantity()).isEqualTo(100);
    creation.create(request(0, 100));
    assertThat(search.search(null, null, null)).isEmpty();
    assertThat(metrics.getInventorySummary().saleableQuantity()).isZero();
    assertThat(
            workTargets.resolve(
                WorkTargetSelection.identifiedScope(WorkSourceScopeType.FARM, null)))
        .extracting(row -> row.orchidGroupId())
        .containsExactlyInAnyOrder(firstId, secondId);
  }

  private void assertWorkMembership(String status) {
    var expected =
        List.of("주의", "이상", "병해충").contains(status) ? List.of(firstId, secondId) : List.of(firstId);
    var state = reader.getStates(List.of(firstId)).get(firstId);
    for (var scope :
        List.of(
            WorkTargetSelection.identifiedScope(WorkSourceScopeType.FARM, null),
            WorkTargetSelection.identifiedScope(WorkSourceScopeType.HOUSE, state.houseId()),
            WorkTargetSelection.identifiedScope(
                WorkSourceScopeType.PHYSICAL_BED, state.physicalBedId()),
            WorkTargetSelection.identifiedScope(WorkSourceScopeType.BED_ZONE, state.bedZoneId()))) {
      assertThat(workTargets.resolve(scope))
          .extracting(row -> row.orchidGroupId())
          .containsExactlyInAnyOrderElementsOf(expected);
    }
    assertThat(workTargets.resolve(WorkTargetSelection.manualSelection(expected)))
        .extracting(row -> row.orchidGroupId())
        .containsExactlyInAnyOrderElementsOf(expected);
    transaction().executeWithoutResult(ignored -> workTargets.lockAndValidateActive(expected));
    if (!expected.contains(secondId)) {
      assertThatThrownBy(() -> workTargets.resolve(WorkTargetSelection.orchidGroup(secondId)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    var derived = derivedGroups.getGroups(null, null, null, null, null, null);
    assertThat(derived)
        .singleElement()
        .satisfies(
            group -> {
              assertThat(group.orchidGroupCount()).isEqualTo(expected.size());
              assertThat(derivedGroups.getMembers(group.groupKey(), null, null, null))
                  .extracting(row -> row.id())
                  .containsExactlyInAnyOrderElementsOf(expected);
            });
  }

  private void legacyStatus(Long id, String status) {
    // Pre-cutover fixtures include inactive legacy rows with remaining physical quantity.
    jdbc.update("UPDATE orchid_groups SET status = ? WHERE id = ?", status, id);
  }

  private void activate() {
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
  }

  private void changeStatus(Long id, String status) {
    transaction()
        .executeWithoutResult(
            ignored -> {
              var group = groups.findAllForUpdateByIdIn(List.of(id)).getFirst();
              engine.updateDetails(
                  new UpdateOrchidGroupMutationCommand(
                      source(),
                      id,
                      new OrchidGroupMutationDetails(
                          group.getVariety().getId(),
                          group.getQuantity(),
                          group.getPotSize(),
                          group.getAgeYear(),
                          status,
                          group.getPlacementType(),
                          group.getTrayCount(),
                          group.getSplitPlacementAllowed(),
                          group.getStartPosition(),
                          group.getEndPosition(),
                          group.getMemo()),
                      DATE,
                      "상태 변경"));
            });
  }

  private OrchidGroupMutationSource source() {
    var key = UUID.randomUUID();
    return new OrchidGroupMutationSource(
        OrchidGroupMutationSourceDomain.FARM, "SALEABILITY_TEST", key.toString(), "APPLY", key);
  }

  private SalesSlipCommand request(int first, int second) {
    var allocations = new ArrayList<SalesSlipAllocationInput>();
    if (first > 0) allocations.add(new SalesSlipAllocationInput(firstId, first));
    if (second > 0) allocations.add(new SalesSlipAllocationInput(secondId, second));
    return new SalesSlipCommand(
        DATE,
        SalesType.DIRECT,
        partnerId,
        null,
        "미입금",
        SalesSlip.STATUS_DRAFT,
        null,
        "상태 정책",
        List.of(
            new SalesSlipItemInput(
                "E2E 난", "팔레놉시스", null, first + second, 1000, null, allocations)));
  }

  private void assertStock(Long id, int quantity, int reserved) {
    var state = reader.getStates(List.of(id)).get(id);
    assertThat(state.quantity()).isEqualTo(quantity);
    assertThat(state.reservedQuantity()).isEqualTo(reserved);
  }

  private TransactionTemplate transaction() {
    return new TransactionTemplate(transactionManager);
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
      rows.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    }
    return rows;
  }

  private Throwable compete(Runnable winner, Runnable loser) throws Exception {
    var winnerReady = new CountDownLatch(1);
    var loserReady = new CountDownLatch(1);
    var commitWinner = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  transaction()
                      .executeWithoutResult(
                          ignored -> {
                            winnerPid.set(
                                jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            winner.run();
                            winnerReady.countDown();
                            await(commitWinner);
                          }));
      try {
        assertThat(winnerReady.await(10, TimeUnit.SECONDS)).isTrue();
        var second =
            executor.submit(
                () -> {
                  try {
                    transaction()
                        .executeWithoutResult(
                            ignored -> {
                              loserPid.set(
                                  jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                              loserReady.countDown();
                              loser.run();
                            });
                    return (Throwable) null;
                  } catch (RuntimeException exception) {
                    return exception;
                  }
                });
        assertThat(loserReady.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean blocked = false;
        while (!second.isDone() && System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT ? = ANY(pg_blocking_pids(?))",
                      Boolean.class,
                      winnerPid.get(),
                      loserPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).as("loser waits for the winning PostgreSQL row lock").isTrue();
        commitWinner.countDown();
        first.get(20, TimeUnit.SECONDS);
        return second.get(20, TimeUnit.SECONDS);
      } finally {
        commitWinner.countDown();
      }
    }
  }

  private void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
