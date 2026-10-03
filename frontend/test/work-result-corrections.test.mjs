import assert from "node:assert/strict";
import test from "node:test";
import { groupResultCorrections } from "../src/features/work-record/lib/workResultCorrections.ts";

const adjustment = (id, before, after, status = "정상") => ({
  orchidGroupId: id,
  beforeQuantity: before,
  afterQuantity: after,
  beforeStatus: "정상",
  afterStatus: status,
});
const event = (id, adjustments) => ({
  id,
  reason: "검수",
  createdAt: "2026-10-02T10:00:00",
  worker: "작업자",
  memo: "확인",
  adjustments,
});

test("each result receives only its own adjustments, preserving audit order and context", () => {
  const first = event(1, [adjustment(10, 60, 55), adjustment(20, 40, 35)]);
  const second = event(2, [adjustment(10, 55, 50)]);
  const original = JSON.stringify([first, second]);
  const grouped = groupResultCorrections([first, second]);
  assert.deepEqual(
    grouped.get(10).map((row) => row.event.id),
    [1, 2],
  );
  assert.deepEqual(
    grouped.get(10).map((row) => row.adjustment.afterQuantity),
    [55, 50],
  );
  assert.equal(grouped.get(20).length, 1);
  assert.equal(grouped.get(20)[0].event.reason, "검수");
  assert.equal(grouped.get(30), undefined);
  assert.equal(JSON.stringify([first, second]), original);
});

test("date-only audits do not appear as quantity or status corrections on unrelated groups", () => {
  const grouped = groupResultCorrections([
    {
      ...event(1, []),
      beforeWorkDate: "2026-10-01",
      afterWorkDate: "2026-09-30",
    },
    event(2, undefined),
    event(3, [adjustment(undefined, 60, 55)]),
  ]);
  assert.equal(grouped.size, 0);
});

test("creation cancellation remains attached to the canceled result", () => {
  const grouped = groupResultCorrections([
    event(1, [adjustment(10, 60, 0, "생성 취소")]),
  ]);
  assert.equal(grouped.get(10)[0].adjustment.afterQuantity, 0);
  assert.equal(grouped.get(10)[0].adjustment.afterStatus, "생성 취소");
});
