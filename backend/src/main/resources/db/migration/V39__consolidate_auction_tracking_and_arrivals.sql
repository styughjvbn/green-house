ALTER TABLE auction_lot_status_history
    ADD COLUMN previous_sold_quantity INTEGER,
    ADD COLUMN new_sold_quantity INTEGER,
    ADD COLUMN previous_waiting_quantity INTEGER,
    ADD COLUMN new_waiting_quantity INTEGER,
    ADD COLUMN previous_returned_quantity INTEGER,
    ADD COLUMN new_returned_quantity INTEGER;

-- Existing histories have no reliable quantity boundary. Leave all snapshots NULL.
-- New histories carry a complete, non-negative pair. Keep mismatch facts intact.
ALTER TABLE auction_lot_status_history
    ADD CONSTRAINT ck_auction_history_quantities CHECK (
        num_nonnulls(previous_sold_quantity, new_sold_quantity,
            previous_waiting_quantity, new_waiting_quantity,
            previous_returned_quantity, new_returned_quantity) = 0
        OR (
            num_nonnulls(previous_sold_quantity, new_sold_quantity,
                previous_waiting_quantity, new_waiting_quantity,
                previous_returned_quantity, new_returned_quantity) = 6
            AND previous_sold_quantity >= 0 AND new_sold_quantity >= 0
            AND previous_waiting_quantity >= 0 AND new_waiting_quantity >= 0
            AND previous_returned_quantity >= 0 AND new_returned_quantity >= 0
        )
    );

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
  OR (canceled_at IS NOT NULL AND cancellation_reason IS NOT NULL
      AND length(trim(cancellation_reason)) > 0
      AND ((creation_mutation_id IS NULL AND cancellation_mutation_id IS NULL)
        OR (creation_mutation_id IS NOT NULL AND cancellation_mutation_id IS NOT NULL)))
)
);
CREATE INDEX idx_auction_arrival_lot ON auction_return_arrivals(lot_id, id);

ALTER TABLE auction_shipment_lots ADD COLUMN follow_up_method VARCHAR(32);
ALTER TABLE auction_shipment_lots ADD COLUMN disposed_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE auction_shipment_lots ADD COLUMN inferred_return_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE auction_shipment_lots ADD CONSTRAINT ck_auction_follow_up_method
  CHECK (follow_up_method IS NULL OR follow_up_method IN ('REAUCTION', 'FARM_RETURN', 'AUCTION_DISPOSAL'));
ALTER TABLE auction_shipment_lots ADD CONSTRAINT ck_auction_disposed_quantity CHECK (disposed_quantity >= 0);
ALTER TABLE auction_shipment_lots ADD CONSTRAINT ck_auction_inferred_quantity CHECK (inferred_return_quantity >= 0);
-- Legacy totals remain untouched. Known inferred returns are separated only when a new follow-up
-- decision explicitly replaces the inferred status; no arrival or Farm mutation is backfilled.
