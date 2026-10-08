-- Preserve actual payment links; derived settlement amounts are not evidence.
-- Missing or already-owned references require explicit review, never an inferred rematch.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM partner_payment_events e LEFT JOIN auction_settlements s ON s.id=e.target_id
    WHERE e.target_type='AUCTION_SETTLEMENT' AND s.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Auction payment target is missing; review actual payment references before migration';
  END IF;
  IF EXISTS (
    SELECT 1 FROM auction_settlement_lines l JOIN auction_proceeds_results r ON r.auction_result_line_id=l.auction_result_line_id
    WHERE EXISTS (SELECT 1 FROM partner_payment_events e WHERE e.target_type='AUCTION_SETTLEMENT' AND e.target_id=l.settlement_id)
  ) THEN
    RAISE EXCEPTION 'Auction payment results already belong to a proceeds target; review references before migration';
  END IF;
END $$;

CREATE TABLE payment_target_aliases (
  original_target_type VARCHAR(255) NOT NULL,
  original_target_id BIGINT NOT NULL,
  target_type VARCHAR(255) NOT NULL,
  target_id BIGINT NOT NULL,
  PRIMARY KEY (original_target_type, original_target_id),
  UNIQUE (target_type, target_id),
  CHECK (original_target_type='AUCTION_SETTLEMENT' AND target_type='AUCTION_PROCEEDS'),
  CHECK (original_target_id > 0 AND target_id > 0)
);

INSERT INTO payment_target_aliases
SELECT 'AUCTION_SETTLEMENT', s.id, 'AUCTION_PROCEEDS', nextval('auction_proceeds_id_seq')
FROM auction_settlements s
WHERE EXISTS (SELECT 1 FROM partner_payment_events e WHERE e.target_type='AUCTION_SETTLEMENT' AND e.target_id=s.id)
ORDER BY s.id;

-- This is an unresolved reference for existing cash, not a copied settlement history.
-- No supplied source, gross/net, matching confirmation, actor or old summary is fabricated.
INSERT INTO auction_proceeds (id, auction_house_id, created_at, updated_at)
SELECT a.target_id, s.auction_house_id, CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
FROM payment_target_aliases a JOIN auction_settlements s ON s.id=a.original_target_id;

INSERT INTO auction_proceeds_results (auction_result_line_id, auction_proceeds_id)
SELECT l.auction_result_line_id, a.target_id
FROM auction_settlement_lines l JOIN payment_target_aliases a ON a.original_target_id=l.settlement_id;

-- IDs, received/matched parent links, dates, amounts, payloads and external UIDs remain exact.
UPDATE partner_payment_events e
SET target_type=a.target_type, target_id=a.target_id
FROM payment_target_aliases a
WHERE e.target_type=a.original_target_type AND e.target_id=a.original_target_id;
