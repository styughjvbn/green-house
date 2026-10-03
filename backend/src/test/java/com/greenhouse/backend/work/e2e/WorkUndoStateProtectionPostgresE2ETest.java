package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.greenhouse.backend.farm.application.inbound.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordCancelRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordUpdateRequest;
import com.greenhouse.backend.work.application.effect.InboundPottingCommand;
import com.greenhouse.backend.work.application.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.application.operation.InboundPottingPlanService;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WorkUndoStateProtectionPostgresE2ETest extends WorkUndoSafetyTestBase {

	@Autowired
	InboundRecordService inbounds;

	@Autowired
	InboundPottingOperationService potting;

	@Autowired
	InboundPottingPlanService plans;

	@Autowired
	PlatformTransactionManager transactions;

	@org.springframework.boot.test.web.server.LocalServerPort
	int port;

	private final LocalDate date = LocalDate.of(2026, 8, 20);

	private long inbound() {
		long variety = jdbc.queryForObject("select min(variety_id) from orchid_groups", Long.class);
		return inbounds
			.create(new InboundRecordCreateCommand(date, InboundType.FLASK_SEEDLING, variety, null, 20, "배양실", date,
					null, "audit", null))
			.id();
	}

	private long execute(long inbound) {
		long zone = jdbc.queryForObject("select min(bed_zone_id) from orchid_groups", Long.class);
		return potting.executeNow(new InboundPottingCommand("audit-potting", inbound, date,
				List.of(new InboundPottingResultInput(zone, 20, "2인치", 1, "트레이", 1, false, BigDecimal.valueOf(12),
						BigDecimal.valueOf(14), null)),
				"audit", null))
			.id();
	}

	@Test
	void ordinaryPatchCannotResurrectAnUndoneResult() throws Exception {
		long operation = jdbc.queryForObject("select min(id) from work_operations", Long.class);
		long group = jdbc.queryForObject("select min(id) from orchid_groups where quantity = 60", Long.class);
		long variety = jdbc.queryForObject("select variety_id from orchid_groups where id = ?", Long.class, group);
		assertThat(post("/api/work-operations/" + operation + "/cancel",
				"{\"idempotencyKey\":\"undo-audit\",\"reason\":\"audit\"}")
			.status()).isEqualTo(200);
		var request = java.net.http.HttpRequest
			.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/orchid-groups/" + group))
			.header("Content-Type", "application/json")
			.method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString("{\"varietyId\":" + variety
					+ ",\"quantity\":60,\"potSize\":\"4치\",\"ageYear\":3,\"status\":\"정상\",\"startPosition\":6,\"endPosition\":8}"))
			.build();
		var response = java.net.http.HttpClient.newHttpClient()
			.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
		assertThat(jdbc.queryForObject("select sum(quantity) from orchid_groups", Long.class)).isEqualTo(100);
		assertThat(jdbc.queryForObject("select status from orchid_groups where id = ?", String.class, group))
			.isEqualTo("생성 취소");
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	@Test
	void stoppedPottingDoesNotAdvertiseUnavailableActionsInDetailOrList() throws Exception {
		long first = inbound(), sibling = inbound();
		var plan = plans
			.create(new InboundPottingPlanCreateRequest("audit", date, date, List.of(first, sibling), "audit", null));
		assertThat(execute(first)).isEqualTo(plan.id());
		assertThat(get("/api/inbound-records/" + first).data().path("availableActions").toString())
			.contains("VOID_POTTING", "CANCEL");
		assertThat(post("/api/work-operations/" + plan.id() + "/end-remaining", "{}").status()).isEqualTo(200);
		assertThat(get("/api/inbound-records/" + first).data().path("availableActions").isEmpty()).isTrue();
		var page = get("/api/inbound-records?page=0&size=20");
		assertThat(page.status()).as(page.body().toString()).isEqualTo(200);
		var row = java.util.stream.StreamSupport.stream(page.data().path("content").spliterator(), false)
			.filter(item -> item.path("id").asLong() == first)
			.findFirst()
			.orElseThrow();
		assertThat(row.path("availableActions").isEmpty()).isTrue();
		assertThat(get("/api/inbound-records/" + sibling).data().path("availableActions").toString())
			.contains("CANCEL");
		var undo = post("/api/inbound-records/" + first + "/potting-void",
				"{\"idempotencyKey\":\"stopped-undo\",\"reason\":\"audit\"}");
		assertThat(undo.status()).as(undo.body().toString()).isEqualTo(400);
	}

	@Test
	void metadataUpdateCannotOverwriteConcurrentCancellation() throws Exception {
		long inbound = inbound();
		updateAfterTransition(inbound,
				() -> inbounds.cancel(inbound, new InboundRecordCancelRequest("audit-cancel", "cancel")));
		assertThat(jdbc.queryForObject("select status from inbound_records where id = ?", String.class, inbound))
			.isEqualTo("CANCELED");
		assertThat(jdbc.queryForObject(
				"select status from work_operations where work_type_id = (select id from work_types where code = 'INBOUND')",
				String.class))
			.isEqualTo("CANCELED");
	}

	@Test
	void metadataUpdateCannotOverwriteConcurrentPottingCompletion() throws Exception {
		long inbound = inbound();
		updateAfterTransition(inbound, () -> execute(inbound));
		assertThat(jdbc.queryForObject("select status from inbound_records where id = ?", String.class, inbound))
			.isEqualTo("PLACED");
		assertThat(jdbc.queryForObject("select estimated_quantity from inbound_records where id = ?", Integer.class,
				inbound))
			.isEqualTo(20);
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	private void updateAfterTransition(long inbound, Runnable transition) throws Exception {
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(1)) {
			var update = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
			new TransactionTemplate(transactions).executeWithoutResult(tx -> {
				jdbc.queryForObject("select id from inbound_records where id = ? for update", Long.class, inbound);
				update.set(executor.submit(() -> inbounds.update(inbound,
						new InboundRecordUpdateRequest(date, 25, "변경", date, "audit", "edited"))));
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
				while (jdbc.queryForObject("select count(*) from pg_locks where not granted", Integer.class) == 0) {
					if (System.nanoTime() > deadline)
						throw new AssertionError("No update lock wait");
					try {
						Thread.sleep(20);
					}
					catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}
				transition.run();
			});
			assertThatThrownBy(() -> update.get().get(15, TimeUnit.SECONDS))
				.isInstanceOf(java.util.concurrent.ExecutionException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class);
		}
	}

}
