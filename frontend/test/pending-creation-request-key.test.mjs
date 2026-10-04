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

test("work plans and completed records keep separate pending keys across form remounts", () => {
  const storage = browserStorage();
  let sequence = 0;
  const createKey = () => `work-${++sequence}`;
  const planScope = "greenhouse:work-plan-create-request:v1";
  const recordScope = "greenhouse:work-record-create-request:v1";
  const plan = createPendingCreationRequestKey(
    planScope,
    createKey,
    () => storage,
  );
  const record = createPendingCreationRequestKey(
    recordScope,
    createKey,
    () => storage,
  );
  const planKey = plan.get();
  const recordKey = record.get();
  assert.notEqual(planKey, recordKey);
  const reopened = createPendingCreationRequestKey(
    planScope,
    createKey,
    () => storage,
  );
  assert.equal(reopened.get(), planKey);
  reopened.complete(planKey);
  assert.equal(record.get(), recordKey);
  assert.notEqual(reopened.get(), planKey);
});
