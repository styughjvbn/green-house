DO $block$
DECLARE
  active_count bigint;
BEGIN
  SELECT count(*) INTO active_count
  FROM orchid_group_ledger_coverages
  WHERE status = 'ACTIVE'
    AND minimum_writer_version = '2.0.0'
    AND import_fingerprint ~ '^[0-9a-f]{64}$'
    AND baseline_fingerprint ~ '^[0-9a-f]{64}$';

  IF active_count <> 1 OR (SELECT count(*) FROM orchid_group_ledger_coverages) <> 1 THEN
    RAISE EXCEPTION 'Exactly one verified ACTIVE Engine coverage is required';
  END IF;

  IF EXISTS (SELECT 1 FROM orchid_groups WHERE state_revision IS NULL)
     OR EXISTS (
       SELECT 1
       FROM orchid_groups groups
       LEFT JOIN orchid_group_mutation_entries entry
         ON entry.orchid_group_id = groups.id
        AND entry.state_revision_after = groups.state_revision
       WHERE entry.id IS NULL OR entry.after_state IS NULL
     ) THEN
    RAISE EXCEPTION 'Current orchid group is missing its final Engine entry';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM orchid_group_mutations mutation
    LEFT JOIN orchid_group_mutation_entries entry ON entry.mutation_id = mutation.id
    WHERE entry.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Engine mutation without entry exists';
  END IF;

  IF EXISTS (
    SELECT 1 FROM (
      SELECT entry.*,
             lag(state_revision_after) OVER chain AS previous_revision,
             lag(after_state) OVER chain AS previous_after,
             row_number() OVER chain AS chain_position
      FROM orchid_group_mutation_entries entry
      WINDOW chain AS (PARTITION BY orchid_group_id ORDER BY state_revision_after)
    ) chain
    WHERE (chain_position = 1 AND NOT (
              (entry_kind = 'BASELINE' AND state_revision_before IS NULL AND state_revision_after = 0)
           OR (entry_kind = 'CREATE' AND state_revision_before IS NULL AND state_revision_after = 1)))
       OR (chain_position > 1 AND (
              state_revision_before IS DISTINCT FROM previous_revision
           OR state_revision_after IS DISTINCT FROM previous_revision + 1
           OR before_state IS DISTINCT FROM previous_after))
  ) THEN
    RAISE EXCEPTION 'Engine revision or snapshot chain is discontinuous';
  END IF;

  IF EXISTS (
    SELECT 1 FROM audit_events event
    LEFT JOIN orchid_group_mutations mutation ON mutation.id=event.mutation_id
    WHERE event.mutation_id IS NOT NULL AND mutation.id IS NULL
  ) OR EXISTS (
    SELECT 1 FROM orchid_group_lineage lineage
    LEFT JOIN orchid_group_mutations mutation ON mutation.id=lineage.mutation_id
    WHERE lineage.mutation_id IS NOT NULL AND mutation.id IS NULL
  ) OR EXISTS (
    SELECT 1 FROM orchid_group_mutation_relations relation
    LEFT JOIN orchid_group_mutations source ON source.id=relation.mutation_id
    LEFT JOIN orchid_group_mutations target ON target.id=relation.related_mutation_id
    WHERE source.id IS NULL OR target.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Engine Audit, Lineage, or Mutation relation is broken';
  END IF;

  IF EXISTS (
    SELECT 1 FROM work_applied_effects
    WHERE (mutation_id IS NULL) <> (correlation_id IS NULL)
  ) OR EXISTS (
    SELECT 1 FROM sales_inventory_movements
    WHERE (mutation_id IS NULL) <> (correlation_id IS NULL)
  ) THEN
    RAISE EXCEPTION 'Incomplete Work or Sales Engine link exists';
  END IF;

  IF EXISTS (
    SELECT 1 FROM work_command_receipts
    WHERE (request_fingerprint IS NOT NULL AND request_fingerprint !~ '^[0-9a-f]{64}$')
       OR (result_operation_ids IS NOT NULL AND jsonb_typeof(result_operation_ids) <> 'array')
  ) THEN
    RAISE EXCEPTION 'Invalid Work command receipt exists';
  END IF;

  IF to_regprocedure('enforce_orchid_group_write_fence()') IS NULL
     OR to_regprocedure('verify_orchid_group_ledger_entry()') IS NULL
     OR NOT EXISTS (
       SELECT 1 FROM pg_trigger
       WHERE tgrelid = 'orchid_groups'::regclass
         AND tgname = 'trg_orchid_group_write_fence'
         AND NOT tgisinternal
     )
     OR NOT EXISTS (
       SELECT 1 FROM pg_trigger
       WHERE tgrelid = 'orchid_groups'::regclass
         AND tgname = 'trg_orchid_group_ledger_entry'
         AND NOT tgisinternal
     ) THEN
    RAISE EXCEPTION 'Engine write-fence function or trigger is missing';
  END IF;

  IF pg_get_serial_sequence('orchid_group_mutations', 'id') IS NULL
     OR pg_get_serial_sequence('orchid_group_mutation_entries', 'id') IS NULL
     OR pg_get_serial_sequence('orchid_group_mutation_relations', 'id') IS NULL
     OR pg_get_serial_sequence('orchid_group_ledger_coverages', 'id') IS NULL THEN
    RAISE EXCEPTION 'Engine-owned sequence is missing';
  END IF;

  IF to_regclass('demo_internal.sanitization_marker') IS NULL THEN
    RAISE EXCEPTION 'Sanitization marker is missing';
  END IF;
END
$block$;
