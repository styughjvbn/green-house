import assert from "node:assert/strict";
import test from "node:test";
import {
  mergeViewportRequestKeys,
  viewportRequestKeys,
} from "../src/features/orchid-management/lib/bedViewportUtils.ts";

const bedOrder = Array.from({ length: 10 }, (_, index) => ({
  id: index + 1,
  houseId: Math.floor(index / 3) + 1,
  houseNumber: Math.floor(index / 3) + 1,
  number: (index % 3) + 1,
}));

test("viewport requests include the current range and adjacent buffers", () => {
  assert.deepEqual(viewportRequestKeys(bedOrder, 4, 3), [
    { startBedId: 2, bedCount: 3 },
    { startBedId: 5, bedCount: 3 },
    { startBedId: 8, bedCount: 3 },
  ]);
});

test("viewport requests clamp and deduplicate farm edges", () => {
  assert.deepEqual(viewportRequestKeys(bedOrder, 0, 3), [
    { startBedId: 1, bedCount: 3 },
    { startBedId: 4, bedCount: 3 },
  ]);
  assert.deepEqual(viewportRequestKeys(bedOrder, 9, 4), [
    { startBedId: 6, bedCount: 4 },
    { startBedId: 10, bedCount: 4 },
  ]);
});

test("visited viewport keys are retained without duplicate requests", () => {
  assert.deepEqual(
    mergeViewportRequestKeys(
      [
        { startBedId: 1, bedCount: 3 },
        { startBedId: 4, bedCount: 3 },
      ],
      [
        { startBedId: 4, bedCount: 3 },
        { startBedId: 7, bedCount: 3 },
      ],
    ),
    [
      { startBedId: 1, bedCount: 3 },
      { startBedId: 4, bedCount: 3 },
      { startBedId: 7, bedCount: 3 },
    ],
  );
});
