package com.greenhouse.backend.work.e2e;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class WorkTestDataSeeder {

	private static final LocalDateTime FIXED_AT = LocalDateTime.of(2026, 7, 15, 0, 0);

	private final JdbcTemplate jdbcTemplate;

	WorkTestDataSeeder(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void reset() {
		jdbcTemplate.execute("""
				TRUNCATE TABLE
				  work_operation_corrections,
				  orchid_group_lineage,
				  work_effect_orchid_groups,
				  work_applied_effects,
				  work_target_executions,
				  work_operation_targets,
				  work_operations,
				  orchid_group_collection_members,
				  orchid_group_collections,
				  orchid_groups,
				  inbound_records,
				  varieties
				RESTART IDENTITY CASCADE
				""");
	}

	ContractScenario seedContractScenario() {
		Long varietyId = insertVariety("E2E-001", "E2E 난");
		Long bedZoneId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM bed_zones", Long.class);
		Long orchidGroupId = jdbcTemplate.queryForObject("""
				INSERT INTO orchid_groups (
				  created_at, updated_at, age_year, genus, placement_type, pot_size, pot_size_code,
				  quantity, sort_order, status, variety_name, bed_zone_id, split_placement_allowed,
				  variety_id, start_position, end_position, reserved_quantity
				) VALUES (?, ?, 2, '팔레놉시스', 'POT', '3.5치', 'POT_3_5',
				  100, 1, '정상', 'E2E 난', ?, FALSE, ?, 0, 5, 0)
				RETURNING id
				""", Long.class, timestamp(), timestamp(), bedZoneId, varietyId);
		return new ContractScenario(
				workTypeId("PESTICIDE"), workTypeId("REPOT"), bedZoneId, orchidGroupId);
	}

	BenchmarkScenario seedBenchmark(int operationCount, int targetsPerOperation) {
		if (operationCount < 1 || targetsPerOperation < 1) {
			throw new IllegalArgumentException("벤치마크 데이터 크기는 1 이상이어야 합니다.");
		}
		Long varietyId = insertVariety("BENCH-001", "벤치마크 난");
		Long bedZoneId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM bed_zones", Long.class);
		Long workTypeId = workTypeId("PESTICIDE");
		int targetCount = operationCount * targetsPerOperation;

		List<Long> orchidGroupIds = jdbcTemplate.queryForList("""
				INSERT INTO orchid_groups (
				  created_at, updated_at, age_year, genus, placement_type, pot_size, pot_size_code,
				  quantity, sort_order, status, variety_name, bed_zone_id, split_placement_allowed,
				  variety_id, start_position, end_position, reserved_quantity
				)
				SELECT ?, ?, 2, '팔레놉시스', 'POT', '3.5치', 'POT_3_5',
				       50, sequence_no, '정상', '벤치마크 난', ?, FALSE, ?,
				       (sequence_no - 1) % 20, ((sequence_no - 1) % 20) + 1, 0
				FROM generate_series(1, ?) AS sequence_no
				RETURNING id
				""", Long.class, timestamp(), timestamp(), bedZoneId, varietyId, targetCount);

		List<Long> operationIds = jdbcTemplate.queryForList("""
				INSERT INTO work_operations (
				  work_type_id, title, status, planned_start_date, source_scope_type,
				  target_snapshot_at, worker, version, created_at, updated_at
				)
				SELECT ?, '벤치마크 작업 ' || sequence_no, 'PLANNED', DATE '2026-07-15',
				       'MANUAL_SELECTION', ?, '벤치마크', 0, ?, ?
				FROM generate_series(1, ?) AS sequence_no
				RETURNING id
				""", Long.class, workTypeId, timestamp(), timestamp(), timestamp(), operationCount);

		List<TargetSeed> targets = new ArrayList<>(targetCount);
		for (int index = 0; index < targetCount; index++) {
			targets.add(new TargetSeed(
					operationIds.get(index / targetsPerOperation), orchidGroupIds.get(index)));
		}
		batchInsertTargets(targets, varietyId);
		jdbcTemplate.update("""
				INSERT INTO work_target_executions (
				  work_operation_target_id, status, processed_quantity, version, created_at, updated_at
				)
				SELECT id, 'PENDING', 0, 0, ?, ? FROM work_operation_targets
				""", timestamp(), timestamp());
		return new BenchmarkScenario(
				operationCount, targetCount, operationIds.getFirst(), orchidGroupIds.getFirst());
	}

	private void batchInsertTargets(List<TargetSeed> targets, Long varietyId) {
		jdbcTemplate.batchUpdate("""
				INSERT INTO work_operation_targets (
				  work_operation_id, orchid_group_id, target_reference_type, inclusion_source,
				  included_at, variety_id_snapshot, variety_name_snapshot, age_year_snapshot,
				  pot_size_code_snapshot, pot_size_snapshot, quantity_snapshot, location_snapshot, created_at
				) VALUES (?, ?, 'ORCHID_GROUP', 'MANUAL_ADDITION', ?, ?, '벤치마크 난', 2,
				          'POT_3_5', '3.5치', 50, CAST(? AS jsonb), ?)
				""", new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement statement, int index) throws SQLException {
						TargetSeed target = targets.get(index);
						statement.setLong(1, target.operationId());
						statement.setLong(2, target.orchidGroupId());
						statement.setTimestamp(3, timestamp());
						statement.setLong(4, varietyId);
						statement.setString(5, "{\"houseNumber\":1,\"bedNumber\":1,\"bedZoneName\":\"좌측\"}");
						statement.setTimestamp(6, timestamp());
					}

					@Override
					public int getBatchSize() {
						return targets.size();
					}
				});
	}

	private Long insertVariety(String code, String name) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO varieties (
				  created_at, updated_at, code, genus, name, default_pot_size,
				  sale_enabled, is_active
				) VALUES (?, ?, ?, '팔레놉시스', ?, '3.5치', TRUE, TRUE)
				RETURNING id
				""", Long.class, timestamp(), timestamp(), code, name);
	}

	private Long workTypeId(String code) {
		return jdbcTemplate.queryForObject(
				"SELECT id FROM work_types WHERE code = ?", Long.class, code);
	}

	private Timestamp timestamp() {
		return Timestamp.valueOf(FIXED_AT);
	}

	record ContractScenario(Long pesticideWorkTypeId, Long repotWorkTypeId, Long bedZoneId, Long orchidGroupId) {
	}

	record BenchmarkScenario(
			int operationCount,
			int targetCount,
			Long firstOperationId,
			Long firstOrchidGroupId) {
	}

	private record TargetSeed(Long operationId, Long orchidGroupId) {
	}
}
