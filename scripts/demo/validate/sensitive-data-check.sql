DO $block$
DECLARE
  target record;
  match_count bigint;
  uuid_pattern constant text := '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
  identifier_pattern text;
  business_date_pattern constant text :=
    '"(workDate|beforeWorkDate|afterWorkDate|inboundDate|pottingDate|businessDate|' ||
    'effectiveBusinessDate|plannedStartDate|plannedEndDate|actualStartAt|actualEndAt|' ||
    'createdAt|updatedAt|recordedAt|appliedAt|canceledAt|voidedAt|occurredAt|' ||
    'targetSnapshotAt|shipmentDate|auctionDate|saleDate|paymentDate|arrivalDate|correctionDate|changeDate|expectedPaymentDate|decisionDate|decidedAt|confirmedAt|changedAt|allocationDate|countedDate|eventDate|joinedAt|latestAuctionDate|latestWorkDate|pottingDueDate|recentInboundDate|recentWorkDate|referenceDate|returnConfirmedDate|' ||
    'work_date|before_work_date|after_work_date|inbound_date|potting_date|business_date|' ||
    'effective_business_date|planned_start_date|planned_end_date|actual_start_at|actual_end_at|' ||
    'created_at|updated_at|recorded_at|applied_at|canceled_at|voided_at|occurred_at|' ||
    'target_snapshot_at|shipment_date|auction_date|sale_date|payment_date|arrival_date|correction_date|change_date|expected_payment_date|decision_date|decided_at|confirmed_at|changed_at|allocation_date|counted_date|event_date|joined_at|latest_auction_date|latest_work_date|potting_due_date|recent_inbound_date|recent_work_date|reference_date|return_confirmed_date)' ||
    '"[[:space:]]*:[[:space:]]*"[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])' ||
    '(T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?(Z|[+-][0-9]{2}:[0-9]{2})?)?"';
  pattern constant text :=
    '([[:alnum:]._%+-]+@[[:alnum:].-]+\.[A-Za-z]{2,})' ||
    '|((https?|ftp)://|www\.)' ||
    '|(\m01[016789][ -]?[0-9]{3,4}[ -]?[0-9]{4}\M)' ||
    '|(\m[0-9]{3}-[0-9]{2}-[0-9]{5}\M)' ||
    '|(\m[0-9]{2,3}[가-힣][0-9]{4}\M)' ||
    '|(\m[0-9]{2,6}[- ][0-9]{2,6}[- ][0-9]{2,8}\M)';
BEGIN
  FOR target IN
    SELECT table_name, column_name, data_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name <> 'flyway_schema_history'
      AND data_type IN ('character varying', 'text', 'json', 'jsonb')
  LOOP
    -- Opaque command identities may embed an original business date. Keep
    -- them stable. The legacy cancel-chain grammar is also a generated key,
    -- not a phone number; neither exemption applies to memo/reason/free text.
    identifier_pattern := CASE
      WHEN target.column_name IN (
        'source_reference_id', 'source_operation_key', 'request_key',
        'receipt_key', 'void_request_key', 'effect_key'
      ) THEN '^(BATCH_VOID:)?cancel-[0-9]+-chain(-[0-9]+)+$|' || uuid_pattern || '|\m[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])\M'
      ELSE 'a^'
    END;
    IF target.data_type IN ('json', 'jsonb') THEN
      -- A structural business date is not a phone/account number. Only remove
      -- known date fields; the same digits inside memo/free text remain checked.
      EXECUTE format(
        'SELECT count(*) FROM public.%I WHERE replace(regexp_replace(regexp_replace(%I::text, $2, '''', ''g''), $3, '''', ''gi''), ''010-0000-0000'', '''') ~* $1',
        target.table_name, target.column_name
      ) INTO match_count USING pattern, business_date_pattern,
        '"(correlationId|correlation_id|requestKey|request_key|executionKey|execution_key|effectKey|effect_key|idempotencyKey|idempotency_key)"[[:space:]]*:[[:space:]]*"' || uuid_pattern || '"';
    ELSE
      EXECUTE format(
        'SELECT count(*) FROM public.%I WHERE replace(regexp_replace(%I::text, $2, '''', ''gi''), ''010-0000-0000'', '''') ~* $1',
        target.table_name, target.column_name
      ) INTO match_count USING pattern, identifier_pattern;
    END IF;

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
      UNION ALL SELECT confirmed_by FROM auction_proceeds
      UNION ALL SELECT worker FROM auction_follow_up_decisions
      UNION ALL SELECT worker FROM auction_return_arrivals
      UNION ALL SELECT worker FROM direct_return_arrivals
      UNION ALL SELECT worker FROM inbound_records
      UNION ALL SELECT created_by FROM partner_payment_events
      UNION ALL SELECT worker FROM work_records
      UNION ALL SELECT created_by FROM orchid_group_collections
      UNION ALL SELECT created_by FROM orchid_group_collection_members
      UNION ALL SELECT worker FROM work_operations
      UNION ALL SELECT worker FROM work_target_executions
      UNION ALL SELECT worker FROM work_applied_effects
      UNION ALL SELECT worker FROM work_operation_corrections
      UNION ALL SELECT worker FROM orchid_stock_counts
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
    SELECT 1 FROM work_operations
    WHERE void_reason IS NOT NULL AND void_reason <> '데모 취소 사유'
  ) OR EXISTS (
    SELECT 1 FROM work_operation_corrections
    WHERE reason <> '데모 보정 사유' OR memo IS NOT NULL
  ) OR EXISTS (
    SELECT 1 FROM orchid_stock_counts
    WHERE reason <> '데모 실사 사유' OR memo IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'Unsanitized work cancellation, correction, or stock-count text remains';
  END IF;

  IF EXISTS (SELECT 1 FROM direct_sale_changes WHERE reason <> '데모 가격 조정/반품 사유')
     OR EXISTS (SELECT 1 FROM direct_return_arrivals
         WHERE cancellation_reason IS NOT NULL AND cancellation_reason <> '데모 도착 정정')
     OR EXISTS (SELECT 1 FROM auction_return_arrivals
         WHERE cancellation_reason IS NOT NULL AND cancellation_reason <> '데모 도착 정정')
     OR EXISTS (SELECT 1 FROM auction_follow_up_decisions
         WHERE reason IS NOT NULL AND reason <> '데모 처리 사유') THEN
    RAISE EXCEPTION 'Unsanitized sales adjustment or return text remains';
  END IF;

  IF EXISTS (SELECT 1 FROM partner_settlement_settings
      WHERE shipper_code IS NOT NULL OR shipper_name IS NOT NULL) THEN
    RAISE EXCEPTION 'Unsanitized auction shipper identifiers remain';
  END IF;

  IF EXISTS (
    SELECT 1 FROM orchid_group_mutations
    WHERE reason IS NOT NULL AND reason <> '데모 이력'
  ) THEN
    RAISE EXCEPTION 'Unsanitized Engine mutation reason remains';
  END IF;
END
$block$;
