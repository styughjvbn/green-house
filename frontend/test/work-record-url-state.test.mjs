import assert from "node:assert/strict";
import test from "node:test";
import {
  readWorkRecordUrlState,
  writeWorkListFilterParams,
} from "../src/features/work-record/lib/workRecordUrlState.ts";

test("work record URL state reads scope, view, filters, and paging", () => {
  const state = readWorkRecordUrlState(
    new URLSearchParams({
      scope: "ALL",
      view: "CALENDAR",
      from: "2026-07-01",
      to: "2026-07-31",
      status: "IN_PROGRESS",
      keyword: "관수",
      page: "3",
      size: "50",
      month: "2026-07",
    }),
    "2026-01",
  );

  assert.deepEqual(state, {
    scope: "ALL",
    view: "CALENDAR",
    filters: {
      from: "2026-07-01",
      to: "2026-07-31",
      status: "IN_PROGRESS",
      keyword: "관수",
    },
    page: 3,
    size: 50,
    month: "2026-07",
  });
});

test("work record URL state bounds invalid values", () => {
  const state = readWorkRecordUrlState(
    new URLSearchParams({
      scope: "UNKNOWN",
      view: "GRID",
      status: "UNKNOWN",
      page: "-2",
      size: "500",
      month: "2026-15",
    }),
    "2026-07",
  );

  assert.equal(state.scope, "MANAGEMENT");
  assert.equal(state.view, "LIST");
  assert.equal(state.filters.status, "");
  assert.equal(state.page, 0);
  assert.equal(state.size, 100);
  assert.equal(state.month, "2026-07");
});

test("work list filters are written and empty values are removed", () => {
  const params = new URLSearchParams({
    from: "old",
    status: "PLANNED",
  });
  writeWorkListFilterParams(params, {
    from: "",
    to: "2026-07-31",
    status: "COMPLETED",
    keyword: "분주",
  });

  assert.equal(params.has("from"), false);
  assert.equal(params.get("to"), "2026-07-31");
  assert.equal(params.get("status"), "COMPLETED");
  assert.equal(params.get("keyword"), "분주");
});
