import assert from "node:assert/strict";
import test from "node:test";
import {
  readMutationLabFilters,
  writeMutationLabFilters,
} from "../src/features/mutation-lab/lib/mutationLabUrl.ts";

test("reads valid mutation lab filters and rejects invalid values", () => {
  const filters = readMutationLabFilters(
    new URLSearchParams(
      "orchidGroupId=42&workOperationId=91&mutationType=RESERVE&sourceDomain=SALES&page=2&size=50",
    ),
  );
  assert.deepEqual(filters, {
    orchidGroupId: 42,
    workOperationId: 91,
    mutationType: "RESERVE",
    sourceDomain: "SALES",
    page: 2,
    size: 50,
  });

  assert.deepEqual(
    readMutationLabFilters(
      new URLSearchParams(
        "orchidGroupId=-1&workOperationId=0&mutationType=UNKNOWN&sourceDomain=NOPE&page=-2&size=999",
      ),
    ),
    {
      orchidGroupId: null,
      workOperationId: null,
      mutationType: null,
      sourceDomain: null,
      page: 0,
      size: 20,
    },
  );
});

test("writes updates while preserving unrelated parameters", () => {
  const query = writeMutationLabFilters(
    new URLSearchParams("keep=yes&page=3"),
    {
      orchidGroupId: 7,
      workOperationId: 9,
      mutationType: "MOVE",
      sourceDomain: null,
      page: 0,
    },
  );
  assert.equal(
    query,
    "keep=yes&page=0&orchidGroupId=7&workOperationId=9&mutationType=MOVE",
  );
});
