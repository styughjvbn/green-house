ALTER TABLE partner_balance_summaries
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE auction_shipment_lots
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE auction_attempts
    ADD CONSTRAINT uk_auction_attempt_lot_date_no
        UNIQUE (shipment_lot_id, auction_date, attempt_no);

CREATE INDEX idx_auction_shipment_lots_shipment
    ON auction_shipment_lots(shipment_id);

CREATE INDEX idx_auction_attempts_lot
    ON auction_attempts(shipment_lot_id);

CREATE INDEX idx_auction_result_lines_attempt
    ON auction_result_lines(auction_attempt_id);

CREATE INDEX idx_auction_lot_status_history_lot
    ON auction_lot_status_history(shipment_lot_id);
