-- Decision history and actual arrivals are separate facts. No historical arrival or Farm effect
-- is inferred from the legacy lot totals, inferred returns or command response snapshots.
CREATE SEQUENCE auction_follow_up_decisions_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE auction_follow_up_decisions (
  id BIGINT PRIMARY KEY,
  lot_id BIGINT NOT NULL REFERENCES auction_shipment_lots(id) ON DELETE RESTRICT,
  method VARCHAR(32) NOT NULL CHECK (method IN ('REAUCTION', 'FARM_RETURN', 'AUCTION_DISPOSAL')),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  decided_at TIMESTAMP NOT NULL,
  worker VARCHAR(255),
  reason TEXT,
  CONSTRAINT uk_auction_follow_up_lot_decision UNIQUE (lot_id, id)
);
CREATE INDEX idx_auction_follow_up_lot ON auction_follow_up_decisions(lot_id, id);
CREATE SEQUENCE auction_return_arrivals_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE auction_return_arrivals (
  id BIGINT PRIMARY KEY,
  decision_id BIGINT NOT NULL,
  lot_id BIGINT NOT NULL REFERENCES auction_shipment_lots(id) ON DELETE RESTRICT,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  arrival_date DATE NOT NULL,
  created_at TIMESTAMP NOT NULL,
  worker VARCHAR(255),
  orchid_group_id BIGINT REFERENCES orchid_groups(id) ON DELETE RESTRICT,
  creation_mutation_id BIGINT UNIQUE REFERENCES orchid_group_mutations(id) ON DELETE RESTRICT,
  canceled_at TIMESTAMP,
  cancellation_mutation_id BIGINT UNIQUE REFERENCES orchid_group_mutations(id) ON DELETE RESTRICT,
  cancellation_reason TEXT,
  CONSTRAINT fk_auction_arrival_decision FOREIGN KEY (lot_id, decision_id)
    REFERENCES auction_follow_up_decisions(lot_id, id) ON DELETE RESTRICT,
  CONSTRAINT ck_auction_arrival_creation_link CHECK (
    (orchid_group_id IS NULL AND creation_mutation_id IS NULL)
    OR (orchid_group_id IS NOT NULL AND creation_mutation_id IS NOT NULL)),
  CONSTRAINT ck_auction_arrival_cancellation_link CHECK (
    (canceled_at IS NULL AND cancellation_mutation_id IS NULL AND cancellation_reason IS NULL)
    OR (canceled_at IS NOT NULL AND cancellation_mutation_id IS NOT NULL
        AND creation_mutation_id IS NOT NULL AND cancellation_reason IS NOT NULL
        AND length(trim(cancellation_reason)) > 0))
);
CREATE INDEX idx_auction_arrival_lot ON auction_return_arrivals(lot_id, id);
