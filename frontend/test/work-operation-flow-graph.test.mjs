import assert from "node:assert/strict";
import test from "node:test";
import { buildWorkOperationFlowGraph } from "../src/features/work-record/lib/workOperationFlowGraph.ts";

function stateNode(id, groupId, revision, quantity, status = "정상") {
  return {
    id,
    nodeType: "STATE",
    selected: false,
    orchidGroupId: groupId,
    stateRevision: revision,
    state: { quantity, reservedQuantity: 0, status, varietyName: "청금" },
    orchidGroupIds: [groupId],
    varietyNames: ["청금"],
  };
}

test("builds input work residual result and linked discard flow without mutation nodes", () => {
  const graph = buildWorkOperationFlowGraph({
    rootWorkOperationId: 10,
    detail: "MUTATION",
    depth: 1,
    maxNodes: 120,
    truncated: false,
    nodes: [
      {
        id: "work-operation-10",
        nodeType: "WORK_OPERATION",
        selected: true,
        workOperationId: 10,
        title: "자리 이동 - 청금",
        orchidGroupIds: [1],
        varietyNames: ["청금"],
      },
      {
        id: "work-operation-11",
        nodeType: "WORK_OPERATION",
        selected: false,
        workOperationId: 11,
        title: "이동 후 폐기 - 청금",
        orchidGroupIds: [1],
        varietyNames: ["청금"],
      },
      {
        id: "mutation-20",
        nodeType: "MUTATION",
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      {
        id: "mutation-21",
        nodeType: "MUTATION",
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      stateNode("group-1-revision-1", 1, 1, 100),
      stateNode("group-1-revision-2", 1, 2, 20),
      stateNode("group-2-revision-1", 2, 1, 80),
      stateNode("group-1-revision-3", 1, 3, 0, "폐기"),
    ],
    edges: [
      {
        id: "effect-10-20",
        sourceNodeId: "work-operation-10",
        targetNodeId: "mutation-20",
        edgeType: "EFFECT",
      },
      {
        id: "effect-11-21",
        sourceNodeId: "work-operation-11",
        targetNodeId: "mutation-21",
        edgeType: "EFFECT",
      },
      {
        id: "state-input-31",
        sourceNodeId: "group-1-revision-1",
        targetNodeId: "mutation-20",
        edgeType: "STATE_INPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-31",
        sourceNodeId: "mutation-20",
        targetNodeId: "group-1-revision-2",
        edgeType: "STATE_OUTPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-32",
        sourceNodeId: "mutation-20",
        targetNodeId: "group-2-revision-1",
        edgeType: "STATE_OUTPUT",
        relationType: "RESULT",
      },
      {
        id: "state-input-33",
        sourceNodeId: "group-1-revision-2",
        targetNodeId: "mutation-21",
        edgeType: "STATE_INPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-33",
        sourceNodeId: "mutation-21",
        targetNodeId: "group-1-revision-3",
        edgeType: "STATE_OUTPUT",
        relationType: "SOURCE",
      },
      {
        id: "precedes-10-11",
        sourceNodeId: "work-operation-10",
        targetNodeId: "work-operation-11",
        edgeType: "PRECEDES",
        relationType: "MOVEMENT_DISCARD",
      },
    ],
  });

  assert.equal(
    graph.nodes.some((node) => node.nodeType === "MUTATION"),
    false,
  );
  assert.deepEqual(
    graph.nodes
      .filter((node) => node.nodeType === "GROUP_STATE")
      .map((node) => [node.id, node.flowRole]),
    [
      ["group-1-revision-1", "INPUT"],
      ["group-1-revision-2", "RESIDUAL"],
      ["group-2-revision-1", "RESULT"],
      ["group-1-revision-3", "TERMINAL"],
    ],
  );
  assert.equal(
    graph.edges.some(
      (edge) =>
        edge.sourceNodeId === "work-operation-10" &&
        edge.targetNodeId === "work-operation-11",
    ),
    false,
  );
  assert.deepEqual(
    graph.edges.map((edge) => edge.flowLabel),
    ["투입", "투입", "잔류", "결과", "처리 결과"],
  );
});

test("keeps inbound as a direct input to the work", () => {
  const graph = buildWorkOperationFlowGraph({
    rootWorkOperationId: 5,
    detail: "MUTATION",
    depth: 1,
    maxNodes: 120,
    truncated: false,
    nodes: [
      {
        id: "origin-inbound-7",
        nodeType: "ORIGIN",
        originType: "INBOUND",
        originReferenceId: 7,
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      {
        id: "work-operation-5",
        nodeType: "WORK_OPERATION",
        selected: true,
        workOperationId: 5,
        orchidGroupIds: [],
        varietyNames: [],
      },
    ],
    edges: [
      {
        id: "originated-inbound-7-5",
        sourceNodeId: "origin-inbound-7",
        targetNodeId: "work-operation-5",
        edgeType: "ORIGINATED",
      },
    ],
  });

  assert.equal(graph.edges[0].flowLabel, "입고");
});

test("bundles multiple results of the same kind through one junction", () => {
  const graph = buildWorkOperationFlowGraph({
    rootWorkOperationId: 5,
    detail: "MUTATION",
    depth: 1,
    maxNodes: 120,
    truncated: false,
    nodes: [
      {
        id: "work-operation-5",
        nodeType: "WORK_OPERATION",
        selected: true,
        workOperationId: 5,
        orchidGroupIds: [2, 3],
        varietyNames: ["청금"],
      },
      {
        id: "mutation-8",
        nodeType: "MUTATION",
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      stateNode("group-2-revision-1", 2, 1, 30),
      stateNode("group-3-revision-1", 3, 1, 40),
    ],
    edges: [
      {
        id: "effect-5-8",
        sourceNodeId: "work-operation-5",
        targetNodeId: "mutation-8",
        edgeType: "EFFECT",
      },
      {
        id: "state-output-41",
        sourceNodeId: "mutation-8",
        targetNodeId: "group-2-revision-1",
        edgeType: "STATE_OUTPUT",
        relationType: "RESULT",
      },
      {
        id: "state-output-42",
        sourceNodeId: "mutation-8",
        targetNodeId: "group-3-revision-1",
        edgeType: "STATE_OUTPUT",
        relationType: "RESULT",
      },
    ],
  });

  const junction = graph.nodes.find(
    (node) => node.nodeType === "FLOW_JUNCTION",
  );
  assert.ok(junction);
  assert.equal(
    graph.edges.filter((edge) => edge.flowLabel === "결과").length,
    3,
  );
  assert.equal(
    graph.edges.filter((edge) => edge.labelVisible !== false).length,
    1,
  );
  assert.deepEqual(
    graph.edges
      .filter((edge) => edge.sourceNodeId === junction.id)
      .map((edge) => edge.targetNodeId),
    ["group-2-revision-1", "group-3-revision-1"],
  );
});
