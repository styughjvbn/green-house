import assert from "node:assert/strict";
import test from "node:test";
import {
  createPhotoSlotBudget,
  runConcurrentPhotoTasks,
} from "../src/features/sales/lib/photoPreparationQueue.ts";
import { resolveImagePreparationConcurrency } from "../src/shared/config/imagePreparation.ts";

const deferred = () => {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

test("runtime worker configuration defaults to three and accepts sequential or parallel processing", () => {
  assert.equal(resolveImagePreparationConcurrency(undefined), 3);
  for (const value of ["1", "3", "4", " 2 "])
    assert.equal(resolveImagePreparationConcurrency(value), Number(value));
  for (const value of [
    "",
    "0",
    "-1",
    "1.5",
    "NaN",
    "Infinity",
    "1e2",
    "2147483648",
  ])
    assert.throws(() => resolveImagePreparationConcurrency(value));
});

test("a finished photo starts the next without waiting for other active photos", async () => {
  const gates = Array.from({ length: 5 }, deferred);
  const started = [];
  let active = 0,
    peak = 0;
  const running = runConcurrentPhotoTasks(
    [0, 1, 2, 3, 4],
    3,
    new AbortController().signal,
    async (id) => {
      started.push(id);
      active++;
      peak = Math.max(peak, active);
      await gates[id].promise;
      active--;
    },
  );
  assert.deepEqual(started, [0, 1, 2]);
  gates[1].resolve();
  await new Promise(setImmediate);
  assert.deepEqual(started, [0, 1, 2, 3]);
  assert.equal(active, 3);
  gates.forEach((gate) => gate.resolve());
  await running;
  assert.equal(peak, 3);
  assert.deepEqual(started, [0, 1, 2, 3, 4]);
});

test("one worker processes sequentially and abort stops queued photos", async () => {
  const controller = new AbortController();
  const started = [];
  await runConcurrentPhotoTasks([1, 2, 3], 1, controller.signal, async (id) => {
    started.push(id);
    controller.abort();
  });
  assert.deepEqual(started, [1]);
});

test("unexpected failure waits for other active photos before releasing the batch", async () => {
  const gate = deferred();
  const failure = new Error("failed");
  let settled = false;
  const running = runConcurrentPhotoTasks(
    [1, 2],
    2,
    new AbortController().signal,
    async (id) => {
      if (id === 1) throw failure;
      await gate.promise;
    },
  );
  const checked = assert
    .rejects(running, (error) => error === failure)
    .then(() => {
      settled = true;
    });
  await new Promise(setImmediate);
  assert.equal(settled, false);
  gate.resolve();
  await checked;
});

test("parallel reservations cannot exceed the document limit and failed downloads release slots once", async () => {
  const reserve = createPhotoSlotBudget(10, 6);
  const gate = deferred();
  const settled = [];
  await runConcurrentPhotoTasks(
    [1, 2, 3],
    3,
    new AbortController().signal,
    async (id) => {
      if (id === 1) {
        const release = reserve(3);
        await gate.promise;
        release();
        release();
        return;
      }
      if (id === 2) {
        assert.throws(() => reserve(2), /최대 10개/);
        reserve(1);
        gate.resolve();
        return;
      }
      await gate.promise;
      await new Promise(setImmediate);
      reserve(3);
      settled.push(id);
    },
  );
  assert.deepEqual(settled, [3]);
  assert.throws(() => reserve(1), /최대 10개/);
});

test("replacement failures cannot reclaim slots already given to another photo", () => {
  const reserve = createPhotoSlotBudget(10, 10);
  const rollback = reserve(1, 2);
  assert.throws(() => reserve(1), /최대 10개/);
  rollback();
  rollback();
  reserve(2, 2);
  assert.throws(() => reserve(3, 2), /최대 10개/);
});
