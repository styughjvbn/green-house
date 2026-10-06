package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Keeps persisted ledger schema checks after the one-time importer is removed. */
@Tag("work-e2e")
class OrchidGroupLedgerSchemaPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void preservesHistoricalV21ThroughV34AndAppliesCurrentSchemaWithoutTransitionTables() {
    flyway.validate();
    assertThat(flyway.info().pending()).isEmpty();
    var resolved =
        Arrays.stream(flyway.info().all())
            .filter(migration -> migration.getVersion() != null)
            .map(migration -> migration.getVersion().getVersion())
            .toList();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class))
        .containsExactlyElementsOf(resolved);
    assertThat(
            jdbcTemplate.queryForList(
                """
				SELECT version || ':' || description
				FROM flyway_schema_history
				WHERE success = TRUE
				  AND version::INTEGER BETWEEN 21 AND 34
				ORDER BY installed_rank
				""",
                String.class))
        .containsExactly(
            "21:add orchid group mutation engine",
            "22:enforce orchid group mutation write fence",
            "23:normalize legacy orchid group pot sizes",
            "24:allocate farm reference codes",
            "25:add work command receipts",
            "26:align work effect idempotency",
            "27:repair orchid group audit provenance",
            "28:add work void and reconciliation",
            "29:link movement discard operations",
            "30:normalize inbound and work receipts",
            "31:normalize historical movement and discard",
            "32:normalize work operation metadata",
            "33:work correction audit events",
            "34:orchid stock count audits");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_types WHERE code = 'MULTI_CREATE'", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'inbound_records'
				  AND column_name IN (
				    'bottle_count', 'actual_quantity', 'potting_date', 'pot_size', 'age_year',
				    'growth_stage', 'placement_type', 'tray_count', 'bed_zone_id', 'created_orchid_group_id'
				  )
				""",
                Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'work_operations'
				  AND column_name IN ('parent_operation_id', 'relation_type')
				""",
                Long.class))
        .isEqualTo(2L);

    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'audit_events'
				  AND column_name = 'mutation_id'
				""",
                Long.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM pg_constraint
				WHERE conrelid = 'audit_events'::regclass
				  AND conname = 'fk_audit_events_orchid_group_mutation'
				""",
                Long.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM pg_indexes
				WHERE schemaname = 'public'
				  AND tablename = 'audit_events'
				  AND indexname = 'idx_audit_events_mutation'
				  AND indexdef LIKE '%WHERE (mutation_id IS NOT NULL)%'
				""",
                Long.class))
        .isOne();

    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name IN (
				      'orchid_group_history_migration_runs',
				      'orchid_group_shadow_comparisons'
				  )
				""",
                Long.class))
        .isZero();

    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'orchid_group_mutation_entries'
				  AND column_name = 'migration_run_id'
				""",
                Long.class))
        .isZero();

    assertThat(
            jdbcTemplate.queryForObject(
                """
				SELECT COUNT(*)
				FROM information_schema.table_constraints constraint_info
				JOIN information_schema.key_column_usage column_info
				  ON column_info.constraint_catalog = constraint_info.constraint_catalog
				 AND column_info.constraint_schema = constraint_info.constraint_schema
				 AND column_info.constraint_name = constraint_info.constraint_name
				WHERE constraint_info.table_schema = 'public'
				  AND constraint_info.table_name = 'orchid_group_mutation_entries'
				  AND constraint_info.constraint_type = 'FOREIGN KEY'
				  AND column_info.column_name = 'orchid_group_id'
				""",
                Long.class))
        .isZero();
  }
}
