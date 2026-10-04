import assert from "node:assert/strict";
import test from "node:test";
import { createPendingCreationRequestKey } from "../src/shared/lib/pendingCreationRequestKey.ts";

function browserStorage() {
  const saved = new Map();
  return {
    getItem: (key) => saved.get(key) ?? null,
    setItem: (key, value) => saved.set(key, value),
    removeItem: (key) => saved.delete(key),
  };
}

test("inbound and sales retries keep independent identities across reloads", () => {
  const storage = browserStorage();
  const sales = createPendingCreationRequestKey(
    "greenhouse:sales-create-request:v1",
    () => "sale",
    () => storage,
  );
  const inbound = createPendingCreationRequestKey(
    "greenhouse:inbound-create-request:v1",
    () => "inbound",
    () => storage,
  );
  assert.equal(sales.get(), "sale");
  assert.equal(inbound.get(), "inbound");
  const reloaded = createPendingCreationRequestKey(
    "greenhouse:inbound-create-request:v1",
    () => "next",
    () => storage,
  );
  assert.equal(reloaded.get(), "inbound");
  reloaded.complete("inbound");
  assert.equal(sales.get(), "sale");
  assert.equal(reloaded.get(), "next");
});

test("input errors and reopening an inbound form retain the unresolved identity", () => {
  let count = 0;
  const requests = createPendingCreationRequestKey(
    "inbound",
    () => `inbound-${++count}`,
  );
  const original = requests.get();
  assert.equal(requests.get(), original);
  requests.complete(original);
  const next = requests.get();
  requests.complete(original);
  assert.equal(requests.get(), next);
});
