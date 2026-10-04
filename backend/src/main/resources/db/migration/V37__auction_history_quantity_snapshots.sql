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
