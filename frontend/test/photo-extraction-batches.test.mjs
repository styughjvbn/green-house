import assert from "node:assert/strict";
import test from "node:test";
import { runPhotoExtractionBatches } from "../src/features/sales/lib/photoExtractionBatches.ts";

test("documents are extracted in list order, at most ten per turn, without parallel turns", async () => {
  const targets = Array.from({ length: 26 }, (_, i) => `photo-${i}`);
  const calls = [];
  let active = 0;
  await runPhotoExtractionBatches(
    targets,
    new AbortController().signal,
    async (batch, index, total) => {
      assert.equal(++active, 1);
      calls.push({ batch, index, total });
      await new Promise(setImmediate);
      active--;
    },
  );
  assert.deepEqual(
    calls.map(({ batch }) => batch.length),
    [10, 10, 6],
  );
  assert.deepEqual(
    calls.flatMap(({ batch }) => batch),
    targets,
  );
  assert.deepEqual(
    calls.map(({ index, total }) => [index, total]),
    [
      [1, 3],
      [2, 3],
      [3, 3],
    ],
  );
});

test("stopping after a completed turn leaves later documents unrequested", async () => {
  const controller = new AbortController();
  const calls = [];
  await runPhotoExtractionBatches(
    Array.from({ length: 16 }, (_, i) => i),
    controller.signal,
    async (batch) => {
      calls.push(batch);
      controller.abort();
    },
  );
  assert.deepEqual(calls, [Array.from({ length: 10 }, (_, i) => i)]);
  await runPhotoExtractionBatches([1], controller.signal, async () =>
    assert.fail("Already stopped"),
  );
});

test("a failed turn preserves earlier results and does not submit the remaining queue", async () => {
  const completed = [],
    started = [];
  const failure = new Error("Provider failed");
  await assert.rejects(
    runPhotoExtractionBatches(
      Array.from({ length: 26 }, (_, i) => i),
      new AbortController().signal,
      async (batch, index) => {
        started.push(index);
        if (index === 2) throw failure;
        completed.push(...batch);
      },
    ),
    (error) => error === failure,
  );
  assert.deepEqual(started, [1, 2]);
  assert.deepEqual(
    completed,
    Array.from({ length: 10 }, (_, i) => i),
  );
});
