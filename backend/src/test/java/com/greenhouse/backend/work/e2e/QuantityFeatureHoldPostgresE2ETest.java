package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuantityFeatureHoldPostgresE2ETest extends WorkUndoSafetyTestBase {

	@Test
	void stockCountIsDisabledAndRollsBackReceipt() throws Exception {
		long group = result();
		var context = get("/api/orchid-groups/" + group + "/stock-count-context").data();
		assertThat(context.path("adjustable").asBoolean()).isFalse();
		var response = post("/api/orchid-groups/" + group + "/stock-counts", """
				{"idempotencyKey":"held-count","countedDate":"%s","expectedRevision":%d,
				 "actualQuantity":70,"reason":"실사"}
				""".formatted(context.path("businessDate").asText(), context.path("stateRevision").asLong()));
		assertHeld(response);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_stock_counts", Long.class)).isZero();
		assertUnchanged();
	}

	@Test
	void declaredInputOnlyCorrectionIsDisabled() throws Exception {
		assertThat(get(path()).data().path("quantityCorrectionEnabled").asBoolean()).isFalse();
		long execution = jdbc.queryForObject("SELECT min(id) FROM work_applied_effects", Long.class);
		var response = post(path(), """
				{"idempotencyKey":"held-input","workDate":"2026-07-15","reason":"투입 정정",
				 "orchidGroupAdjustments":[],
				 "quantityCorrections":[{"executionId":%d,"lossQuantity":0,"increaseQuantity":0}]}
				""".formatted(execution));
		assertHeld(response);
		assertUnchanged();
	}

	@Test
	void legacyResultQuantityPayloadCannotBypassHold() throws Exception {
		var response = post(path(), correction("held-quantity", 70, "정상", false));
		assertHeld(response);
		assertUnchanged();
	}

	@Test
	void dateCorrectionRemainsAvailable() throws Exception {
		var response = post(path(), """
				{"idempotencyKey":"date","workDate":"2026-07-14","reason":"작업일 정정",
				 "orchidGroupAdjustments":[]}
				""");
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		assertThat(response.data().path("corrections").size()).isEqualTo(1);
	}

	@Test
	void statusCorrectionRemainsAvailable() throws Exception {
		var response = post(path(), correction("status", 60, "관리 필요", false));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, result()))
			.isEqualTo(60);
	}

	@Test
	void resultCreationCancellationRemainsAvailable() throws Exception {
		var response = post(path(), correction("cancel", 60, "정상", true));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE start_position=6", Integer.class))
			.isZero();
	}

	private String correction(String key, int quantity, String status, boolean cancel) {
		return """
				{"idempotencyKey":"%s","workDate":"2026-07-15","reason":"정정",
				 "cancelResultCreation":%s,
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":%d,"status":"%s"}]}
				""".formatted(key, cancel, result(), quantity, status);
	}

	private void assertHeld(ApiResult response) {
		assertThat(response.status()).as(response.body().toString()).isEqualTo(409);
		assertThat(response.body().toString()).contains("FEATURE_ON_HOLD");
	}

	private void assertUnchanged() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operation_corrections", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_correction_receipts", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, result()))
			.isEqualTo(60);
	}

	private long result() {
		return jdbc.queryForObject("SELECT id FROM orchid_groups WHERE start_position=6", Long.class);
	}

	private String path() {
		return "/api/work-operations/" + jdbc.queryForObject("SELECT min(id) FROM work_operations", Long.class)
				+ "/corrections";
	}

}
