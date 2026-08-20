CREATE UNIQUE INDEX uk_orchid_group_ledger_coverage_open
    ON orchid_group_ledger_coverages ((1))
    WHERE status IN ('PREPARING', 'ACTIVE');
