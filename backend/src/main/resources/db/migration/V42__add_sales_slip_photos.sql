CREATE TABLE sales_slip_photos (
    sales_slip_id BIGINT PRIMARY KEY REFERENCES sales_slips(id),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(32) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    storage_key VARCHAR(512) NOT NULL UNIQUE CHECK (length(trim(storage_key)) > 0),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 8388608),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMP NOT NULL
);
