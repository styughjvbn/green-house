package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.greenhouse.backend.OrchidGroupStateChainTestSupport;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.SalesInventoryMovement;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

	@Autowired
	private WorkTestDataSeeder seeder;

	@Autowired
	private OrchidGroupRepository groups;

	@Autowired
	private BusinessPartnerRepository partners;

	@Autowired
	private SalesSlipRepository slips;

	@Autowired
	private SalesInventoryMovementRepository movements;

	@Autowired
	private SalesSlipCreationService creation;

	@Autowired
	private SalesSlipUpdateService updates;

	@Autowired
	private SalesSlipStatusService statuses;

	@Autowired
	private SalesQueryService queries;

	@Autowired
	private OrchidGroupStateChainMigrationService migration;

	@Autowired
	private OrchidGroupLedgerCutoverService cutover;

	@Autowired
	private OrchidGroupLedgerReconciliationService reconciliation;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private OrchidGroupMutationRoutingPolicy routing;

	@MockitoSpyBean
	private OrchidGroupReader reader;

	private Long groupId;

	@BeforeEach
	void seed() {
		seeder.reset();
		groupId = seeder.seedContractScenario().orchidGroupId();
	}

	@ParameterizedTest
	@CsvSource({ "false,DIRECT", "true,DIRECT", "false,AUCTION", "true,AUCTION" })
	void preservesSnapshotsAndPerAllocationHistoryAcrossCompletionAndCancellation(boolean engine, SalesType type) {
		activate(engine);
		var partner = partner(type);
		var created = creation.create(request(partner, type, DATE, 3, 2));
		assertStock(100, 5);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_RESERVE, engine, 3, 2);
		assertThat(created.items())
			.allSatisfy(item -> assertThat(item.allocations()).singleElement().satisfies(line -> {
				assertThat(line.creationSnapshot().quantity()).isEqualTo(100);
				assertThat(line.creationSnapshot().reservedQuantity()).isZero();
				assertThat(line.availableQuantity()).isEqualTo(95);
			}));
		var persistedCreation = queries.getSalesSlip(created.id());

		String completedStatus = type == SalesType.DIRECT ? SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED
				: SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED;
		var completed = statuses.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(completedStatus, null));
		statuses.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(completedStatus, null));
		assertStock(95, 0);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_OUTBOUND, engine, -3, -2);
		assertThat(completed.items()).allSatisfy(item -> {
			var line = item.allocations().getFirst();
			assertThat(line.outboundSnapshot().quantity()).isEqualTo(100);
			assertThat(line.outboundSnapshot().reservedQuantity()).isEqualTo(5);
			assertThat(line.creationSnapshot().quantity()).isEqualTo(100);
			assertThat(line.creationSnapshot().reservedQuantity()).isZero();
		});
		if (type == SalesType.AUCTION) {
			assertThat(completed.auctionShipmentId()).isNotNull();
			assertThat(completed.items()).allSatisfy(item -> assertThat(item.auctionShipmentLotId()).isNotNull());
		}
		var persistedCompletion = queries.getSalesSlip(created.id());

		statuses.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
		assertStock(100, 0);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND, engine, 3, 2);
		var canceled = queries.getSalesSlip(created.id());
		for (int index = 0; index < canceled.items().size(); index++) {
			var line = canceled.items().get(index).allocations().getFirst();
			assertThat(line.availableQuantity()).isEqualTo(100);
			assertThat(line.creationSnapshot())
				.isEqualTo(persistedCreation.items().get(index).allocations().getFirst().creationSnapshot());
			assertThat(line.outboundSnapshot())
				.isEqualTo(persistedCompletion.items().get(index).allocations().getFirst().outboundSnapshot());
		}
		if (engine) {
			Long outboundId = movements
				.findBySalesSlipIdAndChangeType(created.id(), SalesInventoryMovementType.SALES_OUTBOUND)
				.getFirst()
				.getMutationId();
			Long restoreId = movements
				.findBySalesSlipIdAndChangeType(created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND)
				.getFirst()
				.getMutationId();
			assertThat(jdbc.queryForObject(
					"select count(*) from orchid_group_mutation_relations "
							+ "where mutation_id = ? and related_mutation_id = ? and relation_type = 'COMPENSATES'",
					Long.class, restoreId, outboundId))
				.isEqualTo(1L);
			assertThat(reconciliation.reconcile().ready()).isTrue();
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void editsReleaseAndReserveTogetherAndFailedEditsRollBack(boolean engine) {
		activate(engine);
		var partner = partner(SalesType.DIRECT);
		var created = creation.create(request(partner, SalesType.DIRECT, DATE, 3, 2));
		updates.update(created.id(), request(partner, SalesType.DIRECT, DATE, 4, 2));
		assertStock(100, 6);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_RELEASE, engine, -3, -2);
		long before = movements.count();
		var updated = queries.getSalesSlip(created.id());

		assertThatThrownBy(() -> updates.update(created.id(), request(partner, SalesType.DIRECT, DATE, 99, 2)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("가용 수량");
		assertStock(100, 6);
		assertThat(movements.count()).isEqualTo(before);
		assertThat(queries.getSalesSlip(created.id())).isEqualTo(updated);
		statuses.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
		assertStock(100, 0);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_RESERVE, engine, -4, -2);
		if (engine)
			assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void laterFailureRollsBackStockSnapshotsShipmentsAndMovements(boolean engine) {
		activate(engine);
		var created = creation.create(request(partner(SalesType.AUCTION), SalesType.AUCTION, DATE, 3, 2));
		long before = movements.count();
		long shipmentsBefore = jdbc.queryForObject("select count(*) from auction_shipments", Long.class);
		var beforeSlip = queries.getSalesSlip(created.id());
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			statuses.updateStatus(created.id(),
					new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED, null));
			entityManager.flush();
			throw new IllegalStateException("later failure");
		})).isInstanceOf(IllegalStateException.class).hasMessage("later failure");
		assertStock(100, 5);
		assertThat(movements.count()).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from auction_shipments", Long.class))
			.isEqualTo(shipmentsBefore);
		assertThat(queries.getSalesSlip(created.id())).isEqualTo(beforeSlip);
		if (engine)
			assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void concurrentReservationsLockGroupsInIdOrderAndRejectOverbooking(boolean engine) throws Exception {
		Long secondId = jdbc.queryForObject(
				"""
						insert into orchid_groups (created_at, updated_at, age_year, genus, placement_type, pot_size, pot_size_code,
						  quantity, sort_order, status, variety_name, bed_zone_id, split_placement_allowed,
						  variety_id, start_position, end_position, reserved_quantity)
						select created_at, updated_at, age_year, genus, placement_type, pot_size, pot_size_code,
						  100, 2, status, variety_name, bed_zone_id, split_placement_allowed, variety_id, 5, 10, 0
						from orchid_groups where id = ? returning id
						""",
				Long.class, groupId);
		activate(engine);
		var first = request(partner(SalesType.DIRECT), List.of(groupId, secondId), DATE);
		var second = request(partner(SalesType.DIRECT), List.of(secondId, groupId), DATE.plusDays(1));
		long slipsBefore = slips.count();
		var arrivals = new CountDownLatch(2);
		doAnswer(invocation -> {
			arrivals.countDown();
			assertThat(arrivals.await(10, TimeUnit.SECONDS)).isTrue();
			return invocation.callRealMethod();
		}).when((OrchidGroupReader) AopTestUtils.getUltimateTargetObject(reader)).lockStates(anyCollection());
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
		if (engine)
			assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	@Test
	void restoresPreCutoverOutboundWithoutInventingACompensationLink() {
		activate(false);
		var created = creation.create(request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2));
		statuses.updateStatus(created.id(),
				new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
		activate(true);
		statuses.updateStatus(created.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
		assertStock(100, 0);
		assertMovement(created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND, true, 3, 2);
		var mutationId = movements
			.findBySalesSlipIdAndChangeType(created.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND)
			.getFirst()
			.getMutationId();
		assertThat(jdbc.queryForObject("select count(*) from orchid_group_mutation_relations where mutation_id = ?",
				Long.class, mutationId))
			.isZero();
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	@Test
	void salesHttpContractsExposeCurrentValuesSeparatelyFromHistoricalSnapshots() throws Exception {
		var result = post("/api/sales-slips",
				objectMapper.writeValueAsString(request(partner(SalesType.DIRECT), SalesType.DIRECT, DATE, 3, 2)));
		assertThat(result.status()).isEqualTo(201);
		var detail = get("/api/sales-slips/" + result.data().path("id").asLong());
		assertThat(detail.status()).isEqualTo(200);
		var line = detail.data().path("items").get(0).path("allocations").get(0);
		assertThat(line.path("orchidGroupId").asLong()).isEqualTo(groupId);
		assertThat(line.path("allocatedQuantity").asInt()).isEqualTo(3);
		assertThat(line.path("availableQuantity").asInt()).isEqualTo(95);
		assertThat(line.path("creationSnapshot").path("reservedQuantity").asInt()).isZero();
		assertThat(line.path("outboundSnapshot").isNull()).isTrue();
		var search = get("/api/sales/orchid-groups/search?status="
				+ java.net.URLEncoder.encode("정상", java.nio.charset.StandardCharsets.UTF_8));
		assertThat(search.status()).isEqualTo(200);
		assertThat(search.data()).hasSize(1);
		assertThat(search.data().get(0).path("id").asLong()).isEqualTo(groupId);
		assertThat(search.data().get(0).path("reservedQuantity").asInt()).isEqualTo(5);
		assertThat(search.data().get(0).path("availableQuantity").asInt()).isEqualTo(95);
		assertThat(search.data().get(0).path("houseNumber")).isEqualTo(line.path("houseNumber"));
	}

	@Test
	void scalarGroupIdsStillRequireExistingFarmRowsInPostgres() {
		var slip = new SalesSlip("FK-SALES", DATE, SalesType.DIRECT, null, partner(SalesType.DIRECT), "미입금",
				SalesSlip.STATUS_DRAFT, null, null);
		var item = new SalesSlipItem(null, "E2E 난", null, null, 1, 100, null);
		item.addAllocation(new SalesSlipItemAllocation(-1L, 1));
		slip.addItem(item);
		assertThatThrownBy(() -> slips.saveAndFlush(slip)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("foreign key");
		var saved = slips.saveAndFlush(new SalesSlip("FK-MOVEMENT", DATE, SalesType.DIRECT, null, slip.getPartnerId(),
				"미입금", SalesSlip.STATUS_DRAFT, null, null));
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			var managed = slips.findById(saved.getId()).orElseThrow();
			managed.addItem(new SalesSlipItem(null, "E2E 난", null, null, 1, 100, null));
			entityManager.flush();
		});
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			var managed = slips.findWithDetailsById(saved.getId()).orElseThrow();
			movements.saveAndFlush(new SalesInventoryMovement(-1L, managed, managed.getItems().getFirst(),
					SalesInventoryMovementType.SALES_RESERVE, 1, null));
		})).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("foreign key");
	}

	private void activate(boolean engine) {
		// Exercise the same service graph in both rollout modes; ENGINE also runs with
		// the real PostgreSQL fence.
		doReturn(engine).when(routing).routesToEngine();
		if (!engine)
			return;
		assertThat(reconciliation.reconcile().issues()).isEmpty();
		var key = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(migration, groups, key, DATE, "1.0.0");
		cutover.execute(new OrchidGroupLedgerCutoverCommand(key, DATE, "1.0.0", "1.1.0", true));
	}

	private Long partner(SalesType type) {
		return partners.saveAndFlush(new BusinessPartner("재고 경계 " + UUID.randomUUID(),
				type == SalesType.DIRECT ? PartnerType.WHOLESALE : PartnerType.AUCTION_HOUSE, null, null, null, null))
			.getId();
	}

	private SalesSlipCommand request(Long partner, SalesType type, LocalDate date, int first, int second) {
		return new SalesSlipCommand(date, type, partner, null, "미입금", SalesSlip.STATUS_DRAFT, null, null,
				List.of(item(List.of(new SalesSlipAllocationInput(groupId, 1),
						new SalesSlipAllocationInput(groupId, first - 1))),
						item(List.of(new SalesSlipAllocationInput(groupId, second)))));
	}

	private SalesSlipCommand request(Long partner, List<Long> ids, LocalDate date) {
		return new SalesSlipCommand(date, SalesType.DIRECT, partner, null, "미입금", SalesSlip.STATUS_DRAFT, null, null,
				List.of(item(ids.stream().map(id -> new SalesSlipAllocationInput(id, 70)).toList())));
	}

	private SalesSlipItemInput item(List<SalesSlipAllocationInput> allocations) {
		return new SalesSlipItemInput("E2E 난", "팔레놉시스", null,
				allocations.stream().mapToInt(SalesSlipAllocationInput::quantity).sum(), 100, null, allocations);
	}

	private boolean tryCreate(SalesSlipCommand request) {
		try {
			creation.create(request);
			return true;
		}
		catch (IllegalArgumentException exception) {
			assertThat(exception).hasMessageContaining("가용 수량");
			return false;
		}
	}

	private void assertStock(int quantity, int reserved) {
		var group = groups.findById(groupId).orElseThrow();
		assertThat(group.getQuantity()).isEqualTo(quantity);
		assertThat(group.getReservedQuantity()).isEqualTo(reserved);
	}

	private void assertMovement(Long slipId, SalesInventoryMovementType type, boolean engine, Integer... deltas) {
		var rows = movements.findBySalesSlipIdAndChangeType(slipId, type);
		assertThat(rows).extracting(SalesInventoryMovement::getQuantityDelta).containsExactlyInAnyOrder(deltas);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.getOrchidGroupId()).isEqualTo(groupId);
			assertThat(row.getMutationId() != null).isEqualTo(engine);
			assertThat(row.getCorrelationId() != null).isEqualTo(engine);
		});
		assertThat(rows.stream().map(SalesInventoryMovement::getMutationId).distinct().count()).isEqualTo(1);
	}

}
