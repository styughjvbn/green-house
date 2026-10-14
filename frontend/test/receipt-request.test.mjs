import assert from "node:assert/strict";
import test from "node:test";
import { createReceiptRequests } from "../src/features/sales/lib/receiptRequest.ts";

test("a receipt retry after reload preserves the partner, input and key", () => {
  const saved = new Map();
  const storage = {
    getItem: (k) => saved.get(k),
    setItem: (k, v) => saved.set(k, v),
    removeItem: (k) => saved.delete(k),
  };
  const first = createReceiptRequests(
    () => "original",
    () => storage,
  );
  const input = {
    operation: "RECEIVE",
    payload: { amount: 123, paymentDate: "2026-10-08", memo: "original" },
  };
  const request = first.prepare(7, input);
  input.payload.amount = 999;
  assert.equal(request.payload.amount, 123);
  const reloaded = createReceiptRequests(
    () => "different",
    () => storage,
  );
  assert.deepEqual(reloaded.read(7), request);
  assert.deepEqual(
    reloaded.prepare(7, {
      operation: "CANCEL",
      receiptId: 18,
      payload: { reason: "changed", correctionDate: "2026-10-09" },
    }),
    request,
  );
  assert.equal(reloaded.read(8), null);
  reloaded.complete(7, "wrong-key");
  assert.deepEqual(reloaded.read(7), request);
  reloaded.complete(7, "original");
  assert.equal(reloaded.read(7), null);
  assert.equal(saved.size, 0);
});

test("cancellation target and reason survive retries with unavailable storage", () => {
  const requests = createReceiptRequests(
    () => "cancel-key",
    () => {
      throw Error("storage unavailable");
    },
  );
  const original = requests.prepare(7, {
    operation: "CANCEL",
    receiptId: 88,
    payload: { correctionDate: "2026-10-08", reason: "오입력" },
  });
  assert.deepEqual(
    requests.prepare(7, {
      operation: "CANCEL",
      receiptId: 99,
      payload: { correctionDate: "2026-10-09", reason: "changed" },
    }),
    original,
  );
  requests.complete(7, "cancel-key");
  assert.equal(requests.read(7), null);
});

test("receipt selection and pagination come from validated URL parameters", async () => {
  const { readReceiptRouteState } =
    await import("../src/features/sales/lib/salesRouteParams.ts");
  assert.deepEqual(
    readReceiptRouteState(
      new URLSearchParams("receiptPartnerId=7&receiptPage=3"),
    ),
    { partnerId: 7, page: 3 },
  );
  assert.deepEqual(
    readReceiptRouteState(
      new URLSearchParams("receiptPartnerId=1.5&receiptPage=invalid"),
    ),
    { partnerId: null, page: 0 },
  );
  assert.deepEqual(
    readReceiptRouteState(
      new URLSearchParams("receiptPartnerId=-1&receiptPage=-2"),
    ),
    { partnerId: null, page: 0 },
  );
  assert.equal(
    readReceiptRouteState(new URLSearchParams("receiptPage=9999999999")).page,
    2147483647,
  );
});
