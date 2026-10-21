-- Sale eligibility is not a variety-level policy. Keep all variety identities and links.
ALTER TABLE varieties DROP COLUMN sale_enabled;
