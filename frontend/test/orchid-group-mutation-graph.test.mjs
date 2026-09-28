import assert from "node:assert/strict";
import test from "node:test";
import {
  buildReadableMutationGraph,
  layoutMutationGraph,
} from "../src/features/mutation-lab/lib/orchidGroupMutationGraph.ts";
import {
  closedCellRange,
  formatMutationGraphLocation,
} from "../src/features/mutation-lab/lib/mutationGraphLocation.ts";

test("formats snapshot placement as a closed human-readable cell range", () => {
  assert.equal(
    formatMutationGraphLocation({
      houseNumber: 3,
      physicalBedNumber: 7,
      side: "RIGHT",
      bedZoneName: "오른쪽 구역",
      startPosition: 0,
      endPosition: 3,
    }),
    "3동 7다이 우측, 1~3칸",
  );
  assert.equal(closedCellRange(2.2, 5.1), "3~6칸");
});

test("bundles multiple result edges behind one junction", () => {
  const nodes = [
    mutationNode("mutation-12", 12),
    stateNode("group-8-revision-1", 8, 1),
    stateNode("group-9-revision-1", 9, 1),
  ];
  const edges = [
    {
      ...edge("result-8", "mutation-12", "group-8-revision-1", "STATE_OUTPUT"),
      entryRole: "RESULT",
      lineageRelationType: "SPLIT_TO",
    },
    {
      ...edge("result-9", "mutation-12", "group-9-revision-1", "STATE_OUTPUT"),
      entryRole: "RESULT",
      lineageRelationType: "SPLIT_TO",
    },
  ];

  const readable = buildReadableMutationGraph(nodes, edges);

  assert.ok(
    readable.nodes.some(
      (node) =>
        node.id === "mutation-12-result-junction" &&
        node.nodeType === "JUNCTION",
    ),
  );
  assert.equal(
    readable.edges.filter((item) => item.edgeType === "RESULT_BUNDLE").length,
    1,
  );
  assert.equal(
    readable.edges.filter((item) => item.edgeType === "RESULT_BRANCH").length,
    2,
  );
  assert.equal(
    readable.edges.some(
      (item) =>
        item.edgeType === "STATE_OUTPUT" && item.sourceNodeId === "mutation-12",
    ),
    false,
  );
});

test("bundles work graph results that expose RESULT as relationType", () => {
  const nodes = [
    mutationNode("mutation-21", 21),
    stateNode("group-10-revision-1", 10, 1),
    stateNode("group-11-revision-1", 11, 1),
  ];
  const edges = [
    {
      ...edge(
        "result-10",
        "mutation-21",
        "group-10-revision-1",
        "STATE_OUTPUT",
      ),
      relationType: "RESULT",
    },
    {
      ...edge(
        "result-11",
        "mutation-21",
        "group-11-revision-1",
        "STATE_OUTPUT",
      ),
      relationType: "RESULT",
    },
  ];

  const readable = buildReadableMutationGraph(nodes, edges);

  assert.equal(
    readable.edges.filter((item) => item.edgeType === "RESULT_BRANCH").length,
    2,
  );
});

test("lays out state and mutation nodes in directed revision order", () => {
  const nodes = [
    stateNode("group-7-revision-1", 7, 1),
    mutationNode("mutation-12", 12),
    stateNode("group-7-revision-2", 7, 2),
    stateNode("group-8-revision-1", 8, 1),
  ];
  const edges = [
    edge("input", "group-7-revision-1", "mutation-12", "STATE_INPUT"),
    edge("source-output", "mutation-12", "group-7-revision-2", "STATE_OUTPUT"),
    edge("result-output", "mutation-12", "group-8-revision-1", "STATE_OUTPUT"),
  ];

  const laidOut = layoutMutationGraph(nodes, edges);
  const byId = new Map(laidOut.map((node) => [node.id, node.position]));

  assert.ok(byId.get("group-7-revision-1").x < byId.get("mutation-12").x);
  assert.ok(byId.get("mutation-12").x < byId.get("group-7-revision-2").x);
  assert.ok(byId.get("mutation-12").x < byId.get("group-8-revision-1").x);
  assert.notEqual(
    byId.get("group-7-revision-2").y,
    byId.get("group-8-revision-1").y,
  );
});

test("does not let correction relation edges change the history layout", () => {
  const nodes = [mutationNode("mutation-1", 1), mutationNode("mutation-2", 2)];
  const relation = edge(
    "relation",
    "mutation-2",
    "mutation-1",
    "MUTATION_RELATION",
  );

  const laidOut = layoutMutationGraph(nodes, [relation]);

  assert.equal(laidOut.length, 2);
  laidOut.forEach((node) => {
    assert.equal(Number.isFinite(node.position.x), true);
    assert.equal(Number.isFinite(node.position.y), true);
  });
});

function stateNode(id, orchidGroupId, revision) {
  return {
    id,
    nodeType: "STATE",
    orchidGroupId,
    stateRevision: revision,
    state: { quantity: 10, reservedQuantity: 0 },
  };
}

function mutationNode(id, mutationId) {
  return {
    id,
    nodeType: "MUTATION",
    mutationId,
    mutationType: "TRANSFORM",
    sourceDomain: "WORK",
  };
}

function edge(id, sourceNodeId, targetNodeId, edgeType) {
  return { id, sourceNodeId, targetNodeId, edgeType };
}
