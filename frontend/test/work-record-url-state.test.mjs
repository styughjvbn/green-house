import assert from "node:assert/strict";
import test from "node:test";
import {
  createNormalizedWorkRecordSearchParams,
  createServerSearchParamReader,
  needsWorkRecordUrlNormalization,
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

test("server search parameters use the same work record route parser", () => {
  const state = readWorkRecordUrlState(
    createServerSearchParamReader({
      scope: "ALL",
      view: "LIST",
      keyword: ["관수", "분주"],
      page: "2",
      size: "20",
    }),
    "2026-07",
  );

  assert.equal(state.scope, "ALL");
  assert.equal(state.view, "LIST");
  assert.equal(state.filters.keyword, "관수");
  assert.equal(state.page, 2);
  assert.equal(state.size, 20);
});

test("work record URL defaults are normalized before data prefetch", () => {
  const searchParams = { keyword: "관수" };
  const state = readWorkRecordUrlState(
    createServerSearchParamReader(searchParams),
    "2026-07",
  );

  assert.equal(needsWorkRecordUrlNormalization(searchParams, state), true);
  assert.equal(
    createNormalizedWorkRecordSearchParams(searchParams, state).toString(),
    "keyword=%EA%B4%80%EC%88%98&scope=MANAGEMENT&view=LIST",
  );
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

test("stopped work remains a valid status filter", () => {
  const state = readWorkRecordUrlState(
    new URLSearchParams({ status: "STOPPED" }),
    "2026-07",
  );

  assert.equal(state.filters.status, "STOPPED");
});

test("removed correction filters are ignored and stripped from old URLs", () => {
  const params = {
    scope: "ALL",
    view: "LIST",
    status: "CORRECTED",
    hasCorrections: "true",
  };
  const state = readWorkRecordUrlState(
    createServerSearchParamReader(params),
    "2026-07",
  );
  assert.equal(state.filters.status, "");
  assert.equal("hasCorrections" in state.filters, false);
  assert.equal(needsWorkRecordUrlNormalization(params, state), true);
  const normalized = createNormalizedWorkRecordSearchParams(params, state);
  assert.equal(normalized.has("status"), false);
  assert.equal(normalized.has("hasCorrections"), false);
});

test("filter updates remove stale correction conditions without changing other filters", () => {
  const params = new URLSearchParams({
    hasCorrections: "false",
    status: "COMPLETED",
  });
  const state = readWorkRecordUrlState(params, "2026-07");
  writeWorkListFilterParams(params, state.filters);
  assert.equal(params.has("hasCorrections"), false);
  assert.equal(params.get("status"), "COMPLETED");
});
