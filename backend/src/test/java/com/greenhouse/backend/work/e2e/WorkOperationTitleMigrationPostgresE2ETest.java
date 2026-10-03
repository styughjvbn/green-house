package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class WorkOperationTitleMigrationPostgresE2ETest extends WorkE2ETestBase {

  @Test
  void normalizesManagedHistoryTitlesAndPreservesGenericTitles() {
    String database = "work_operation_titles_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
    var dataSource =
        new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(dataSource).target("31").load().migrate();
      var jdbc = new JdbcTemplate(dataSource);
      seedOperations(jdbc);

      var upgrade = Flyway.configure().dataSource(dataSource).target("32").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);

      assertThat(
              jdbc.queryForList(
                  """
					SELECT operation.id, operation.title
					FROM work_operations operation
					WHERE operation.id BETWEEN 900 AND 910
					ORDER BY operation.id
					"""))
          .extracting(row -> row.get("id"), row -> row.get("title"))
          .containsExactly(
              tuple(900L, "청금 · 입고"),
              tuple(901L, "청금 · 포트 식재"),
              tuple(902L, "청금 · 자리 이동"),
              tuple(903L, "청금 · 분갈이"),
              tuple(904L, "청금 · 분주"),
              tuple(905L, "청금 · 합식"),
              tuple(906L, "청금 · 폐기"),
              tuple(907L, "청금 · 현장 상태 조정"),
              tuple(908L, "청금 · 자리 이동 후 폐기"),
              tuple(909L, "청금 · 분갈이 보정"),
              tuple(910L, "농장 전체 방제"));
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  private void seedOperations(JdbcTemplate jdbc) {
    jdbc.execute("ALTER TABLE orchid_groups DISABLE TRIGGER USER");
    jdbc.execute(
        """
				INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
				VALUES (9201, 'VAR-TITLE', '속', '청금', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
				INSERT INTO orchid_groups (
				    id, bed_zone_id, variety_id, genus, variety_name, quantity, reserved_quantity,
				    sort_order, status, pot_size_code, version, state_revision, created_at, updated_at
				) VALUES (
				    9201, (SELECT min(id) FROM bed_zones), 9201, '속', '청금', 10, 0,
				    1, '정상', 'POT_3', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
				);
				""");
    jdbc.execute("ALTER TABLE orchid_groups ENABLE TRIGGER USER");
    jdbc.execute(
        """
				INSERT INTO work_operations (
				    id, work_type_id, title, status, planned_start_date, planned_end_date,
				    source_scope_type, source_condition_snapshot, target_snapshot_at, details,
				    worker, version, created_at, updated_at, parent_operation_id, relation_type
				)
				SELECT seeded.id, work_type.id, seeded.title, 'COMPLETED', DATE '2026-09-01', DATE '2026-09-01',
				       seeded.scope_type, '{}'::jsonb, CURRENT_TIMESTAMP, '{}'::jsonb,
				       '작업자', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, seeded.parent_id, seeded.relation_type
				FROM (VALUES
				    (900::BIGINT, 'INBOUND', '입고 - 청금', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (901::BIGINT, 'POTTING', '포트 작업 작업', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (902::BIGINT, 'MOVEMENT', '위치 이동', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (903::BIGINT, 'REPOT', '사용자가 바꾼 과거 제목', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (904::BIGINT, 'DIVIDE', '분주 작업', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (905::BIGINT, 'MERGE', '합식 작업', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (906::BIGINT, 'DISCARD', '폐기 작업', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (907::BIGINT, 'RECONCILIATION', '현장 상태 동기화', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR),
				    (908::BIGINT, 'DISCARD', '이동 후 잔여 난 폐기', 'MANUAL_SELECTION', 902::BIGINT, 'MOVEMENT_DISCARD'),
				    (909::BIGINT, 'CORRECTION', '결과 보정', 'NONE', NULL::BIGINT, NULL::VARCHAR),
				    (910::BIGINT, 'PESTICIDE', '농장 전체 방제', 'MANUAL_SELECTION', NULL::BIGINT, NULL::VARCHAR)
				) AS seeded(id, code, title, scope_type, parent_id, relation_type)
				JOIN work_types work_type ON work_type.code = seeded.code;

				INSERT INTO work_operation_targets (
				    id, work_operation_id, orchid_group_id, target_reference_type, inclusion_source,
				    included_at, variety_id_snapshot, variety_name_snapshot, quantity_snapshot,
				    location_snapshot, created_at
				)
				SELECT operation.id, operation.id, 9201, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP,
				       9201, '청금', 10, '{}'::jsonb, CURRENT_TIMESTAMP
				FROM work_operations operation
				WHERE operation.id BETWEEN 900 AND 910
				  AND operation.id <> 909;

				INSERT INTO work_operation_corrections (
				    original_work_operation_id, correction_work_operation_id, reason, created_at
				) VALUES (903, 909, '수량 정정', CURRENT_TIMESTAMP);
				""");
  }
}
