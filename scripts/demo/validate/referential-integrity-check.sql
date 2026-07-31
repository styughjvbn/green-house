DO $block$
DECLARE
  relation record;
  orphan_count bigint;
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE connamespace = 'public'::regnamespace
      AND contype IN ('f', 'c')
      AND NOT convalidated
  ) THEN
    RAISE EXCEPTION 'Unvalidated foreign-key or check constraint exists';
  END IF;

  FOR relation IN
    SELECT child.relname AS child_table,
           child_column.attname AS child_column,
           parent.relname AS parent_table,
           parent_column.attname AS parent_column
    FROM pg_constraint constraint_row
    JOIN pg_class child ON child.oid = constraint_row.conrelid
    JOIN pg_class parent ON parent.oid = constraint_row.confrelid
    JOIN pg_attribute child_column
      ON child_column.attrelid = child.oid
     AND child_column.attnum = constraint_row.conkey[1]
    JOIN pg_attribute parent_column
      ON parent_column.attrelid = parent.oid
     AND parent_column.attnum = constraint_row.confkey[1]
    WHERE constraint_row.contype = 'f'
      AND constraint_row.connamespace = 'public'::regnamespace
      AND array_length(constraint_row.conkey, 1) = 1
  LOOP
    EXECUTE format(
      'SELECT count(*) FROM public.%I child LEFT JOIN public.%I parent ON child.%I = parent.%I WHERE child.%I IS NOT NULL AND parent.%I IS NULL',
      relation.child_table, relation.parent_table,
      relation.child_column, relation.parent_column,
      relation.child_column, relation.parent_column
    )
    INTO orphan_count;

    IF orphan_count > 0 THEN
      RAISE EXCEPTION 'Foreign-key orphan: %.% -> %.% (% rows)',
        relation.child_table, relation.child_column,
        relation.parent_table, relation.parent_column, orphan_count;
    END IF;
  END LOOP;
END
$block$;
