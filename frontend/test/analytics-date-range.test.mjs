import assert from "node:assert/strict";
import test from "node:test";
import {
  defaultAnalyticsDateRange,
  readAnalyticsDateRange,
} from "../src/features/analytics/lib/analyticsDateRange.ts";

const today = new Date(2026, 6, 29);

test("analytics date range keeps a valid URL range", () => {
  assert.deepEqual(readAnalyticsDateRange("2026-01-01", "2026-07-29", today), {
    dateFrom: "2026-01-01",
    dateTo: "2026-07-29",
  });
});

test("analytics date range rejects invalid and over-two-year ranges", () => {
  const defaults = defaultAnalyticsDateRange(today);

  assert.deepEqual(
    readAnalyticsDateRange("2026-02-30", "2026-07-29", today),
    defaults,
  );
  assert.deepEqual(
    readAnalyticsDateRange("2026-08-01", "2026-07-29", today),
    defaults,
  );
  assert.deepEqual(
    readAnalyticsDateRange("2024-07-28", "2026-07-29", today),
    defaults,
  );
});
