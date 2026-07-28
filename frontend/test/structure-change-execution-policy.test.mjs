import assert from "node:assert/strict";
import test from "node:test";
import { isStructureChangeSourceLocked } from "../src/features/work-record/model/work-types/structure-change/structureChangeExecutionPolicy.ts";

test("scheduled structure change execution allows partial source quantity", () => {
  assert.equal(isStructureChangeSourceLocked(false), false);
});

test("immediate structure change record locks source quantity", () => {
  assert.equal(isStructureChangeSourceLocked(true), true);
});
