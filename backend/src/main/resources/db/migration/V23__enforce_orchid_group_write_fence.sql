CREATE OR REPLACE FUNCTION enforce_orchid_group_write_fence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    active_cutover UUID;
    write_context TEXT;
BEGIN
    SELECT coverage.cutover_key
      INTO active_cutover
      FROM orchid_group_ledger_coverages coverage
     WHERE coverage.status = 'ACTIVE'
     LIMIT 1;

    IF active_cutover IS NULL THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'ACTIVE OrchidGroup ledger에서는 물리 삭제할 수 없습니다.'
            USING ERRCODE = '23514';
    END IF;

    write_context := current_setting('greenhouse.orchid_group_mutation', TRUE);
    IF write_context IS NULL OR write_context !~ '^MUTATION:[0-9]+$' THEN
        RAISE EXCEPTION 'OrchidGroup Mutation context가 없는 쓰기는 허용되지 않습니다.'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'INSERT' AND NEW.state_revision IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION 'ACTIVE ledger의 신규 OrchidGroup revision은 1이어야 합니다.'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' AND (
        OLD.state_revision IS NULL
        OR NEW.state_revision IS DISTINCT FROM OLD.state_revision + 1
    ) THEN
        RAISE EXCEPTION 'ACTIVE ledger의 OrchidGroup update는 revision을 정확히 1 증가시켜야 합니다.'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_orchid_group_write_fence
BEFORE INSERT OR UPDATE OR DELETE ON orchid_groups
FOR EACH ROW
EXECUTE FUNCTION enforce_orchid_group_write_fence();

CREATE OR REPLACE FUNCTION verify_orchid_group_ledger_entry()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM orchid_group_ledger_coverages coverage
         WHERE coverage.status = 'ACTIVE'
    ) AND NOT EXISTS (
        SELECT 1
          FROM orchid_group_mutation_entries entry
         WHERE entry.orchid_group_id = NEW.id
           AND entry.state_revision_after = NEW.state_revision
    ) THEN
        RAISE EXCEPTION 'OrchidGroup revision에 대응하는 MutationEntry가 없습니다.'
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_orchid_group_ledger_entry
AFTER INSERT OR UPDATE ON orchid_groups
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION verify_orchid_group_ledger_entry();
