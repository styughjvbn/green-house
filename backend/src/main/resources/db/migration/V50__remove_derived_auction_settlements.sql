-- Retire derived tables only after converting all remaining actual cash references.
-- V47 aliases and confirmed proceeds are preserved; no derived amounts are copied.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM partner_payment_events event
      LEFT JOIN auction_settlements settlement ON settlement.id=event.target_id
      WHERE event.target_type='AUCTION_SETTLEMENT' AND settlement.id IS NULL) THEN
    RAISE EXCEPTION 'Auction payment target is missing; review actual payment references before removal';
  END IF;
  IF EXISTS (SELECT 1 FROM payment_target_aliases alias
      LEFT JOIN auction_proceeds proceeds ON proceeds.id=alias.target_id
      WHERE alias.original_target_type='AUCTION_SETTLEMENT' AND proceeds.id IS NULL) THEN
    RAISE EXCEPTION 'Canonical auction payment target is missing; review aliases before removal';
  END IF;
  IF EXISTS (SELECT 1 FROM auction_settlement_lines line
      JOIN auction_proceeds_results reference ON reference.auction_result_line_id=line.auction_result_line_id
      WHERE EXISTS (SELECT 1 FROM partner_payment_events event
          WHERE event.target_type='AUCTION_SETTLEMENT' AND event.target_id=line.settlement_id)
        AND NOT EXISTS (SELECT 1 FROM payment_target_aliases alias
          WHERE alias.original_target_type='AUCTION_SETTLEMENT' AND alias.original_target_id=line.settlement_id)) THEN
    RAISE EXCEPTION 'Auction payment results already belong to a proceeds target; review references before removal';
  END IF;
END $$;

CREATE TEMP TABLE remaining_auction_payment_targets ON COMMIT DROP AS
SELECT settlement.id AS original_id,nextval('auction_proceeds_id_seq') AS target_id
FROM auction_settlements settlement
WHERE EXISTS (SELECT 1 FROM partner_payment_events event
    WHERE event.target_type='AUCTION_SETTLEMENT' AND event.target_id=settlement.id)
  AND NOT EXISTS (SELECT 1 FROM payment_target_aliases alias
    WHERE alias.original_target_type='AUCTION_SETTLEMENT' AND alias.original_target_id=settlement.id)
ORDER BY settlement.id;

INSERT INTO payment_target_aliases
SELECT 'AUCTION_SETTLEMENT',original_id,'AUCTION_PROCEEDS',target_id FROM remaining_auction_payment_targets;
INSERT INTO auction_proceeds (id,auction_house_id,created_at,updated_at)
SELECT target.target_id,settlement.auction_house_id,CURRENT_TIMESTAMP AT TIME ZONE 'UTC',CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
FROM remaining_auction_payment_targets target JOIN auction_settlements settlement ON settlement.id=target.original_id;
INSERT INTO auction_proceeds_results (auction_result_line_id,auction_proceeds_id)
SELECT line.auction_result_line_id,target.target_id
FROM auction_settlement_lines line JOIN remaining_auction_payment_targets target ON target.original_id=line.settlement_id;
UPDATE partner_payment_events event SET target_type=alias.target_type,target_id=alias.target_id
FROM payment_target_aliases alias
WHERE event.target_type=alias.original_target_type AND event.target_id=alias.original_target_id;

DROP TABLE auction_settlement_lines;
DROP TABLE auction_settlements;
DROP SEQUENCE IF EXISTS auction_settlement_lines_id_seq;
DROP SEQUENCE IF EXISTS auction_settlements_id_seq;
