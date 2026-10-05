-- Per-house/day rebuild must not rescan every result for each commit unit.
-- Use the same transactional write maintenance window as V41.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';
CREATE INDEX idx_auction_results_sold_date_id
    ON auction_result_lines (auction_date, id) WHERE amount > 0;
