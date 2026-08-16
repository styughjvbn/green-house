import assert from "node:assert/strict";
import test from "node:test";
import {
  buildWorkTargetSourceFromForm,
  buildWorkTargetSourceFromGroupChoice,
  derivedGroupWorkTargetSource,
  manualWorkTargetSource,
} from "../src/features/work-record/model/workTargetSource.ts";

test("work target source factories normalize and validate shared payloads", () => {
  assert.deepEqual(derivedGroupWorkTargetSource(" 12:3:POT_35 "), {
    sourceScopeType: "DERIVED_GROUP",
    sourceDerivedGroupKey: "12:3:POT_35",
  });
  assert.deepEqual(manualWorkTargetSource([3, 1, 3]), {
    sourceScopeType: "MANUAL_SELECTION",
    sourceOrchidGroupIds: [3, 1],
  });
  assert.throws(() => manualWorkTargetSource([]), /난 묶음이 한 개 이상/);
});

test("work target source is built consistently from registration form", () => {
  assert.deepEqual(
    buildWorkTargetSourceFromForm(
      { sourceScopeType: "FARM", derivedGroupKey: "", collectionId: "" },
      new Set(),
    ),
    { sourceScopeType: "FARM" },
  );
  assert.deepEqual(
    buildWorkTargetSourceFromForm(
      {
        sourceScopeType: "DERIVED_GROUP",
        derivedGroupKey: " 12:3:POT_35 ",
        collectionId: "",
      },
      new Set(),
    ),
    {
      sourceScopeType: "DERIVED_GROUP",
      sourceDerivedGroupKey: "12:3:POT_35",
    },
  );
  assert.deepEqual(
    buildWorkTargetSourceFromForm(
      {
        sourceScopeType: "MANUAL_SELECTION",
        derivedGroupKey: "",
        collectionId: "",
      },
      new Set([3, 1]),
    ),
    {
      sourceScopeType: "MANUAL_SELECTION",
      sourceOrchidGroupIds: [3, 1],
    },
  );
});

test("group choice and manual choice share the target source builder", () => {
  assert.deepEqual(
    buildWorkTargetSourceFromGroupChoice(
      {
        type: "USER_COLLECTION",
        collectionId: 7,
        label: "관리 그룹",
        memberIds: [1, 2],
      },
      new Set([1, 2]),
    ),
    { sourceScopeType: "USER_COLLECTION", sourceScopeId: 7 },
  );
  assert.deepEqual(
    buildWorkTargetSourceFromGroupChoice(null, new Set([4, 5])),
    {
      sourceScopeType: "MANUAL_SELECTION",
      sourceOrchidGroupIds: [4, 5],
    },
  );
  assert.equal(buildWorkTargetSourceFromGroupChoice(null, new Set()), null);
});
