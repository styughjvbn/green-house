CREATE TABLE sales_slip_photos (
    sales_slip_id BIGINT PRIMARY KEY REFERENCES sales_slips(id),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(32) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 8388608),
    created_at TIMESTAMP NOT NULL
);
