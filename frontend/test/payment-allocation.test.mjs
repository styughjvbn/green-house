import test from "node:test";
import assert from "node:assert/strict";
import {
  allocationInput,
  correctionInput,
} from "../src/features/sales/lib/paymentAllocationPayload.ts";
import { createReceiptRequests } from "../src/features/sales/lib/receiptRequest.ts";
import { readAllocationRouteState } from "../src/features/sales/lib/salesRouteParams.ts";
const row = {
  key: "a",
  receiptId: "4",
  targetType: "SALES_SLIP",
  targetId: "8",
  amount: "300",
};
test("multi rows preserve separate source and target references", () => {
  const input = allocationInput(
    [row, { ...row, receiptId: "5", targetId: "9", amount: "200" }],
    "2026-10-08",
  );
  assert.deepEqual(input.allocations, [
    { receiptId: 4, targetType: "SALES_SLIP", targetId: 8, amount: 300 },
    { receiptId: 5, targetType: "SALES_SLIP", targetId: 9, amount: 200 },
  ]);
});
test("correction supports cancellation only and replacement", () => {
  assert.deepEqual(
    correctionInput([], "2026-10-08", [6], " wrong ").allocations,
    [],
  );
  assert.equal(
    correctionInput([row], "2026-10-08", [6], " wrong ").reason,
    "wrong",
  );
});
test("invalid and unsafe monetary inputs are rejected without calculating domain capacity", () => {
  for (const amount of ["0", "-1", "1.5", "9007199254740992", ""])
    assert.throws(() => allocationInput([{ ...row, amount }], "2026-10-08"));
  assert.throws(() => allocationInput([], "2026-10-08"));
  assert.throws(() => correctionInput([], "2026-10-08", [], "reason"));
});
test("allocation replay keeps exact payload and key across reload and view switch", () => {
  const data = new Map();
  const storage = {
    getItem: (key) => data.get(key),
    setItem: (key, value) => data.set(key, value),
    removeItem: (key) => data.delete(key),
  };
  const first = createReceiptRequests(
    () => "original-key",
    () => storage,
  );
  const input = {
    operation: "CORRECT",
    payload: correctionInput([row], "2026-10-08", [6], "reason"),
  };
  const request = first.prepare(7, input);
  input.payload.allocations[0].amount = 999;
  const second = createReceiptRequests(
    () => "new-key",
    () => storage,
  );
  assert.deepEqual(second.read(7), request);
  assert.deepEqual(
    second.prepare(7, {
      operation: "ALLOCATE",
      payload: allocationInput([row], "2026-10-08"),
    }),
    request,
  );
  second.complete(7, "wrong-key");
  assert.deepEqual(second.read(7), request);
  second.complete(7, "original-key");
  assert.equal(second.read(7), null);
});
test("allocation route keeps URL selection and normalizes invalid pages", () => {
  assert.deepEqual(
    readAllocationRouteState(
      new URLSearchParams(
        "receiptPartnerId=7&receiptId=4&sourcePage=2&allocationPage=1",
      ),
    ),
    { partnerId: 7, receiptId: 4, page: 2, allocationPage: 1 },
  );
  assert.deepEqual(
    readAllocationRouteState(
      new URLSearchParams("receiptId=-1&sourcePage=bad&allocationPage=-5"),
    ),
    { partnerId: null, receiptId: null, page: 0, allocationPage: 0 },
  );
});
