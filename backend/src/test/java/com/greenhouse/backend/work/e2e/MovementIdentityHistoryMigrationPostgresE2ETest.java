package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class MovementIdentityHistoryMigrationPostgresE2ETest extends WorkE2ETestBase {

	@Test
	void consolidatesHistoricalOneToOneMovementResultsIntoTheirOriginalGroupIds() {
		String database = "movement_identity_" + UUID.randomUUID().toString().replace("-", "");
		var admin = new JdbcTemplate(
				new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
		admin.execute("CREATE DATABASE " + database);
		String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
		var dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
		try {
			Flyway.configure().dataSource(dataSource).target("35").load().migrate();
			var jdbc = new JdbcTemplate(dataSource);
			seedHistoricalMovement(jdbc);

			var upgrade = Flyway.configure().dataSource(dataSource).target("36").load();
			assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);

			assertThat(jdbc.queryForList("""
					SELECT id, quantity, state_revision, start_position, end_position
					FROM orchid_groups WHERE id IN (1, 2, 11, 12) ORDER BY id
					""")).containsExactly(
					Map.of("id", 1L, "quantity", 10, "state_revision", 2L, "start_position", new BigDecimal("20.00"),
							"end_position", new BigDecimal("21.00")),
					Map.of("id", 2L, "quantity", 20, "state_revision", 1L, "start_position", new BigDecimal("10.00"),
							"end_position", new BigDecimal("12.00")));
			assertThat(jdbc.queryForObject("SELECT mutation_type FROM orchid_group_mutations WHERE id = 100",
					String.class))
				.isEqualTo("MOVE");
			assertThat(jdbc.queryForList("""
					SELECT orchid_group_id, role, state_revision_before, state_revision_after
					FROM orchid_group_mutation_entries
					WHERE mutation_id = 100 ORDER BY orchid_group_id
					""")).containsExactly(
					Map.of("orchid_group_id", 1L, "role", "AFFECTED", "state_revision_before", 0L,
							"state_revision_after", 1L),
					Map.of("orchid_group_id", 2L, "role", "AFFECTED", "state_revision_before", 0L,
							"state_revision_after", 1L));
			assertThat(jdbc.queryForList("""
					SELECT orchid_group_id, state_revision_before, state_revision_after
					FROM orchid_group_mutation_entries WHERE mutation_id = 101
					""")).containsExactly(
					Map.of("orchid_group_id", 1L, "state_revision_before", 1L, "state_revision_after", 2L));
			assertThat(jdbc.queryForList("""
					SELECT preserved_orchid_group_id, removed_orchid_group_id
					FROM orchid_group_identity_migrations ORDER BY removed_orchid_group_id
					""")).containsExactly(Map.of("preserved_orchid_group_id", 1L, "removed_orchid_group_id", 11L),
					Map.of("preserved_orchid_group_id", 2L, "removed_orchid_group_id", 12L));
			assertThat(jdbc.queryForObject("""
					SELECT orchid_group_id FROM work_operation_targets WHERE id = 201
					""", Long.class)).isEqualTo(1L);
			String resultDetails = jdbc.queryForObject("SELECT result_details FROM work_applied_effects WHERE id = 100",
					String.class);
			assertThat(resultDetails).contains("\"identityPreserved\": true")
				.contains("\"orchidGroupId\": 1")
				.contains("\"orchidGroupId\": 2")
				.doesNotContain("\"orchidGroupId\": 11", "\"orchidGroupId\": 12");
			assertThat(upgrade.migrate().migrationsExecuted).isZero();
		}
		finally {
			admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
		}
	}

	@Test
	void restoresSourceMetadataDroppedByTheLegacyOneToOneMovementTransformer() {
		String database = "movement_metadata_" + UUID.randomUUID().toString().replace("-", "");
		var admin = new JdbcTemplate(
				new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
		admin.execute("CREATE DATABASE " + database);
		String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
		var dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
		try {
			Flyway.configure().dataSource(dataSource).target("36").load().migrate();
			var jdbc = new JdbcTemplate(dataSource);
			seedLegacyMetadataLoss(jdbc);

			var upgrade = Flyway.configure().dataSource(dataSource).target("37").load();
			assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
			assertThat(jdbc.queryForList("""
					SELECT id, quantity, status, memo, start_position, end_position
					FROM orchid_groups WHERE id IN (21, 31)
					""")).containsExactly(Map.of("id", 21L, "quantity", 10, "status", "정상", "memo", "원본 메모",
					"start_position", new BigDecimal("5.00"), "end_position", new BigDecimal("6.00")));
			assertThat(jdbc.queryForObject("SELECT mutation_type FROM orchid_group_mutations WHERE id = 200",
					String.class))
				.isEqualTo("MOVE");
			assertThat(jdbc.queryForObject("""
					SELECT after_state ->> 'memo' FROM orchid_group_mutation_entries
					WHERE mutation_id = 200 AND orchid_group_id = 21
					""", String.class)).isEqualTo("원본 메모");
			assertThat(
					jdbc.queryForObject("SELECT result_details FROM work_applied_effects WHERE id = 200", String.class))
				.contains("\"identityPreserved\": true")
				.contains("\"orchidGroupId\": 21");
			assertThat(upgrade.migrate().migrationsExecuted).isZero();
		}
		finally {
			admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
		}
	}

	private void seedHistoricalMovement(JdbcTemplate jdbc) {
		Long zoneId = jdbc.queryForObject("SELECT min(id) FROM bed_zones", Long.class);
		jdbc.execute("ALTER TABLE orchid_groups DISABLE TRIGGER USER");
		jdbc.update("""
				INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
				VALUES (9001, 'MOVE-ID-MIGRATION', '속', '이동 품종', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""");
		jdbc.update("""
				INSERT INTO orchid_groups (
				  id, bed_zone_id, variety_id, genus, variety_name, quantity, reserved_quantity,
				  sort_order, status, pot_size_code, version, state_revision,
				  start_position, end_position, created_at, updated_at
				) VALUES
				  (1, ?, 9001, '속', '이동 품종', 0, 0, 1, '종료', 'POT_3', 0, 1, 0, 1,
				   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				  (2, ?, 9001, '속', '이동 품종', 0, 0, 2, '종료', 'POT_3', 0, 1, 1, 3,
				   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				  (11, ?, 9001, '속', '이동 품종', 10, 0, 3, '정상', 'POT_3', 0, 2, 20, 21,
				   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				  (12, ?, 9001, '속', '이동 품종', 20, 0, 4, '정상', 'POT_3', 0, 1, 10, 12,
				   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""", zoneId, zoneId, zoneId, zoneId);
		jdbc.execute("ALTER TABLE orchid_groups ENABLE TRIGGER USER");

		jdbc.update("""
				INSERT INTO work_operations (
				  id, work_type_id, title, status, planned_start_date, actual_start_at, actual_end_at,
				  source_scope_type, target_snapshot_at, details, version, created_at, updated_at
				) VALUES
				  (100, (SELECT id FROM work_types WHERE code = 'MOVEMENT'), '과거 N:N 자리 이동', 'COMPLETED',
				   DATE '2026-08-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MANUAL_SELECTION', CURRENT_TIMESTAMP,
				   '{}'::jsonb, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				  (101, (SELECT id FROM work_types WHERE code = 'MOVEMENT'), '후속 직접 이동', 'COMPLETED',
				   DATE '2026-08-02', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MANUAL_SELECTION', CURRENT_TIMESTAMP,
				   '{}'::jsonb, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""");
		jdbc.update("""
				INSERT INTO work_operation_targets (
				  id, work_operation_id, orchid_group_id, target_reference_type, inclusion_source,
				  included_at, variety_id_snapshot, variety_name_snapshot, quantity_snapshot,
				  location_snapshot, created_at
				) VALUES
				  (100, 100, 1, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9001, '이동 품종', 10,
				   '{}'::jsonb, CURRENT_TIMESTAMP),
				  (101, 100, 2, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9001, '이동 품종', 20,
				   '{}'::jsonb, CURRENT_TIMESTAMP),
				  (201, 101, 11, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9001, '이동 품종', 10,
				   '{}'::jsonb, CURRENT_TIMESTAMP)
				""");
		jdbc.update("""
				INSERT INTO work_target_executions (
				  id, work_operation_target_id, status, result_details, processed_quantity,
				  version, created_at, updated_at
				) VALUES
				  (100, 100, 'COMPLETED', '{}'::jsonb, 10, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				  (101, 101, 'COMPLETED', '{}'::jsonb, 20, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				  (201, 201, 'COMPLETED', '{"orchidGroupId":11}'::jsonb, 10, 0,
				   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""");

		String sourceOne = state(10, zoneId, 1, 0, 1, "정상");
		String sourceTwo = state(20, zoneId, 2, 1, 3, "정상");
		String closedOne = state(0, zoneId, 1, 0, 1, "종료");
		String closedTwo = state(0, zoneId, 2, 1, 3, "종료");
		String resultOne = state(10, zoneId, 3, 5, 6, "정상");
		String resultTwo = state(20, zoneId, 4, 10, 12, "정상");
		String movedAgain = state(10, zoneId, 5, 20, 21, "정상");
		jdbc.update("""
				INSERT INTO orchid_group_mutations (
				  id, mutation_type, source_domain, source_type, source_reference_id, source_operation_key,
				  correlation_id, command_fingerprint, occurred_at, recorded_at,
				  effective_business_date, schema_version
				) VALUES
				  (100, 'TRANSFORM', 'WORK', 'WORK_EFFECT', '100', 'EXECUTION:old-move',
				   '00000000-0000-0000-0000-000000000100', repeat('1', 64), CURRENT_TIMESTAMP,
				   CURRENT_TIMESTAMP, DATE '2026-08-01', 1),
				  (101, 'MOVE', 'WORK', 'WORK_EFFECT', '101', 'TARGET:201',
				   '00000000-0000-0000-0000-000000000101', repeat('2', 64), CURRENT_TIMESTAMP + interval '1 day',
				   CURRENT_TIMESTAMP, DATE '2026-08-02', 1)
				""");
		jdbc.update("""
				INSERT INTO orchid_group_mutation_entries (
				  id, mutation_id, orchid_group_id, entry_kind, role, state_revision_before,
				  state_revision_after, before_state, after_state
				) VALUES
				  (100, 100, 1, 'CHANGE', 'SOURCE', 0, 1, CAST(? AS jsonb), CAST(? AS jsonb)),
				  (101, 100, 2, 'CHANGE', 'SOURCE', 0, 1, CAST(? AS jsonb), CAST(? AS jsonb)),
				  (102, 100, 11, 'CREATE', 'RESULT', NULL, 1, NULL, CAST(? AS jsonb)),
				  (103, 100, 12, 'CREATE', 'RESULT', NULL, 1, NULL, CAST(? AS jsonb)),
				  (104, 101, 11, 'CHANGE', 'AFFECTED', 1, 2, CAST(? AS jsonb), CAST(? AS jsonb))
				""", sourceOne, closedOne, sourceTwo, closedTwo, resultOne, resultTwo, resultOne, movedAgain);
		jdbc.update("""
				INSERT INTO work_applied_effects (
				  id, work_operation_id, work_operation_target_id, effect_key, effect_kind,
				  handler_code, applied_at, command_details, result_details, created_at, updated_at,
				  mutation_id
				) VALUES
				  (100, 100, NULL, 'EXECUTION:old-move', 'TARGET_COMPLETION', 'MOVEMENT', CURRENT_TIMESTAMP,
				   CAST(? AS jsonb), CAST(? AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100),
				  (101, 101, 201, 'TARGET:201', 'TARGET_COMPLETION', 'MOVEMENT', CURRENT_TIMESTAMP,
				   '{"orchidGroupId":11}'::jsonb, '{"orchidGroupId":11}'::jsonb,
				   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 101)
				""", commandDetails(zoneId), resultDetails());
		jdbc.update("""
				INSERT INTO work_effect_orchid_groups (
				  work_applied_effect_id, orchid_group_id, relation_type, created_at
				) VALUES
				  (100, 1, 'SOURCE', CURRENT_TIMESTAMP), (100, 2, 'SOURCE', CURRENT_TIMESTAMP),
				  (100, 11, 'RESULT', CURRENT_TIMESTAMP), (100, 12, 'RESULT', CURRENT_TIMESTAMP),
				  (101, 11, 'AFFECTED', CURRENT_TIMESTAMP)
				""");
	}

	private void seedLegacyMetadataLoss(JdbcTemplate jdbc) {
		Long zoneId = jdbc.queryForObject("SELECT min(id) FROM bed_zones", Long.class);
		String source = metadataState(state(10, zoneId, 1, 0, 1, "정상")).replace("\"memo\":null", "\"memo\":\"원본 메모\"");
		String closed = metadataState(state(0, zoneId, 1, 0, 1, "종료")).replace("\"memo\":null", "\"memo\":\"원본 메모\"");
		String result = metadataState(state(10, zoneId, 2, 5, 6, "정상"));
		jdbc.execute("ALTER TABLE orchid_groups DISABLE TRIGGER USER");
		jdbc.update("""
				INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
				VALUES (9101, 'MOVE-METADATA-MIGRATION', '속', '메모 품종', TRUE, TRUE,
				        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""");
		jdbc.update("""
				INSERT INTO orchid_groups (
				  id, bed_zone_id, variety_id, genus, variety_name, quantity, reserved_quantity,
				  sort_order, status, pot_size_code, version, state_revision, memo,
				  start_position, end_position, created_at, updated_at
				) VALUES
				  (21, ?, 9101, '속', '메모 품종', 0, 0, 1, '종료', 'POT_3', 0, 1, '원본 메모',
				   0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				  (31, ?, 9101, '속', '메모 품종', 10, 0, 2, '정상', 'POT_3', 0, 1, NULL,
				   5, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""", zoneId, zoneId);
		jdbc.execute("ALTER TABLE orchid_groups ENABLE TRIGGER USER");
		jdbc.update("""
				INSERT INTO work_operations (
				  id, work_type_id, title, status, planned_start_date, actual_start_at, actual_end_at,
				  source_scope_type, target_snapshot_at, details, version, created_at, updated_at
				) VALUES
				  (200, (SELECT id FROM work_types WHERE code = 'MOVEMENT'), '과거 메모 유실 이동', 'COMPLETED',
				   DATE '2026-09-23', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MANUAL_SELECTION', CURRENT_TIMESTAMP,
				   '{}'::jsonb, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""");
		jdbc.update("""
				INSERT INTO work_operation_targets (
				  id, work_operation_id, orchid_group_id, target_reference_type, inclusion_source,
				  included_at, variety_id_snapshot, variety_name_snapshot, quantity_snapshot,
				  location_snapshot, created_at
				) VALUES (200, 200, 21, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9101, '메모 품종', 10,
				          '{}'::jsonb, CURRENT_TIMESTAMP);
				INSERT INTO work_target_executions (
				  id, work_operation_target_id, status, result_details, processed_quantity,
				  version, created_at, updated_at
				) VALUES (200, 200, 'COMPLETED',
				          '{"results":[{"orchidGroupId":31,"quantity":10,"purpose":"NORMAL"}]}'::jsonb,
				          10, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""");
		jdbc.update("""
				INSERT INTO orchid_group_mutations (
				  id, mutation_type, source_domain, source_type, source_reference_id, source_operation_key,
				  correlation_id, command_fingerprint, occurred_at, recorded_at,
				  effective_business_date, schema_version
				) VALUES (200, 'TRANSFORM', 'WORK', 'WORK_EFFECT', '200', 'EXECUTION:legacy-memo',
				          '00000000-0000-0000-0000-000000000200', repeat('3', 64), CURRENT_TIMESTAMP,
				          CURRENT_TIMESTAMP, DATE '2026-09-23', 1)
				""");
		jdbc.update("""
				INSERT INTO orchid_group_mutation_entries (
				  id, mutation_id, orchid_group_id, entry_kind, role, state_revision_before,
				  state_revision_after, before_state, after_state
				) VALUES
				  (200, 200, 21, 'CHANGE', 'SOURCE', 0, 1, CAST(? AS jsonb), CAST(? AS jsonb)),
				  (201, 200, 31, 'CREATE', 'RESULT', NULL, 1, NULL, CAST(? AS jsonb))
				""", source, closed, result);
		jdbc.update("""
				INSERT INTO work_applied_effects (
				  id, work_operation_id, effect_key, effect_kind, handler_code, applied_at,
				  command_details, result_details, created_at, updated_at, mutation_id
				) VALUES (200, 200, 'EXECUTION:legacy-memo', 'TARGET_COMPLETION', 'MOVEMENT', CURRENT_TIMESTAMP,
				          CAST(? AS jsonb),
				          '{"results":[{"orchidGroupId":31,"quantity":10,"purpose":"NORMAL"}],
				            "sourceInputQuantities":{"21":10},"lossQuantity":0}'::jsonb,
				          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 200)
				""", """
				{"sources":[{"sourceOrchidGroupId":21,"inputQuantity":10}],
				 "results":[{"bedZoneId":%d,"quantity":10,"attributeSourceOrchidGroupId":21,
				             "purpose":"NORMAL","memo":null}]}
				""".formatted(zoneId));
		jdbc.update("""
				INSERT INTO work_effect_orchid_groups
				  (work_applied_effect_id, orchid_group_id, relation_type, created_at)
				VALUES (200, 21, 'SOURCE', CURRENT_TIMESTAMP), (200, 31, 'RESULT', CURRENT_TIMESTAMP);
				INSERT INTO orchid_group_lineage (
				  source_orchid_group_id, result_orchid_group_id, relation_type, work_operation_id,
				  source_quantity, result_quantity, created_at, mutation_id
				) VALUES (21, 31, 'MOVED_TO', 200, 10, 10, CURRENT_TIMESTAMP, 200)
				""");
	}

	private String metadataState(String state) {
		return state.replace("\"varietyId\":9001", "\"varietyId\":9101").replace("이동 품종", "메모 품종");
	}

	private String state(int quantity, Long zoneId, int sortOrder, int start, int end, String status) {
		return """
				{"quantity":%d,"reservedQuantity":0,"status":"%s","bedZoneId":%d,"sortOrder":%d,
				 "startPosition":%d,"endPosition":%d,"varietyId":9001,"genus":"속",
				 "varietyName":"이동 품종","ageYear":2,"potSizeCode":"POT_3",
				 "placementType":null,"trayCount":null,"splitPlacementAllowed":false,
				 "inboundRecordId":null,"memo":null}
				""".formatted(quantity, status, zoneId, sortOrder, start, end);
	}

	private String commandDetails(Long zoneId) {
		return """
				{"sources":[
				  {"sourceOrchidGroupId":1,"inputQuantity":10},
				  {"sourceOrchidGroupId":2,"inputQuantity":20}],
				 "results":[
				  {"bedZoneId":%d,"quantity":10,"attributeSourceOrchidGroupId":1,"purpose":"NORMAL"},
				  {"bedZoneId":%d,"quantity":20,"attributeSourceOrchidGroupId":2,"purpose":"NORMAL"}]}
				""".formatted(zoneId, zoneId);
	}

	private String resultDetails() {
		return """
				{"sourceInputQuantities":{"1":10,"2":20},"lossQuantity":0,"increaseQuantity":0,
				 "results":[
				  {"orchidGroupId":11,"quantity":10,"purpose":"NORMAL"},
				  {"orchidGroupId":12,"quantity":20,"purpose":"NORMAL"}]}
				""";
	}

}
