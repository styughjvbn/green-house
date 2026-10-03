package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class MovementDiscardHistoryMigrationPostgresE2ETest extends WorkE2ETestBase {

  @Test
  void convertsPreDiscardHistoryToMovementThenResidualDiscard() throws Exception {
    String database = "movement_discard_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE " + database);
    String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
    var dataSource =
        new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    try {
      Flyway.configure().dataSource(dataSource).target("30").load().migrate();
      var jdbc = new JdbcTemplate(dataSource);
      seedPreDiscardHistory(jdbc);
      activateLedger(jdbc);
      var original = jdbc.queryForMap("SELECT * FROM orchid_groups WHERE id = 1");

      var upgrade = Flyway.configure().dataSource(dataSource).target("31").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);

      assertThat(
              jdbc.queryForList(
                  """
					SELECT operation.id, type.code,
					       entry.state_revision_before AS before_revision,
					       entry.state_revision_after AS after_revision,
					       (entry.before_state ->> 'quantity')::INTEGER AS before_quantity,
					       (entry.after_state ->> 'quantity')::INTEGER AS after_quantity,
					       entry.after_state ->> 'status' AS after_status
					FROM work_operations operation
					JOIN work_types type ON type.id = operation.work_type_id
					JOIN work_applied_effects effect ON effect.work_operation_id = operation.id
					JOIN orchid_group_mutation_entries entry ON entry.mutation_id = effect.mutation_id
					ORDER BY entry.state_revision_after
					"""))
          .containsExactly(
              Map.of(
                  "id",
                  100L,
                  "code",
                  "MOVEMENT",
                  "before_revision",
                  0L,
                  "after_revision",
                  1L,
                  "before_quantity",
                  10,
                  "after_quantity",
                  4,
                  "after_status",
                  "정상"),
              Map.of(
                  "id",
                  101L,
                  "code",
                  "DISCARD",
                  "before_revision",
                  1L,
                  "after_revision",
                  2L,
                  "before_quantity",
                  4,
                  "after_quantity",
                  0,
                  "after_status",
                  "폐기"));
      assertThat(
              jdbc.queryForObject("SELECT title FROM work_operations WHERE id = 101", String.class))
          .isEqualTo("자리 이동 작업 - 이동 후 잔여 난 폐기");
      assertThat(
              jdbc.queryForObject(
                  "SELECT details FROM work_operations WHERE id = 101", String.class))
          .contains("LEGACY_RECORDED_SOURCE_ALLOCATION")
          .contains("\"totalDiscardQuantity\": 4")
          .doesNotContain("movementOperationId", "relation");
      assertThat(
              jdbc.queryForObject(
                  """
					SELECT quantity_snapshot FROM work_operation_targets
					WHERE work_operation_id = 101
					""",
                  Integer.class))
          .isEqualTo(4);
      assertThat(jdbc.queryForMap("SELECT * FROM orchid_groups WHERE id = 1"))
          .containsEntry("status", "폐기")
          .containsAllEntriesOf(withoutStatus(original));
      assertThat(
              jdbc.queryForObject(
                  """
					SELECT g.status = e.after_state ->> 'status'
					  AND g.quantity = (e.after_state ->> 'quantity')::INTEGER
					  AND g.state_revision = e.state_revision_after
					FROM orchid_groups g JOIN orchid_group_mutation_entries e
					  ON e.orchid_group_id = g.id AND e.state_revision_after = g.state_revision
					WHERE g.id = 1
					""",
                  Boolean.class))
          .isTrue();
      assertLedgerTriggersEnabled(jdbc);
      assertThat(upgrade.migrate().migrationsExecuted).isZero();

      assertThat(
              jdbc.queryForObject(
                  """
					SELECT processed_quantity FROM work_target_executions
					WHERE work_operation_target_id = 101
					""",
                  Integer.class))
          .isEqualTo(4);
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  @Test
  void preservesPartialRemainingGroupAndLaterCurrentState() {
    for (boolean later : new boolean[] {false, true}) {
      String database = "movement_status_" + UUID.randomUUID().toString().replace("-", "");
      var admin =
          new JdbcTemplate(
              new DriverManagerDataSource(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
      admin.execute("CREATE DATABASE " + database);
      var dataSource =
          new DriverManagerDataSource(
              POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
              POSTGRES.getUsername(),
              POSTGRES.getPassword());
      try {
        Flyway.configure().dataSource(dataSource).target("30").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        seedPreDiscardHistory(jdbc);
        if (later) {
          jdbc.execute(
              """
									UPDATE orchid_groups SET status = '생성 취소', state_revision = 3 WHERE id = 1;
									INSERT INTO orchid_group_mutations (
									    id, mutation_type, source_domain, source_type, source_reference_id, source_operation_key,
									    correlation_id, command_fingerprint, occurred_at, recorded_at, effective_business_date, schema_version
									) VALUES (102, 'CANCEL_CREATION', 'FARM', 'TEST', '1', 'later',
									    '00000000-0000-0000-0000-000000000102', repeat('2', 64), CURRENT_TIMESTAMP,
									    CURRENT_TIMESTAMP, DATE '2026-08-02', 1);
									INSERT INTO orchid_group_mutation_entries (
									    id, mutation_id, orchid_group_id, entry_kind, role, state_revision_before,
									    state_revision_after, before_state, after_state
									) VALUES (102, 102, 1, 'CHANGE', 'AFFECTED', 2, 3,
									    '{"quantity":0,"status":"종료"}', '{"quantity":0,"status":"생성 취소"}');
									""");
        } else {
          jdbc.execute(
              """
							UPDATE orchid_groups SET quantity = 2, status = '주의' WHERE id = 1;
							UPDATE orchid_group_mutation_entries
							SET after_state = '{"quantity":2,"status":"주의"}' WHERE id = 101;
							""");
        }
        activateLedger(jdbc);
        var original = jdbc.queryForMap("SELECT * FROM orchid_groups WHERE id = 1");
        Flyway.configure().dataSource(dataSource).target("31").load().migrate();
        assertThat(jdbc.queryForMap("SELECT * FROM orchid_groups WHERE id = 1"))
            .isEqualTo(original);
        assertThat(
                jdbc.queryForObject(
                    """
						SELECT count(*) FROM orchid_group_mutation_entries previous
						JOIN orchid_group_mutation_entries following
						  ON following.orchid_group_id = previous.orchid_group_id
						 AND following.state_revision_before = previous.state_revision_after
						WHERE previous.orchid_group_id = 1
						  AND previous.after_state IS DISTINCT FROM following.before_state
						""",
                    Long.class))
            .isZero();
        if (later) {
          assertThat(
                  jdbc.queryForMap(
                      """
							SELECT state_revision_before, state_revision_after,
							       before_state ->> 'status' AS before_status,
							       after_state::text AS after_state
							FROM orchid_group_mutation_entries WHERE id = 102
							"""))
              .containsExactlyInAnyOrderEntriesOf(
                  Map.of(
                      "state_revision_before",
                      2L,
                      "state_revision_after",
                      3L,
                      "before_status",
                      "폐기",
                      "after_state",
                      "{\"status\": \"생성 취소\", \"quantity\": 0}"));
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM orchid_group_mutation_entries", Long.class))
              .isEqualTo(3);
        }
        assertLedgerTriggersEnabled(jdbc);
      } finally {
        admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
      }
    }
  }

  private Map<String, Object> withoutStatus(Map<String, Object> original) {
    var preserved = new HashMap<>(original);
    preserved.remove("status");
    return preserved;
  }

  private void activateLedger(JdbcTemplate jdbc) {
    jdbc.execute(
        """
				INSERT INTO orchid_group_ledger_coverages (
				    cutover_key, status, engine_schema_version, snapshot_schema_version,
				    effective_business_date, minimum_writer_version
				) VALUES ('00000000-0000-0000-0000-000000000001', 'ACTIVE', 1, 1, DATE '2026-08-01', '1.0.0');
				""");
  }

  private void assertLedgerTriggersEnabled(JdbcTemplate jdbc) {
    assertThat(
            jdbc.queryForList(
                """
				SELECT tgenabled::text FROM pg_trigger
				WHERE tgrelid = 'orchid_groups'::regclass
				  AND tgname IN ('trg_orchid_group_write_fence', 'trg_orchid_group_ledger_entry')
				""",
                String.class))
        .containsExactlyInAnyOrder("O", "O");
  }

  private void seedPreDiscardHistory(JdbcTemplate jdbc) {
    jdbc.execute("ALTER TABLE orchid_groups DISABLE TRIGGER USER");
    jdbc.execute(
        """
				INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
				VALUES (9001, 'VAR-TEST', '속', '품종', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
				INSERT INTO orchid_groups (
				    id, bed_zone_id, variety_id, genus, variety_name, quantity, reserved_quantity,
				    sort_order, status, pot_size_code, version, state_revision, created_at, updated_at
				) VALUES (
				    1, (SELECT min(id) FROM bed_zones), 9001, '속', '품종', 0, 0, 1, '종료', 'POT_3', 0, 2,
				    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
				);
				""");
    jdbc.execute("ALTER TABLE orchid_groups ENABLE TRIGGER USER");
    jdbc.execute(
        """
				INSERT INTO work_operations (
				    id, work_type_id, title, status, planned_start_date, planned_end_date,
				    actual_start_at, actual_end_at, source_scope_type, source_condition_snapshot,
				    target_snapshot_at, details, version, created_at, updated_at
				) VALUES
				    (100, (SELECT id FROM work_types WHERE code = 'MOVEMENT'), '자리 이동 작업', 'COMPLETED',
				     DATE '2026-08-01', DATE '2026-08-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
				     'MANUAL_SELECTION', '{}'::jsonb, CURRENT_TIMESTAMP, '{}'::jsonb, 0,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				    (101, (SELECT id FROM work_types WHERE code = 'DISCARD'), '자리 이동 작업 - 동시 폐기',
				     'COMPLETED', DATE '2026-08-01', DATE '2026-08-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
				     'MANUAL_SELECTION', '{}'::jsonb, CURRENT_TIMESTAMP,
				     '{"movementOperationId":100,"relation":"MOVEMENT_DISCARD"}'::jsonb, 0,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
				UPDATE work_operations
				SET parent_operation_id = 100, relation_type = 'MOVEMENT_DISCARD'
				WHERE id = 101;
				""");
    jdbc.execute(
        """
						INSERT INTO work_operation_targets (
						    id, work_operation_id, orchid_group_id, target_reference_type, inclusion_source,
						    included_at, variety_id_snapshot, variety_name_snapshot, quantity_snapshot,
						    location_snapshot, created_at
						) VALUES
						    (100, 100, 1, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9001, '품종', 10,
						     '{}'::jsonb, CURRENT_TIMESTAMP),
						    (101, 101, 1, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9001, '품종', 10,
						     '{}'::jsonb, CURRENT_TIMESTAMP);
						INSERT INTO work_target_executions (
						    id, work_operation_target_id, status, result_details, processed_quantity,
						    version, created_at, updated_at
						) VALUES
						    (100, 100, 'COMPLETED', '{"remainingQuantity":0,"discardWorkOperationId":101}'::jsonb,
						     10, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
						    (101, 101, 'COMPLETED',
						     '{"reason":"자리 이동 중 동시 폐기","beforeQuantity":10,"discardedQuantity":4,"remainingQuantity":6,"status":"정상"}'::jsonb,
						     10, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
						""");
    jdbc.execute(
        """
				INSERT INTO orchid_group_mutations (
				    id, mutation_type, source_domain, source_type, source_reference_id, source_operation_key,
				    correlation_id, command_fingerprint, occurred_at, recorded_at,
				    effective_business_date, schema_version
				) VALUES
				    (100, 'DISCARD', 'WORK', 'WORK_EFFECT', '101', 'TARGET:101',
				     '00000000-0000-0000-0000-000000000100', repeat('0', 64), CURRENT_TIMESTAMP,
				     CURRENT_TIMESTAMP, DATE '2026-08-01', 1),
				    (101, 'TRANSFORM', 'WORK', 'WORK_EFFECT', '100', 'EXECUTION:test',
				     '00000000-0000-0000-0000-000000000101', repeat('1', 64), CURRENT_TIMESTAMP,
				     CURRENT_TIMESTAMP, DATE '2026-08-01', 1);
				INSERT INTO orchid_group_mutation_entries (
				    id, mutation_id, orchid_group_id, entry_kind, role, state_revision_before,
				    state_revision_after, before_state, after_state
				) VALUES
				    (100, 100, 1, 'CHANGE', 'AFFECTED', 0, 1,
				     '{"quantity":10,"status":"정상"}'::jsonb,
				     '{"quantity":6,"status":"정상"}'::jsonb),
				    (101, 101, 1, 'CHANGE', 'SOURCE', 1, 2,
				     '{"quantity":6,"status":"정상"}'::jsonb,
				     '{"quantity":0,"status":"종료"}'::jsonb);
				""");
    jdbc.execute(
        """
						INSERT INTO work_applied_effects (
						    id, work_operation_id, work_operation_target_id, effect_key, effect_kind,
						    handler_code, applied_at, command_details, result_details, created_at, updated_at,
						    mutation_id
						) VALUES
						    (100, 101, 101, 'TARGET:101', 'TARGET_COMPLETION', 'DISCARD', CURRENT_TIMESTAMP,
						     '{"reason":"자리 이동 중 동시 폐기","discardQuantity":4}'::jsonb,
						     '{"reason":"자리 이동 중 동시 폐기","beforeQuantity":10,"discardedQuantity":4,"remainingQuantity":6,"status":"정상"}'::jsonb,
						     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100),
						    (101, 100, NULL, 'EXECUTION:test', 'TARGET_COMPLETION', 'MOVEMENT', CURRENT_TIMESTAMP,
						     '{"sources":[{"sourceOrchidGroupId":1,"inputQuantity":10}]}'::jsonb,
						     '{"sourceInputQuantities":{"1":10},"lossQuantity":4,"remainingQuantity":0}'::jsonb,
						     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 101);
						""");
  }
}
