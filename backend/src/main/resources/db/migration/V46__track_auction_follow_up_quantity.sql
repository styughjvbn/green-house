ALTER TABLE auction_shipment_lots ADD COLUMN follow_up_method VARCHAR(32);
ALTER TABLE auction_shipment_lots ADD COLUMN disposed_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE auction_shipment_lots ADD COLUMN inferred_return_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE auction_shipment_lots ADD CONSTRAINT ck_auction_follow_up_method
  CHECK (follow_up_method IS NULL OR follow_up_method IN ('REAUCTION', 'FARM_RETURN', 'AUCTION_DISPOSAL'));
ALTER TABLE auction_shipment_lots ADD CONSTRAINT ck_auction_disposed_quantity CHECK (disposed_quantity >= 0);
ALTER TABLE auction_shipment_lots ADD CONSTRAINT ck_auction_inferred_quantity CHECK (inferred_return_quantity >= 0);
-- Legacy totals remain untouched. Known inferred returns are separated only when a new follow-up
-- decision explicitly replaces the inferred status; no arrival or Farm mutation is backfilled.
ALTER TABLE auction_command_receipts DROP CONSTRAINT ck_auction_command_type;
ALTER TABLE auction_command_receipts ADD CONSTRAINT ck_auction_command_type
  CHECK (command_type IN ('RESULT', 'RETURN', 'FOLLOW_UP', 'ARRIVAL', 'ARRIVAL_CANCEL'));
ALTER TABLE auction_command_receipts DROP CONSTRAINT ck_auction_command_response;
ALTER TABLE auction_command_receipts ADD CONSTRAINT ck_auction_command_response CHECK (
  jsonb_typeof(response_snapshot) = 'object' AND (
    (command_type IN ('RESULT', 'RETURN') AND response_snapshot ? 'id'
      AND response_snapshot->'id' = to_jsonb(lot_id))
    OR
    (command_type IN ('FOLLOW_UP', 'ARRIVAL', 'ARRIVAL_CANCEL')
      AND response_snapshot ? 'followUp'
      AND jsonb_typeof(response_snapshot->'followUp') = 'object'
      AND response_snapshot->'followUp' ? 'lotId'
      AND response_snapshot->'followUp'->'lotId' = to_jsonb(lot_id)
      AND (command_type = 'FOLLOW_UP' OR (
        response_snapshot ? 'arrival'
        AND jsonb_typeof(response_snapshot->'arrival') = 'object'
        AND response_snapshot->'arrival' ? 'lotId'
        AND response_snapshot->'arrival'->'lotId' = to_jsonb(lot_id))))
  )
);
