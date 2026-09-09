DO $block$
DECLARE
  target record;
  match_count bigint;
  pattern constant text :=
    '([[:alnum:]._%+-]+@[[:alnum:].-]+\.[A-Za-z]{2,})' ||
    '|((https?|ftp)://|www\.)' ||
    '|(\m01[016789][ -]?[0-9]{3,4}[ -]?[0-9]{4}\M)' ||
    '|(\m[0-9]{3}-[0-9]{2}-[0-9]{5}\M)' ||
    '|(\m[0-9]{2,3}[가-힣][0-9]{4}\M)' ||
    '|(\m[0-9]{2,6}[- ][0-9]{2,6}[- ][0-9]{2,8}\M)';
BEGIN
  FOR target IN
    SELECT table_name, column_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name <> 'flyway_schema_history'
      AND data_type IN ('character varying', 'text', 'json', 'jsonb')
  LOOP
    EXECUTE format(
      'SELECT count(*) FROM public.%I WHERE replace(%I::text, ''010-0000-0000'', '''') ~* $1',
      target.table_name, target.column_name
    )
    INTO match_count
    USING pattern;

    IF match_count > 0 THEN
      RAISE EXCEPTION 'Sensitive pattern remains in %.% (% rows)',
        target.table_name, target.column_name, match_count;
    END IF;
  END LOOP;

  IF EXISTS (
    SELECT 1 FROM business_partners
    WHERE name NOT LIKE '데모 소매 거래처 %'
      AND name NOT LIKE '데모 도매 거래처 %'
      AND name NOT LIKE '데모 경매장 %'
  ) THEN
    RAISE EXCEPTION 'Unsanitized business partner name remains';
  END IF;

  IF EXISTS (
    SELECT 1 FROM business_partners
    WHERE coalesce(phone, '010-0000-0000') <> '010-0000-0000'
       OR coalesce(address, '데모 주소') <> '데모 주소'
  ) THEN
    RAISE EXCEPTION 'Unsanitized partner contact data remains';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM (
      SELECT worker AS actor FROM auction_lot_status_history
      UNION ALL SELECT actor_id FROM audit_events
      UNION ALL SELECT confirmed_by FROM auction_settlements
      UNION ALL SELECT worker FROM inbound_records
      UNION ALL SELECT created_by FROM partner_payment_events
      UNION ALL SELECT worker FROM work_records
      UNION ALL SELECT created_by FROM orchid_group_collections
      UNION ALL SELECT created_by FROM orchid_group_collection_members
      UNION ALL SELECT worker FROM work_operations
      UNION ALL SELECT worker FROM work_target_executions
      UNION ALL SELECT worker FROM work_applied_effects
    ) actors
    WHERE actor IS NOT NULL AND actor !~ '^작업자 [0-9]{3}$'
  ) THEN
    RAISE EXCEPTION 'Unsanitized actor name remains';
  END IF;

  IF EXISTS (
    SELECT 1 FROM partner_payment_events
    WHERE external_uid IS NOT NULL
       OR description IS NOT NULL
       OR memo IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'Raw payment identifiers or free text remain';
  END IF;

  IF EXISTS (
    SELECT 1 FROM audit_events
    WHERE session_id IS NOT NULL
       OR client_instance_id IS NOT NULL
       OR request_id IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'Raw audit request identifiers remain';
  END IF;

  IF EXISTS (
    SELECT 1 FROM orchid_group_mutations
    WHERE reason IS NOT NULL AND reason <> '데모 전환 이력'
  ) THEN
    RAISE EXCEPTION 'Unsanitized Engine mutation reason remains';
  END IF;
END
$block$;
