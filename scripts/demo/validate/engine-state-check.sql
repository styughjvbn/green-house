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
END
$block$;
