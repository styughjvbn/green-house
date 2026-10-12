import assert from "node:assert/strict";
import test from "node:test";
import { createDirectChangeRequests } from "../src/features/sales/lib/directChangeRequests.ts";

function storage() {
  const saved = new Map();
  return {
    getItem: (key) => saved.get(key) ?? null,
    setItem: (key, value) => saved.set(key, value),
    removeItem: (key) => saved.delete(key),
  };
}
const adjustment = {
  operation: "PRICE_ADJUSTMENT",
  payload: {
    itemId: 901,
    sourceChangeId: 23,
    quantity: 5,
    unitPrice: 8000,
    changeDate: "2026-10-08",
    reason: "품질",
  },
};
test("lost adjustment keeps its exact portion, price and key across reload and blocks a new return", () => {
  const saved = storage();
  const first = createDirectChangeRequests(
    () => "first",
    () => saved,
  ).prepare(900, adjustment);
  const reloaded = createDirectChangeRequests(
    () => "second",
    () => saved,
  );
  assert.deepEqual(reloaded.read(900), first);
  assert.deepEqual(
    reloaded.prepare(900, {
      operation: "RETURN",
      payload: { ...adjustment.payload, quantity: 1, unitPrice: undefined },
    }),
    first,
  );
  reloaded.complete(900, "wrong");
  assert.deepEqual(reloaded.read(900), first);
  reloaded.complete(900, "first");
  assert.equal(reloaded.read(900), null);
  assert.equal(reloaded.prepare(900, adjustment).key, "second");
});
test("cancel identity is isolated per document and disabled storage preserves retries", () => {
  let count = 0;
  const requests = createDirectChangeRequests(
    () => `key-${++count}`,
    () => {
      throw Error("disabled");
    },
  );
  const input = {
    operation: "CANCEL",
    changeId: 12,
    payload: { changeDate: "2026-10-09", reason: "오입력" },
  };
  const first = requests.prepare(900, input);
  input.payload.reason = "수정";
  assert.equal(requests.read(900).payload.reason, "오입력");
  assert.notEqual(requests.prepare(901, input).key, first.key);
  assert.equal(requests.prepare(900, { ...input, changeId: 13 }).changeId, 12);
});
