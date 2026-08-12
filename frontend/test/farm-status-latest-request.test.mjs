import assert from "node:assert/strict";
import test from "node:test";
import { createLatestRequestCoordinator } from "../src/features/farm-status/model/latestRequestCoordinator.ts";

test("only the latest farm status request can update state", () => {
  const coordinator = createLatestRequestCoordinator();
  const first = coordinator.begin();
  const second = coordinator.begin();

  assert.equal(first.signal.aborted, true);
  assert.equal(first.isCurrent(), false);
  assert.equal(second.isCurrent(), true);
  assert.equal(coordinator.complete(first), false);
  assert.equal(coordinator.complete(second), true);
  assert.equal(second.isCurrent(), false);
});

test("cancel invalidates the active farm status request", () => {
  const coordinator = createLatestRequestCoordinator();
  const request = coordinator.begin();

  coordinator.cancel();

  assert.equal(request.signal.aborted, true);
  assert.equal(request.isCurrent(), false);
});
