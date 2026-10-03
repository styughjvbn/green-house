import assert from "node:assert/strict";
import test from "node:test";
import { workOperationGraphEdgeLabel } from "../src/features/work-record/lib/workOperationGraphLabels.ts";

test("labels movement discard relation by its business meaning", () => {
  assert.equal(
    workOperationGraphEdgeLabel("PRECEDES", "MOVEMENT_DISCARD"),
    "이동 후 폐기",
  );
});

test("keeps the generic label for other preceding relations", () => {
  assert.equal(workOperationGraphEdgeLabel("PRECEDES"), "선행");
});
