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
        workTypeCode: "MOVEMENT",
        title: "자리 이동 - 청금",
        orchidGroupIds: [1],
        varietyNames: ["청금"],
      },
      {
        id: "work-operation-11",
        nodeType: "WORK_OPERATION",
        selected: false,
        workOperationId: 11,
        workTypeCode: "DISCARD",
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

test("hides consumed source outputs for replacement transformations", () => {
  const graph = buildWorkOperationFlowGraph({
    rootWorkOperationId: 30,
    detail: "MUTATION",
    depth: 1,
    maxNodes: 120,
    truncated: false,
    nodes: [
      {
        id: "work-operation-30",
        nodeType: "WORK_OPERATION",
        selected: true,
        workOperationId: 30,
        workTypeCode: "REPOT",
        title: "분갈이",
        orchidGroupIds: [1],
        varietyNames: ["청금"],
      },
      {
        id: "mutation-30",
        nodeType: "MUTATION",
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      stateNode("group-1-revision-1", 1, 1, 10),
      stateNode("group-1-revision-2", 1, 2, 0, "종료"),
      stateNode("group-2-revision-1", 2, 1, 10),
    ],
    edges: [
      {
        id: "effect-30-30",
        sourceNodeId: "work-operation-30",
        targetNodeId: "mutation-30",
        edgeType: "EFFECT",
      },
      {
        id: "state-input-50",
        sourceNodeId: "group-1-revision-1",
        targetNodeId: "mutation-30",
        edgeType: "STATE_INPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-50",
        sourceNodeId: "mutation-30",
        targetNodeId: "group-1-revision-2",
        edgeType: "STATE_OUTPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-51",
        sourceNodeId: "mutation-30",
        targetNodeId: "group-2-revision-1",
        edgeType: "STATE_OUTPUT",
        relationType: "RESULT",
      },
    ],
  });

  assert.deepEqual(
    graph.nodes
      .filter((node) => node.nodeType === "GROUP_STATE")
      .map((node) => [node.id, node.flowRole]),
    [
      ["group-1-revision-1", "INPUT"],
      ["group-2-revision-1", "RESULT"],
    ],
  );
  assert.deepEqual(
    graph.edges.map((edge) => edge.flowLabel),
    ["투입", "결과"],
  );
});

test("hides a closed legacy movement source while keeping its new result", () => {
  const graph = buildWorkOperationFlowGraph({
    rootWorkOperationId: 40,
    detail: "MUTATION",
    depth: 1,
    maxNodes: 120,
    truncated: false,
    nodes: [
      {
        id: "work-operation-40",
        nodeType: "WORK_OPERATION",
        selected: true,
        workOperationId: 40,
        workTypeCode: "MOVEMENT",
        title: "과거 자리 이동",
        orchidGroupIds: [1],
        varietyNames: ["청금"],
      },
      {
        id: "mutation-40",
        nodeType: "MUTATION",
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      stateNode("group-1-revision-2", 1, 2, 10),
      stateNode("group-1-revision-3", 1, 3, 0, "종료"),
      stateNode("group-2-revision-1", 2, 1, 10),
    ],
    edges: [
      {
        id: "effect-40-40",
        sourceNodeId: "work-operation-40",
        targetNodeId: "mutation-40",
        edgeType: "EFFECT",
      },
      {
        id: "state-input-60",
        sourceNodeId: "group-1-revision-2",
        targetNodeId: "mutation-40",
        edgeType: "STATE_INPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-60",
        sourceNodeId: "mutation-40",
        targetNodeId: "group-1-revision-3",
        edgeType: "STATE_OUTPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-61",
        sourceNodeId: "mutation-40",
        targetNodeId: "group-2-revision-1",
        edgeType: "STATE_OUTPUT",
        relationType: "RESULT",
      },
    ],
  });

  assert.deepEqual(
    graph.nodes
      .filter((node) => node.nodeType === "GROUP_STATE")
      .map((node) => [node.id, node.flowRole]),
    [
      ["group-1-revision-2", "INPUT"],
      ["group-2-revision-1", "RESULT"],
    ],
  );
  assert.deepEqual(
    graph.edges.map((edge) => edge.flowLabel),
    ["투입", "결과"],
  );
});

test("marks the input edge to a voided work and dims from the work onward", () => {
  const graph = buildWorkOperationFlowGraph({
    rootWorkOperationId: 50,
    detail: "MUTATION",
    depth: 2,
    maxNodes: 120,
    truncated: false,
    nodes: [
      {
        id: "work-operation-50",
        nodeType: "WORK_OPERATION",
        selected: true,
        workOperationId: 50,
        workTypeCode: "REPOT",
        status: "VOIDED",
        title: "무효화된 분갈이",
        orchidGroupIds: [1],
        varietyNames: ["청금"],
      },
      {
        id: "work-operation-51",
        nodeType: "WORK_OPERATION",
        selected: false,
        workOperationId: 51,
        workTypeCode: "WATERING",
        status: "COMPLETED",
        title: "후속 작업",
        orchidGroupIds: [2],
        varietyNames: ["청금"],
      },
      {
        id: "mutation-50",
        nodeType: "MUTATION",
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      {
        id: "mutation-51",
        nodeType: "MUTATION",
        selected: false,
        orchidGroupIds: [],
        varietyNames: [],
      },
      stateNode("group-1-revision-1", 1, 1, 10),
      stateNode("group-2-revision-1", 2, 1, 10),
      stateNode("group-2-revision-2", 2, 2, 10),
    ],
    edges: [
      {
        id: "effect-50-50",
        sourceNodeId: "work-operation-50",
        targetNodeId: "mutation-50",
        edgeType: "EFFECT",
      },
      {
        id: "effect-51-51",
        sourceNodeId: "work-operation-51",
        targetNodeId: "mutation-51",
        edgeType: "EFFECT",
      },
      {
        id: "state-input-70",
        sourceNodeId: "group-1-revision-1",
        targetNodeId: "mutation-50",
        edgeType: "STATE_INPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-71",
        sourceNodeId: "mutation-50",
        targetNodeId: "group-2-revision-1",
        edgeType: "STATE_OUTPUT",
        relationType: "RESULT",
      },
      {
        id: "state-input-72",
        sourceNodeId: "group-2-revision-1",
        targetNodeId: "mutation-51",
        edgeType: "STATE_INPUT",
        relationType: "SOURCE",
      },
      {
        id: "state-output-72",
        sourceNodeId: "mutation-51",
        targetNodeId: "group-2-revision-2",
        edgeType: "STATE_OUTPUT",
        relationType: "AFFECTED",
      },
    ],
  });

  assert.equal(
    graph.nodes.find((node) => node.id === "work-operation-50").dimmed,
    true,
  );
  assert.equal(
    graph.nodes.find((node) => node.id === "group-1-revision-1").dimmed,
    undefined,
  );
  assert.equal(
    graph.nodes.find((node) => node.id === "group-2-revision-1").dimmed,
    true,
  );
  assert.equal(
    graph.nodes.find((node) => node.id === "work-operation-51").dimmed,
    true,
  );
  assert.equal(
    graph.nodes.find((node) => node.id === "group-2-revision-2").dimmed,
    true,
  );
  const voidedEdge = graph.edges.find(
    (edge) =>
      edge.sourceNodeId === "group-1-revision-1" &&
      edge.targetNodeId === "work-operation-50",
  );
  assert.equal(voidedEdge.voided, true);
  assert.equal(voidedEdge.dimmed, false);
  assert.equal(
    graph.edges.find(
      (edge) =>
        edge.sourceNodeId === "work-operation-50" &&
        edge.targetNodeId === "group-2-revision-1",
    ).dimmed,
    true,
  );
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
