import assert from "node:assert/strict";
import test from "node:test";
import { createAuctionFollowUpRequests } from "../src/features/sales/lib/auctionFollowUpRequest.ts";
import { buildAuctionArrivalInput } from "../src/features/sales/lib/auctionArrivalPayload.ts";

function storage() {
  const saved = new Map();
  return {
    getItem: (key) => saved.get(key) ?? null,
    setItem: (key, value) => saved.set(key, value),
    removeItem: (key) => saved.delete(key),
  };
}
const decision = {
  operation: "FOLLOW_UP",
  payload: { method: "FARM_RETURN", reason: "유찰" },
};
test("a lost response preserves the exact input and key across reloads and blocks a different command", () => {
  const saved = storage();
  const before = createAuctionFollowUpRequests(
    () => "same-key",
    () => saved,
  );
  const first = before.prepare(7, decision);
  const after = createAuctionFollowUpRequests(
    () => "new-key",
    () => saved,
  );
  assert.deepEqual(after.read(7), first);
  assert.deepEqual(
    after.prepare(7, {
      operation: "FOLLOW_UP",
      payload: { method: "AUCTION_DISPOSAL", reason: "changed" },
    }),
    first,
  );
  after.complete(7, "wrong-key");
  assert.deepEqual(after.read(7), first);
  after.complete(7, first.payload.idempotencyKey);
  assert.equal(after.read(7), null);
  assert.equal(after.prepare(7, decision).payload.idempotencyKey, "new-key");
});
test("cancel retries preserve the original arrival target and selections have independent commands", () => {
  let id = 0;
  const requests = createAuctionFollowUpRequests(
    () => `key-${++id}`,
    () => null,
  );
  const first = requests.prepare(7, {
    operation: "ARRIVAL_CANCEL",
    arrivalId: 11,
    payload: { correctionDate: "2026-10-08", reason: "정정" },
  });
  assert.deepEqual(
    requests.prepare(7, {
      operation: "ARRIVAL_CANCEL",
      arrivalId: 12,
      payload: { correctionDate: "2026-10-09", reason: "다른 기록" },
    }),
    first,
  );
  assert.notEqual(
    requests.prepare(8, decision).payload.idempotencyKey,
    first.payload.idempotencyKey,
  );
});
test("disabled storage retains the request in memory and draft edits cannot change an unresolved request", () => {
  const requests = createAuctionFollowUpRequests(
    () => "key",
    () => {
      throw new Error("disabled");
    },
  );
  const input = structuredClone(decision);
  requests.prepare(7, input);
  input.payload.reason = "변경";
  assert.equal(requests.read(7).payload.reason, "유찰");
  requests.complete(7, "key");
  assert.equal(requests.read(7), null);
});
test("arrival payload preserves business dates, placement bounds and unspecified values", () => {
  const result = buildAuctionArrivalInput({
    arrivalDate: "2026-10-08",
    varietyId: "9",
    quantity: "5",
    potSize: '3"',
    ageYear: "",
    status: "주의",
    placementType: "SINGLE_POT",
    trayCount: "",
    worker: "",
    memo: " 반환 ",
    placement: {
      bedZoneId: 6,
      startPosition: 3,
      endPosition: 5,
      startCell: 4,
      endCell: 5,
      label: "배치",
    },
  });
  assert.deepEqual(result, {
    operation: "ARRIVAL",
    payload: {
      arrivalDate: "2026-10-08",
      bedZoneId: 6,
      details: {
        varietyId: 9,
        quantity: 5,
        potSize: '3"',
        status: "주의",
        placementType: "SINGLE_POT",
        startPosition: 3,
        endPosition: 5,
        splitPlacementAllowed: false,
        memo: "반환",
      },
    },
  });
  assert.throws(
    () => buildAuctionArrivalInput({ placement: null }),
    /논리 구역/,
  );
});
