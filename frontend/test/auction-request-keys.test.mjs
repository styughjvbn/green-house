import assert from "node:assert/strict";
import test from "node:test";
import { createAuctionRequestKeys } from "../src/features/sales/lib/auctionRequestKeys.ts";

function keys() {
  let count = 0;
  return createAuctionRequestKeys(() => `request-${++count}`);
}

test("lost responses and concurrent submissions reuse the pending request key", () => {
  const requests = keys();
  const first = requests.get(1, "RESULT");
  assert.equal(requests.get(1, "RESULT"), first);
  assert.equal(requests.get(1, "RESULT"), first);
});

test("a confirmed operation allows another real partial result with a new key", () => {
  const requests = keys();
  const first = requests.get(1, "RESULT");
  requests.complete(1, "RESULT", first);
  assert.notEqual(requests.get(1, "RESULT"), first);
});

test("selection changes and result/return operations keep independent retry identities", () => {
  const requests = keys();
  const result = requests.get(1, "RESULT");
  const returned = requests.get(1, "RETURN");
  const otherLot = requests.get(2, "RESULT");
  assert.equal(new Set([result, returned, otherLot]).size, 3);
  assert.equal(requests.get(1, "RESULT"), result);
  assert.equal(requests.get(1, "RETURN"), returned);
});

test("an older concurrent response cannot clear the identity of the next operation", () => {
  const requests = keys();
  const first = requests.get(1, "RETURN");
  requests.complete(1, "RETURN", first);
  const next = requests.get(1, "RETURN");
  requests.complete(1, "RETURN", first);
  assert.equal(requests.get(1, "RETURN"), next);
});

test("a lost response keeps the key across navigation and same-tab reload", () => {
  const saved = new Map();
  const storage = {
    getItem: (key) => saved.get(key) ?? null,
    setItem: (key, value) => saved.set(key, value),
    removeItem: (key) => saved.delete(key),
  };
  const beforeReload = createAuctionRequestKeys(
    () => "original",
    () => storage,
  );
  const original = beforeReload.get(7, "RESULT");
  const afterReload = createAuctionRequestKeys(
    () => "next",
    () => storage,
  );
  assert.equal(afterReload.get(7, "RESULT"), original);
  afterReload.complete(7, "RESULT", original);
  assert.equal(afterReload.get(7, "RESULT"), "next");
});

test("unavailable browser storage still preserves retries in the mounted screen", () => {
  let count = 0;
  const requests = createAuctionRequestKeys(
    () => `key-${++count}`,
    () => {
      throw new Error("Storage blocked");
    },
  );
  assert.equal(requests.get(7, "RETURN"), requests.get(7, "RETURN"));
});
