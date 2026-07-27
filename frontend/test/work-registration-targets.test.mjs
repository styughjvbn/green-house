import assert from "node:assert/strict";
import test from "node:test";
import {
  getIncludedTargets,
  getRecordTargetIds,
} from "../src/features/work-record/model/registrationTargetSelection.ts";

const preview = {
  targets: [
    { orchidGroupId: 11 },
    { orchidGroupId: 12 },
    { orchidGroupId: 13 },
  ],
};

test("record targets use manual selection before a preview exists", () => {
  assert.deepEqual(
    getRecordTargetIds(null, new Set(), new Set([11, 12, 13])),
    [11, 12, 13],
  );
});

test("record targets stay empty when every preview target is excluded", () => {
  const excludedIds = new Set([11, 12, 13]);

  assert.deepEqual(
    getRecordTargetIds(preview, excludedIds, new Set([11, 12, 13])),
    [],
  );
  assert.deepEqual(getIncludedTargets(preview, excludedIds), []);
});
