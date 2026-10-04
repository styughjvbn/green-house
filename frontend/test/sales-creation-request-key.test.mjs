import assert from "node:assert/strict";
import test from "node:test";
import { createSalesCreationRequestKey } from "../src/features/sales/lib/salesCreationRequestKey.ts";

test("lost responses and repeated submits keep one creation identity", () => {
  let count = 0;
  const keys = createSalesCreationRequestKey(() => `key-${++count}`);
  const first = keys.get();
  assert.equal(keys.get(), first);
  assert.equal(keys.get(), first);
  assert.equal(count, 1);
});

test("an acknowledged create permits an intentionally identical new sale", () => {
  let count = 0;
  const keys = createSalesCreationRequestKey(() => `key-${++count}`);
  const first = keys.get();
  keys.complete(first);
  assert.notEqual(keys.get(), first);
});

test("late responses cannot clear the next creation key", () => {
  let count = 0;
  const keys = createSalesCreationRequestKey(() => `key-${++count}`);
  const first = keys.get();
  keys.complete(first);
  const next = keys.get();
  keys.complete(first);
  assert.equal(keys.get(), next);
});

test("navigation and same-tab reload preserve unresolved creation keys", () => {
  const saved = new Map();
  const storage = {
    getItem: (key) => saved.get(key) ?? null,
    setItem: (key, value) => saved.set(key, value),
    removeItem: (key) => saved.delete(key),
  };
  const before = createSalesCreationRequestKey(
    () => "original",
    () => storage,
  );
  const key = before.get();
  const after = createSalesCreationRequestKey(
    () => "next",
    () => storage,
  );
  assert.equal(after.get(), key);
  after.complete(key);
  assert.equal(after.get(), "next");
});

test("blocked storage retains the key within the mounted screen", () => {
  let count = 0;
  const keys = createSalesCreationRequestKey(
    () => `key-${++count}`,
    () => {
      throw new Error("Storage blocked");
    },
  );
  assert.equal(keys.get(), keys.get());
});
