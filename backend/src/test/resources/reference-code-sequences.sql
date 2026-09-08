-- H2 does not run Flyway. Reserve a separate range from manually coded test fixtures.
CREATE SEQUENCE IF NOT EXISTS variety_codes_seq START WITH 1000000;
CREATE SEQUENCE IF NOT EXISTS material_codes_seq START WITH 1000000;
