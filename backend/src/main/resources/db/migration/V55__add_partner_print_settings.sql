ALTER TABLE partner_settlement_settings
    ADD COLUMN print_template VARCHAR(50) NOT NULL DEFAULT 'STANDARD_A5',
    ADD COLUMN shipper_code VARCHAR(50),
    ADD COLUMN shipper_name VARCHAR(100);

ALTER TABLE partner_settlement_settings
    ADD CONSTRAINT ck_partner_print_template
        CHECK (print_template IN ('STANDARD_A5', 'YANGJAE_AUCTION_SHIPMENT'));
