package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("work-e2e")
class MultiVarietyDiscardMigrationPostgresE2ETest extends WorkE2ETestBase {

  @Test
  void splitsDiscardTargetsEffectsAndMutationsByVariety() {
    String database = "multi_variety_discard_" + UUID.randomUUID().toString().replace("-", "");
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
      seedMultiVarietyDiscard(jdbc);

      var upgrade = Flyway.configure().dataSource(dataSource).target("31").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);

      assertThat(
              jdbc.queryForList(
                  """
					SELECT operation.title,
					       count(target.id) AS target_count,
					       count(DISTINCT target.variety_id_snapshot) AS variety_count
					FROM work_operations operation
					JOIN work_operation_targets target ON target.work_operation_id = operation.id
					WHERE operation.id = 100
					   OR operation.title LIKE '다품종 폐기 - %'
					GROUP BY operation.id, operation.title
					ORDER BY operation.title
					"""))
          .extracting(
              row -> row.get("title"),
              row -> row.get("target_count"),
              row -> row.get("variety_count"))
          .containsExactlyInAnyOrder(
              tuple("다품종 폐기 - 품종 A", 2L, 1L), tuple("다품종 폐기 - 품종 B", 1L, 1L));

      assertThat(
              jdbc.queryForObject(
                  """
					SELECT count(*)
					FROM work_applied_effects effect
					JOIN work_operation_targets target ON target.id = effect.work_operation_target_id
					WHERE effect.work_operation_id <> target.work_operation_id
					""",
                  Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  """
					SELECT count(*)
					FROM orchid_group_mutations mutation
					JOIN work_applied_effects effect ON effect.mutation_id = mutation.id
					WHERE mutation.source_reference_id <> effect.work_operation_id::TEXT
					   OR mutation.correlation_id <> effect.correlation_id
					""",
                  Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  """
					SELECT count(DISTINCT correlation_id)
					FROM work_applied_effects
					WHERE id IN (100, 101, 102)
					""",
                  Integer.class))
          .isEqualTo(2);
      assertThat(
              jdbc.queryForObject(
                  """
					SELECT count(*) FROM (
					    SELECT operation.id
					    FROM work_operations operation
					    JOIN work_types work_type
					      ON work_type.id = operation.work_type_id
					     AND work_type.code = 'DISCARD'
					    JOIN work_operation_targets target ON target.work_operation_id = operation.id
					    GROUP BY operation.id
					    HAVING count(DISTINCT COALESCE(
					        target.variety_id_snapshot::TEXT,
					        'name:' || target.variety_name_snapshot
					    )) > 1
					) candidates
					""",
                  Long.class))
          .isZero();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  @Test
  void lateStageFailureRollsBackTheEntireHistoricalContext() {
    String database = "history_rollback_" + UUID.randomUUID().toString().replace("-", "");
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
      seedMultiVarietyDiscard(jdbc);
      jdbc.update("UPDATE work_operations SET request_key='unsupported' WHERE id=100");
      var upgrade = Flyway.configure().dataSource(dataSource).target("31").load();
      assertThatThrownBy(upgrade::migrate)
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("unsupported operation metadata");
      assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("30");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='orchid_group_identity_migrations'",
                  Long.class))
          .isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operations", Long.class))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM work_operation_targets WHERE work_operation_id=100",
                  Long.class))
          .isEqualTo(3);
      assertThat(
              jdbc.queryForList(
                  "SELECT tgenabled::text FROM pg_trigger WHERE tgrelid='orchid_groups'::regclass AND tgname IN ('trg_orchid_group_write_fence','trg_orchid_group_ledger_entry')",
                  String.class))
          .containsExactlyInAnyOrder("O", "O");
      Flyway.configure().dataSource(dataSource).target("30").load().validate();
    } finally {
      admin.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  private void seedMultiVarietyDiscard(JdbcTemplate jdbc) {
    jdbc.execute("ALTER TABLE orchid_groups DISABLE TRIGGER USER");
    jdbc.execute(
        """
				INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
				VALUES
				    (9101, 'VAR-DISCARD-A', '속', '품종 A', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				    (9102, 'VAR-DISCARD-B', '속', '품종 B', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
				INSERT INTO orchid_groups (
				    id, bed_zone_id, variety_id, genus, variety_name, quantity, reserved_quantity,
				    sort_order, status, pot_size_code, version, state_revision, created_at, updated_at
				) VALUES
				    (1, (SELECT min(id) FROM bed_zones), 9101, '속', '품종 A', 0, 0, 1, '폐기', 'POT_3', 0, 1,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				    (2, (SELECT min(id) FROM bed_zones), 9101, '속', '품종 A', 0, 0, 2, '폐기', 'POT_3', 0, 1,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				    (3, (SELECT min(id) FROM bed_zones), 9102, '속', '품종 B', 0, 0, 3, '폐기', 'POT_3', 0, 1,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
				""");
    jdbc.execute("ALTER TABLE orchid_groups ENABLE TRIGGER USER");
    jdbc.execute(
        """
				INSERT INTO work_operations (
				    id, work_type_id, title, status, planned_start_date, planned_end_date,
				    actual_start_at, actual_end_at, source_scope_type, source_condition_snapshot,
				    target_snapshot_at, details, worker, version, created_at, updated_at
				) VALUES (
				    100, (SELECT id FROM work_types WHERE code = 'DISCARD'), '다품종 폐기', 'COMPLETED',
				    DATE '2026-07-22', DATE '2026-07-22', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
				    'MANUAL_SELECTION', '{"orchidGroupIds":[1,2,3]}'::jsonb, CURRENT_TIMESTAMP,
				    '{}'::jsonb, '작업자', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
				);
				INSERT INTO work_operation_targets (
				    id, work_operation_id, orchid_group_id, target_reference_type, inclusion_source,
				    included_at, variety_id_snapshot, variety_name_snapshot, quantity_snapshot,
				    location_snapshot, created_at
				) VALUES
				    (100, 100, 1, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9101, '품종 A', 10,
				     '{}'::jsonb, CURRENT_TIMESTAMP),
				    (101, 100, 2, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9101, '품종 A', 20,
				     '{}'::jsonb, CURRENT_TIMESTAMP),
				    (102, 100, 3, 'ORCHID_GROUP', 'MANUAL', CURRENT_TIMESTAMP, 9102, '품종 B', 30,
				     '{}'::jsonb, CURRENT_TIMESTAMP);
				INSERT INTO work_target_executions (
				    id, work_operation_target_id, status, result_details, processed_quantity,
				    version, created_at, updated_at
				) VALUES
				    (100, 100, 'COMPLETED', '{"discardedQuantity":10}'::jsonb, 10, 0,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				    (101, 101, 'COMPLETED', '{"discardedQuantity":20}'::jsonb, 20, 0,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
				    (102, 102, 'COMPLETED', '{"discardedQuantity":30}'::jsonb, 30, 0,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
				""");
    jdbc.execute(
        """
				INSERT INTO orchid_group_mutations (
				    id, mutation_type, source_domain, source_type, source_reference_id, source_operation_key,
				    correlation_id, command_fingerprint, occurred_at, recorded_at,
				    effective_business_date, schema_version
				) VALUES
				    (100, 'DISCARD', 'WORK', 'WORK_EFFECT', '100', 'TARGET:100',
				     '11111111-1111-3111-8111-111111111111', repeat('1', 64), CURRENT_TIMESTAMP,
				     CURRENT_TIMESTAMP, DATE '2026-07-22', 1),
				    (101, 'DISCARD', 'WORK', 'WORK_EFFECT', '100', 'TARGET:101',
				     '11111111-1111-3111-8111-111111111111', repeat('2', 64), CURRENT_TIMESTAMP,
				     CURRENT_TIMESTAMP, DATE '2026-07-22', 1),
				    (102, 'DISCARD', 'WORK', 'WORK_EFFECT', '100', 'TARGET:102',
				     '11111111-1111-3111-8111-111111111111', repeat('3', 64), CURRENT_TIMESTAMP,
				     CURRENT_TIMESTAMP, DATE '2026-07-22', 1);
				INSERT INTO orchid_group_mutation_entries (
				    id, mutation_id, orchid_group_id, entry_kind, role, state_revision_before,
				    state_revision_after, before_state, after_state
				) VALUES
				    (100, 100, 1, 'CHANGE', 'AFFECTED', 0, 1,
				     '{"quantity":10,"status":"정상"}'::jsonb, '{"quantity":0,"status":"폐기"}'::jsonb),
				    (101, 101, 2, 'CHANGE', 'AFFECTED', 0, 1,
				     '{"quantity":20,"status":"정상"}'::jsonb, '{"quantity":0,"status":"폐기"}'::jsonb),
				    (102, 102, 3, 'CHANGE', 'AFFECTED', 0, 1,
				     '{"quantity":30,"status":"정상"}'::jsonb, '{"quantity":0,"status":"폐기"}'::jsonb);
				INSERT INTO work_applied_effects (
				    id, work_operation_id, work_operation_target_id, effect_key, effect_kind,
				    handler_code, applied_at, command_details, result_details, created_at, updated_at,
				    mutation_id, correlation_id
				) VALUES
				    (100, 100, 100, 'TARGET:100', 'TARGET_COMPLETION', 'DISCARD', CURRENT_TIMESTAMP,
				     '{"discardQuantity":10}'::jsonb, '{"discardedQuantity":10}'::jsonb,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100, '11111111-1111-3111-8111-111111111111'),
				    (101, 100, 101, 'TARGET:101', 'TARGET_COMPLETION', 'DISCARD', CURRENT_TIMESTAMP,
				     '{"discardQuantity":20}'::jsonb, '{"discardedQuantity":20}'::jsonb,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 101, '11111111-1111-3111-8111-111111111111'),
				    (102, 100, 102, 'TARGET:102', 'TARGET_COMPLETION', 'DISCARD', CURRENT_TIMESTAMP,
				     '{"discardQuantity":30}'::jsonb, '{"discardedQuantity":30}'::jsonb,
				     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 102, '11111111-1111-3111-8111-111111111111');
				""");
  }
}
