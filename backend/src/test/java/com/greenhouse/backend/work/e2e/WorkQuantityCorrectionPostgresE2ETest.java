package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkQuantityCorrectionPostgresE2ETest extends WorkUndoSafetyTestBase {

	@Test
	void resultIncreaseMustNotInventGrowthOrRewriteLoss() throws Exception {
		var response = post(path(), """
				{"idempotencyKey":"unexplained","workDate":"2026-07-15","reason":"수량 차이",
				"orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":110,"status":"정상"}]}
				""".formatted(result()));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operation_corrections", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, result()))
			.isEqualTo(60);
	}

	@Test
	void rejectsUnbalancedCorrectionWithoutChangingGroupOrAudit() throws Exception {
		var response = post(path(), request("invalid", 100, 15, 0));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operation_corrections", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, result()))
			.isEqualTo(60);
	}

	@Test
	void explicitInputErratumPreservesSourceAndInitialSnapshot() throws Exception {
		String snapshot = jdbc
			.queryForObject("SELECT result_details::text FROM work_applied_effects ORDER BY id LIMIT 1", String.class);
		var response = post(path(), request("input-error", 120, 15, 0));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		assertThat(jdbc.queryForObject("SELECT result_details::text FROM work_applied_effects ORDER BY id LIMIT 1",
				String.class))
			.isEqualTo(snapshot);
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, source()))
			.isZero();
		var balances = get(path()).data().path("quantityBalances");
		assertThat(balances.get(0).path("inputQuantity").asInt()).isEqualTo(120);
		assertThat(balances.get(0).path("resultQuantity").asInt()).isEqualTo(105);
		assertThat(balances.get(0).path("lossQuantity").asInt()).isEqualTo(15);
		assertThat(get(path()).data()
			.path("corrections")
			.get(0)
			.path("quantityBalances")
			.get(0)
			.path("before")
			.path("inputQuantity")
			.asInt()).isEqualTo(100);
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	@Test
	void explicitGrowthAndLossBalanceIsSavedAndCanBeCorrectedAgain() throws Exception {
		var response = post(path(), request("growth", 100, 15, 20));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		var again = post(path(), request("growth-again", 100, 10, 15));
		assertThat(again.status()).as(again.body().toString()).isEqualTo(201);
		assertThat(get(path()).data().path("quantityBalances").get(0).path("increaseQuantity").asInt()).isEqualTo(15);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operation_corrections", Long.class)).isEqualTo(2);
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	private long result() {
		return jdbc.queryForObject("SELECT max(id) FROM orchid_groups WHERE start_position=6", Long.class);
	}

	private long source() {
		return jdbc.queryForObject("SELECT id FROM orchid_groups WHERE quantity=0", Long.class);
	}

	private String path() {
		return "/api/work-operations/" + jdbc.queryForObject("SELECT min(id) FROM work_operations", Long.class)
				+ "/corrections";
	}

	private String request(String key, int input, int loss, int growth) {
		long effect = jdbc.queryForObject("SELECT min(id) FROM work_applied_effects", Long.class);
		return """
				{"idempotencyKey":"%s","workDate":"2026-07-15","reason":"작업 입력 오류 정정",
				"orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":65,"status":"정상"}],
				"quantityCorrections":[{"executionId":%d,"sourceInputQuantities":{"%d":%d},"lossQuantity":%d,"increaseQuantity":%d}]}
				"""
			.formatted(key, result(), effect, source(), input, loss, growth);
	}

}
