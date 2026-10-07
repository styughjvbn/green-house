-- Prepare source-backed payment targets before replacing derived settlements.
-- No daily grouping, fee calculation or legacy financial snapshot is copied here.
CREATE SEQUENCE auction_proceeds_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE auction_proceeds (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    auction_house_id BIGINT NOT NULL REFERENCES business_partners(id) ON DELETE RESTRICT,
    source_reference TEXT,
    reported_gross_amount BIGINT,
    receivable_amount BIGINT,
    matching_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_by VARCHAR(255),
    confirmed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_auction_proceeds_reported_amount CHECK (reported_gross_amount >= 0),
    CONSTRAINT ck_auction_proceeds_receivable CHECK (receivable_amount >= 0),
    CONSTRAINT ck_auction_proceeds_matching_evidence CHECK (
        NOT matching_confirmed OR (source_reference IS NOT NULL AND length(trim(source_reference)) > 0
            AND reported_gross_amount IS NOT NULL AND confirmed_at IS NOT NULL))
);
CREATE INDEX idx_auction_proceeds_house ON auction_proceeds(auction_house_id, id);
CREATE TABLE auction_proceeds_results (
    auction_result_line_id BIGINT PRIMARY KEY REFERENCES auction_result_lines(id) ON DELETE RESTRICT,
    auction_proceeds_id BIGINT NOT NULL REFERENCES auction_proceeds(id) ON DELETE RESTRICT
);
CREATE INDEX idx_auction_proceeds_results_target ON auction_proceeds_results(auction_proceeds_id, auction_result_line_id);
