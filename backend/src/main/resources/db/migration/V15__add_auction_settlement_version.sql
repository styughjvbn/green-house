ALTER TABLE auction_settlements
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
