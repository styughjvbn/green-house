import assert from "node:assert/strict";
import test from "node:test";
import { allocateMovementQuantities } from "../src/features/work-record/model/work-types/structure-change/movementQuantityAllocation.ts";

test("movement discard is allocated proportionally by selected input quantity", () => {
  assert.deepEqual(
    allocateMovementQuantities(
      [
        { sourceOrchidGroupId: 1, inputQuantity: 200 },
        { sourceOrchidGroupId: 2, inputQuantity: 300 },
      ],
      450,
    ),
    [
      {
        sourceOrchidGroupId: 1,
        inputQuantity: 200,
        discardQuantity: 20,
        movedQuantity: 180,
      },
      {
        sourceOrchidGroupId: 2,
        inputQuantity: 300,
        discardQuantity: 30,
        movedQuantity: 270,
      },
    ],
  );
});

test("movement discard rounding uses largest remainder and source id", () => {
  assert.deepEqual(
    allocateMovementQuantities(
      [
        { sourceOrchidGroupId: 2, inputQuantity: 1 },
        { sourceOrchidGroupId: 1, inputQuantity: 1 },
        { sourceOrchidGroupId: 3, inputQuantity: 1 },
      ],
      2,
    ).map(({ sourceOrchidGroupId, discardQuantity }) => ({
      sourceOrchidGroupId,
      discardQuantity,
    })),
    [
      { sourceOrchidGroupId: 1, discardQuantity: 1 },
      { sourceOrchidGroupId: 2, discardQuantity: 0 },
      { sourceOrchidGroupId: 3, discardQuantity: 0 },
    ],
  );
});
