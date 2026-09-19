import assert from "node:assert/strict";
import test from "node:test";
import { buildOrchidGroupMutationFlow } from "../src/features/mutation-lab/lib/orchidGroupMutationGraph.ts";

test("builds one orchid group's graph in revision order", () => {
  const mutations = [
    mutation(12, 7, 2, 8, 3, 99),
    mutation(10, 7, 1, 10, 2, 99),
    mutation(11, 8, 1, 30, 0, 100),
  ];

  const { points, edges } = buildOrchidGroupMutationFlow(mutations, 7);

  assert.deepEqual(
    points.map((point) => ({
      mutationId: point.mutationId,
      revision: point.revision,
      quantity: point.quantity,
      reservedQuantity: point.reservedQuantity,
      availableQuantity: point.availableQuantity,
      bedZoneId: point.bedZoneId,
    })),
    [
      {
        mutationId: 10,
        revision: 1,
        quantity: 10,
        reservedQuantity: 2,
        availableQuantity: 8,
        bedZoneId: 99,
      },
      {
        mutationId: 12,
        revision: 2,
        quantity: 8,
        reservedQuantity: 3,
        availableQuantity: 5,
        bedZoneId: 99,
      },
    ],
  );
  assert.deepEqual(edges, [
    {
      id: "revision-10-12",
      sourceMutationId: 10,
      targetMutationId: 12,
      kind: "revision",
    },
  ]);
});

test("adds a deduplicated relation edge between visible mutation nodes", () => {
  const created = mutation(10, 7, 1, 10, 0, 99);
  const corrected = mutation(12, 7, 2, 8, 0, 99);
  const relation = {
    id: 44,
    mutationId: 12,
    relatedMutationId: 10,
    relationType: "CORRECTS",
  };
  created.relations = [relation];
  corrected.relations = [relation];

  const { edges } = buildOrchidGroupMutationFlow([corrected, created], 7);

  assert.deepEqual(edges.at(-1), {
    id: "relation-44",
    sourceMutationId: 12,
    targetMutationId: 10,
    kind: "relation",
    relationType: "CORRECTS",
  });
  assert.equal(edges.filter((edge) => edge.kind === "relation").length, 1);
});

function mutation(id, orchidGroupId, revision, quantity, reserved, bedZoneId) {
  return {
    id,
    mutationType: revision === 1 ? "CREATE" : "RESERVE",
    sourceDomain: revision === 1 ? "FARM" : "SALES",
    sourceType: "TEST",
    sourceReferenceId: String(id),
    sourceOperationKey: "TEST",
    correlationId: "00000000-0000-0000-0000-000000000000",
    commandFingerprint: "a".repeat(64),
    occurredAt: `2026-09-${10 + revision}T00:00:00Z`,
    recordedAt: `2026-09-${10 + revision}T00:00:00Z`,
    effectiveBusinessDate: `2026-09-${10 + revision}`,
    schemaVersion: 1,
    relations: [],
    entries: [
      {
        id,
        orchidGroupId,
        entryKind: revision === 1 ? "CREATE" : "CHANGE",
        role: "AFFECTED",
        stateRevisionAfter: revision,
        afterState: {
          quantity,
          reservedQuantity: reserved,
          status: "NORMAL",
          bedZoneId,
        },
      },
    ],
  };
}
