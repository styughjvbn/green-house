import assert from "node:assert/strict";
import test from "node:test";
import { movementRecordPayload } from "../src/features/orchid-management/lib/movementRecordPayload.ts";

test("simple movement uses one explicit full-quantity pair and the farm business date", () => {
  const payload = movementRecordPayload(
    { id: 348, quantity: 1050, varietyName: "난" },
    { bedZoneId: 4, startPosition: 2, endPosition: 8 },
    7,
    "2026-10-04",
    "stable-key",
  );
  const { operation, execution } = payload.records[0];
  assert.equal(operation.workTypeId, 7);
  assert.equal(operation.plannedStartDate, "2026-10-04");
  assert.equal(execution.completedDate, "2026-10-04");
  assert.equal(execution.idempotencyKey, "stable-key");
  assert.deepEqual(operation.sourceOrchidGroupIds, [348]);
  assert.deepEqual(execution.sources, [
    { sourceOrchidGroupId: 348, inputQuantity: 1050 },
  ]);
  assert.deepEqual(execution.results, [
    {
      bedZoneId: 4,
      startPosition: 2,
      endPosition: 8,
      quantity: 1050,
      attributeSourceOrchidGroupId: 348,
      purpose: "NORMAL",
    },
  ]);
  assert.equal("toBedZoneId" in execution, false);
});
