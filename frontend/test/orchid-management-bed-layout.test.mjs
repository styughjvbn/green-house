import assert from "node:assert/strict";
import test from "node:test";
import {
  resolveActualPlacementMaxPosition,
  resolvePositionUnitCount,
} from "../src/features/orchid-management/lib/bedLayoutUtils.ts";

test("actual placement uses the longest visible bed as its row count", () => {
  assert.equal(resolveActualPlacementMaxPosition([28, 28, 21]), 28);
  assert.equal(resolveActualPlacementMaxPosition([21, 20]), 21);
});

test("position unit count keeps the existing 28-cell fallback", () => {
  assert.equal(resolvePositionUnitCount(null), 28);
  assert.equal(resolvePositionUnitCount(undefined), 28);
  assert.equal(resolvePositionUnitCount(21.9), 21);
  assert.equal(resolvePositionUnitCount(0), 1);
});
