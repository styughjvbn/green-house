import type {
  WorkOperationGraph,
  WorkOperationGraphEdge,
  WorkOperationGraphNode,
} from "../model/types";

type State = WorkOperationGraphNode["state"];

const SOURCE_CONSUMING_TRANSFORMATION_TYPES = new Set([
  "MOVEMENT",
  "REPOT",
  "DIVIDE",
  "MERGE",
]);

export type WorkFlowRole = "INPUT" | "RESIDUAL" | "RESULT" | "TERMINAL";

export type WorkFlowChange = {
  key: string;
  label: string;
  before: string;
  after: string;
};

export type WorkFlowGroupNode = Omit<WorkOperationGraphNode, "nodeType"> & {
  nodeType: "GROUP_STATE";
  flowRole: WorkFlowRole;
  changes: WorkFlowChange[];
  dimmed?: boolean;
};

export type WorkFlowJunctionNode = {
  id: string;
  nodeType: "FLOW_JUNCTION";
  flowLabel: string;
  dimmed?: boolean;
};

export type WorkFlowNode =
  | (WorkOperationGraphNode & { dimmed?: boolean })
  | WorkFlowGroupNode
  | WorkFlowJunctionNode;

export type WorkFlowEdge = WorkOperationGraphEdge & {
  flowLabel: string;
  labelVisible?: boolean;
  voided?: boolean;
  dimmed?: boolean;
};

export function buildWorkOperationFlowGraph(graph: WorkOperationGraph) {
  const workNodes = graph.nodes.filter(
    (node) => node.nodeType === "WORK_OPERATION",
  );
  const workNodeById = new Map(workNodes.map((node) => [node.id, node]));
  const workNodeIds = new Set(workNodeById.keys());
  const voidedWorkNodeIds = new Set(
    workNodes.filter((node) => node.status === "VOIDED").map((node) => node.id),
  );
  const stateNodeById = new Map(
    graph.nodes
      .filter((node) => node.nodeType === "STATE")
      .map((node) => [node.id, node]),
  );
  const workByMutationId = new Map(
    graph.edges
      .filter(
        (edge) =>
          edge.edgeType === "EFFECT" && workNodeIds.has(edge.sourceNodeId),
      )
      .map((edge) => [edge.targetNodeId, edge.sourceNodeId]),
  );
  const inputEdges = graph.edges.filter(
    (edge) => edge.edgeType === "STATE_INPUT",
  );
  const outputEdges = graph.edges.filter(
    (edge) => edge.edgeType === "STATE_OUTPUT",
  );
  const groupNodes = new Map<string, WorkFlowGroupNode>();
  const flowEdges: WorkFlowEdge[] = [];

  inputEdges.forEach((edge) => {
    const workNodeId = workByMutationId.get(edge.targetNodeId);
    const stateNode = stateNodeById.get(edge.sourceNodeId);
    if (!workNodeId || !stateNode?.orchidGroupId) return;
    upsertGroupNode(groupNodes, stateNode, "INPUT", []);
    flowEdges.push({
      ...edge,
      targetNodeId: workNodeId,
      flowLabel: "투입",
      voided: voidedWorkNodeIds.has(workNodeId),
    });
  });

  outputEdges.forEach((edge) => {
    const workNodeId = workByMutationId.get(edge.sourceNodeId);
    const stateNode = stateNodeById.get(edge.targetNodeId);
    if (!workNodeId || !stateNode?.orchidGroupId) return;
    const workNode = workNodeById.get(workNodeId);
    if (hideConsumedSourceOutput(workNode, edge, stateNode.state)) return;
    const input = matchingInput(edge, inputEdges, stateNodeById);
    const beforeState = input
      ? stateNodeById.get(input.sourceNodeId)?.state
      : undefined;
    const role = outputRole(edge.relationType, stateNode.state);
    upsertGroupNode(
      groupNodes,
      stateNode,
      role,
      stateChanges(beforeState, stateNode.state),
    );
    flowEdges.push({
      ...edge,
      sourceNodeId: workNodeId,
      flowLabel: roleLabel(role),
    });
  });

  const inboundNodes = graph.nodes.filter(
    (node) => node.nodeType === "ORIGIN" && node.originType === "INBOUND",
  );
  graph.edges
    .filter(
      (edge) =>
        edge.edgeType === "ORIGINATED" &&
        inboundNodes.some((node) => node.id === edge.sourceNodeId) &&
        workNodeIds.has(edge.targetNodeId),
    )
    .forEach((edge) =>
      flowEdges.push({
        ...edge,
        flowLabel: "입고",
        voided: voidedWorkNodeIds.has(edge.targetNodeId),
      }),
    );

  graph.edges
    .filter(
      (edge) =>
        edge.edgeType === "PRECEDES" &&
        workNodeIds.has(edge.sourceNodeId) &&
        workNodeIds.has(edge.targetNodeId),
    )
    .forEach((edge) => {
      if (!hasGroupBridge(flowEdges, edge.sourceNodeId, edge.targetNodeId)) {
        flowEdges.push({
          ...edge,
          flowLabel:
            edge.relationType === "MOVEMENT_DISCARD"
              ? "이동 후 폐기"
              : "연계 작업",
        });
      }
    });

  graph.edges
    .filter((edge) => edge.edgeType === "MUTATION_RELATION")
    .forEach((edge) => {
      const sourceWork = workByMutationId.get(edge.sourceNodeId);
      const targetWork = workByMutationId.get(edge.targetNodeId);
      if (!sourceWork || !targetWork || sourceWork === targetWork) return;
      if (
        flowEdges.some(
          (candidate) =>
            candidate.sourceNodeId === sourceWork &&
            candidate.targetNodeId === targetWork,
        )
      ) {
        return;
      }
      flowEdges.push({
        ...edge,
        sourceNodeId: sourceWork,
        targetNodeId: targetWork,
        flowLabel: relationLabel(edge.relationType),
      });
    });

  const bundled = bundleParallelFlows(
    [...inboundNodes, ...workNodes, ...groupNodes.values()] as WorkFlowNode[],
    deduplicateEdges(flowEdges),
  );
  return dimVoidedBranches(bundled.nodes, bundled.edges);
}

function dimVoidedBranches(nodes: WorkFlowNode[], edges: WorkFlowEdge[]) {
  const outgoing = new Map<string, WorkFlowEdge[]>();
  edges.forEach((edge) => {
    const values = outgoing.get(edge.sourceNodeId) ?? [];
    values.push(edge);
    outgoing.set(edge.sourceNodeId, values);
  });
  const dimmedNodeIds = new Set<string>();
  const pending = edges
    .filter((edge) => edge.voided)
    .map((edge) => edge.targetNodeId);
  while (pending.length) {
    const nodeId = pending.shift();
    if (!nodeId || dimmedNodeIds.has(nodeId)) continue;
    dimmedNodeIds.add(nodeId);
    outgoing.get(nodeId)?.forEach((edge) => pending.push(edge.targetNodeId));
  }
  return {
    nodes: nodes.map((node) =>
      dimmedNodeIds.has(node.id) ? { ...node, dimmed: true } : node,
    ),
    edges: edges.map((edge) => ({
      ...edge,
      dimmed: !edge.voided && dimmedNodeIds.has(edge.sourceNodeId),
    })),
  };
}

function hideConsumedSourceOutput(
  workNode: WorkOperationGraphNode | undefined,
  edge: WorkOperationGraphEdge,
  state: State,
) {
  return (
    edge.relationType === "SOURCE" &&
    SOURCE_CONSUMING_TRANSFORMATION_TYPES.has(workNode?.workTypeCode ?? "") &&
    (state == null || state.quantity === 0 || state.status === "종료")
  );
}

function bundleParallelFlows(nodes: WorkFlowNode[], edges: WorkFlowEdge[]) {
  const workNodeIds = new Set(
    nodes
      .filter((node) => node.nodeType === "WORK_OPERATION")
      .map((node) => node.id),
  );
  const groups = new Map<
    string,
    { direction: "INPUT" | "OUTPUT"; label: string; edges: WorkFlowEdge[] }
  >();
  edges.forEach((edge) => {
    const direction = workNodeIds.has(edge.targetNodeId)
      ? "INPUT"
      : workNodeIds.has(edge.sourceNodeId)
        ? "OUTPUT"
        : null;
    if (
      !direction ||
      !["투입", "입고", "잔류", "결과", "처리 결과"].includes(edge.flowLabel)
    ) {
      return;
    }
    const workNodeId =
      direction === "INPUT" ? edge.targetNodeId : edge.sourceNodeId;
    const key = `${direction}:${workNodeId}:${edge.flowLabel}`;
    const group = groups.get(key) ?? {
      direction,
      label: edge.flowLabel,
      edges: [],
    };
    group.edges.push(edge);
    groups.set(key, group);
  });

  const bundledEdgeIds = new Set<string>();
  const junctionNodes: WorkFlowJunctionNode[] = [];
  const bundledEdges: WorkFlowEdge[] = [];
  groups.forEach((group, key) => {
    if (group.edges.length < 2) return;
    group.edges.forEach((edge) => bundledEdgeIds.add(edge.id));
    const junctionId = `flow-junction-${key.replaceAll(":", "-")}`;
    junctionNodes.push({
      id: junctionId,
      nodeType: "FLOW_JUNCTION",
      flowLabel: group.label,
    });
    const first = group.edges[0];
    if (group.direction === "INPUT") {
      group.edges.forEach((edge, index) =>
        bundledEdges.push({
          ...edge,
          id: `${edge.id}-branch-${index}`,
          targetNodeId: junctionId,
          labelVisible: false,
        }),
      );
      bundledEdges.push({
        ...first,
        id: `${junctionId}-trunk`,
        sourceNodeId: junctionId,
        targetNodeId: first.targetNodeId,
        labelVisible: true,
      });
      return;
    }
    bundledEdges.push({
      ...first,
      id: `${junctionId}-trunk`,
      sourceNodeId: first.sourceNodeId,
      targetNodeId: junctionId,
      labelVisible: true,
    });
    group.edges.forEach((edge, index) =>
      bundledEdges.push({
        ...edge,
        id: `${edge.id}-branch-${index}`,
        sourceNodeId: junctionId,
        labelVisible: false,
      }),
    );
  });

  return {
    nodes: [...nodes, ...junctionNodes],
    edges: [
      ...edges.filter((edge) => !bundledEdgeIds.has(edge.id)),
      ...bundledEdges,
    ],
  };
}

function upsertGroupNode(
  nodes: Map<string, WorkFlowGroupNode>,
  stateNode: WorkOperationGraphNode,
  role: WorkFlowRole,
  changes: WorkFlowChange[],
) {
  const current = nodes.get(stateNode.id);
  if (current && current.flowRole !== "INPUT") return;
  nodes.set(stateNode.id, {
    ...stateNode,
    nodeType: "GROUP_STATE",
    flowRole:
      current?.flowRole === "INPUT" && role === "INPUT" ? "INPUT" : role,
    changes: changes.length ? changes : (current?.changes ?? []),
  });
}

function matchingInput(
  output: WorkOperationGraphEdge,
  inputs: WorkOperationGraphEdge[],
  stateNodes: Map<string, WorkOperationGraphNode>,
) {
  const outputState = stateNodes.get(output.targetNodeId);
  const entryId = graphEntryId(output.id);
  return (
    inputs.find(
      (input) =>
        input.targetNodeId === output.sourceNodeId &&
        graphEntryId(input.id) === entryId,
    ) ??
    inputs.find(
      (input) =>
        input.targetNodeId === output.sourceNodeId &&
        stateNodes.get(input.sourceNodeId)?.orchidGroupId ===
          outputState?.orchidGroupId,
    )
  );
}

function graphEntryId(edgeId: string) {
  return edgeId.replace(/^state-(?:input|output)-/, "");
}

function outputRole(
  relationType: string | null | undefined,
  state: State,
): WorkFlowRole {
  if (
    state == null ||
    ["폐기", "종료", "판매 완료", "생성 취소"].includes(state.status ?? "")
  ) {
    return "TERMINAL";
  }
  return relationType === "SOURCE" ? "RESIDUAL" : "RESULT";
}

function roleLabel(role: WorkFlowRole) {
  if (role === "INPUT") return "투입";
  if (role === "RESIDUAL") return "잔류";
  if (role === "TERMINAL") return "처리 결과";
  return "결과";
}

function relationLabel(type?: string | null) {
  if (type === "CORRECTS") return "보정";
  if (type === "COMPENSATES") return "보상";
  if (type === "SUPERSEDES") return "대체";
  return "연계 작업";
}

function hasGroupBridge(
  edges: WorkFlowEdge[],
  sourceWorkId: string,
  targetWorkId: string,
) {
  const outputs = new Set(
    edges
      .filter((edge) => edge.sourceNodeId === sourceWorkId)
      .map((edge) => edge.targetNodeId),
  );
  return edges.some(
    (edge) =>
      outputs.has(edge.sourceNodeId) && edge.targetNodeId === targetWorkId,
  );
}

function deduplicateEdges(edges: WorkFlowEdge[]) {
  const seen = new Set<string>();
  return edges.filter((edge) => {
    const key = `${edge.sourceNodeId}:${edge.targetNodeId}:${edge.flowLabel}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function stateChanges(before: State, after: State): WorkFlowChange[] {
  if (!after) return [];
  if (!before) {
    return [
      { key: "created", label: "변화", before: "없음", after: "신규 생성" },
    ];
  }
  const fields = [
    ["quantity", "수량", quantity(before.quantity), quantity(after.quantity)],
    [
      "reservedQuantity",
      "예약",
      quantity(before.reservedQuantity),
      quantity(after.reservedQuantity),
    ],
    ["status", "상태", value(before.status), value(after.status)],
    ["variety", "품종", variety(before), variety(after)],
    ["ageYear", "년생", age(before.ageYear), age(after.ageYear)],
    [
      "potSizeCode",
      "화분",
      value(before.potSizeCode),
      value(after.potSizeCode),
    ],
    ["location", "위치", location(before), location(after)],
  ];
  return fields
    .filter(([, , beforeValue, afterValue]) => beforeValue !== afterValue)
    .map(([key, label, beforeValue, afterValue]) => ({
      key,
      label,
      before: beforeValue,
      after: afterValue,
    }));
}

function quantity(value?: number | null) {
  return value == null ? "-" : `${value}분`;
}

function age(value?: number | null) {
  return value == null ? "-" : `${value}년생`;
}

function value(value?: string | null) {
  return value || "-";
}

function variety(state: NonNullable<State>) {
  return [state.genus, state.varietyName].filter(Boolean).join(" ") || "-";
}

function location(state: NonNullable<State>) {
  const place = [
    state.houseNumber != null ? `${state.houseNumber}동` : null,
    state.physicalBedNumber != null ? `${state.physicalBedNumber}다이` : null,
    state.bedZoneName,
  ]
    .filter(Boolean)
    .join(" · ");
  const range =
    state.startPosition != null && state.endPosition != null
      ? `${state.startPosition}~${state.endPosition}`
      : null;
  return [place, range].filter(Boolean).join(" / ") || "-";
}
