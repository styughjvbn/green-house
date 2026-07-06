BEGIN;

ALTER TABLE customers RENAME TO business_partners;
ALTER TABLE business_partners ADD COLUMN partner_type VARCHAR(255) NOT NULL DEFAULT 'WHOLESALE';
ALTER TABLE business_partners ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE sales_slips RENAME COLUMN customer_id TO partner_id;

INSERT INTO business_partners (
    created_at,
    updated_at,
    name,
    partner_type,
    is_active,
    memo
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    shipment.auction_market,
    'AUCTION_HOUSE',
    TRUE,
    '경매 출하 기록에서 전환'
FROM auction_shipments shipment
WHERE NOT EXISTS (
    SELECT 1
    FROM business_partners partner
    WHERE LOWER(partner.name) = LOWER(shipment.auction_market)
)
GROUP BY shipment.auction_market;

UPDATE business_partners partner
SET partner_type = 'AUCTION_HOUSE', updated_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM auction_shipments shipment
    WHERE LOWER(shipment.auction_market) = LOWER(partner.name)
);

ALTER TABLE auction_shipments ADD COLUMN auction_house_id BIGINT;

UPDATE auction_shipments shipment
SET auction_house_id = (
    SELECT MIN(partner.id)
    FROM business_partners partner
    WHERE LOWER(partner.name) = LOWER(shipment.auction_market)
      AND partner.partner_type = 'AUCTION_HOUSE'
);

ALTER TABLE auction_shipments ALTER COLUMN auction_house_id SET NOT NULL;
ALTER TABLE auction_shipments
    ADD CONSTRAINT fk_auction_shipments_auction_house
    FOREIGN KEY (auction_house_id) REFERENCES business_partners(id);
ALTER TABLE auction_shipments DROP COLUMN auction_market;

CREATE INDEX idx_sales_slips_partner_id ON sales_slips(partner_id);
CREATE INDEX idx_auction_shipments_date_house
    ON auction_shipments(shipment_date, auction_house_id);

COMMIT;
