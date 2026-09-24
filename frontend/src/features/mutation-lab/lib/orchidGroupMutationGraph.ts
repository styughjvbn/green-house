import dagre from "@dagrejs/dagre";
import type { MutationGraphEdge, MutationGraphNode } from "../model/types";

export type MutationGraphViewNode =
  | MutationGraphNode
  | {
      id: string;
      nodeType: "JUNCTION";
      mutationId: number | null;
    };

export type MutationGraphViewEdge = Omit<MutationGraphEdge, "edgeType"> & {
  edgeType: MutationGraphEdge["edgeType"] | "RESULT_BUNDLE" | "RESULT_BRANCH";
};

export type MutationGraphLayoutNode = MutationGraphViewNode & {
  position: { x: number; y: number };
};

const STATE_SIZE = { width: 270, height: 176 };
const MUTATION_SIZE = { width: 250, height: 112 };
const WORK_MUTATION_SIZE = { width: 250, height: 178 };
const JUNCTION_SIZE = { width: 22, height: 22 };

export function buildReadableMutationGraph(
  nodes: MutationGraphNode[],
  edges: MutationGraphEdge[],
) {
  const resultEdgesByMutation = new Map<string, MutationGraphEdge[]>();
  edges.forEach((edge) => {
    if (edge.edgeType === "STATE_OUTPUT" && edge.entryRole === "RESULT") {
      const resultEdges = resultEdgesByMutation.get(edge.sourceNodeId) ?? [];
      resultEdges.push(edge);
      resultEdgesByMutation.set(edge.sourceNodeId, resultEdges);
    }
  });

  const bundledEdgeIds = new Set(
    [...resultEdgesByMutation.values()]
      .filter((resultEdges) => resultEdges.length > 1)
      .flatMap((resultEdges) => resultEdges.map((edge) => edge.id)),
  );
  const viewNodes: MutationGraphViewNode[] = [...nodes];
  const viewEdges: MutationGraphViewEdge[] = edges.filter(
    (edge) => !bundledEdgeIds.has(edge.id),
  );

  resultEdgesByMutation.forEach((resultEdges, mutationNodeId) => {
    if (resultEdges.length < 2) return;
    const junctionId = `${mutationNodeId}-result-junction`;
    const mutationNode = nodes.find((node) => node.id === mutationNodeId);
    viewNodes.push({
      id: junctionId,
      nodeType: "JUNCTION",
      mutationId: mutationNode?.mutationId ?? null,
    });
    viewEdges.push({
      id: `${mutationNodeId}-result-bundle`,
      sourceNodeId: mutationNodeId,
      targetNodeId: junctionId,
      edgeType: "RESULT_BUNDLE",
      entryRole: "RESULT",
    });
    resultEdges.forEach((edge) =>
      viewEdges.push({
        ...edge,
        sourceNodeId: junctionId,
        edgeType: "RESULT_BRANCH",
      }),
    );
  });

  return { nodes: viewNodes, edges: viewEdges };
}

export function layoutMutationGraph(
  nodes: MutationGraphViewNode[],
  edges: MutationGraphViewEdge[],
): MutationGraphLayoutNode[] {
  const graph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  graph.setGraph({
    rankdir: "LR",
    ranksep: 80,
    nodesep: 48,
    marginx: 28,
    marginy: 28,
  });

  nodes.forEach((node) => graph.setNode(node.id, { ...nodeSize(node) }));
  edges
    .filter((edge) => edge.edgeType !== "MUTATION_RELATION")
    .forEach((edge) => graph.setEdge(edge.sourceNodeId, edge.targetNodeId));
  dagre.layout(graph);

  const positions = new Map(
    nodes.map((node) => {
      const size = nodeSize(node);
      const position = graph.node(node.id) as { x: number; y: number };
      return [
        node.id,
        { x: position.x - size.width / 2, y: position.y - size.height / 2 },
      ] as const;
    }),
  );

  edges
    .filter((edge) => edge.edgeType === "RESULT_BUNDLE")
    .forEach((edge) => {
      const mutationPosition = positions.get(edge.sourceNodeId);
      const junctionPosition = positions.get(edge.targetNodeId);
      const mutationNode = nodes.find((node) => node.id === edge.sourceNodeId);
      const mutationSize = mutationNode
        ? nodeSize(mutationNode)
        : MUTATION_SIZE;
      if (mutationPosition && junctionPosition) {
        positions.set(edge.targetNodeId, {
          ...junctionPosition,
          y:
            mutationPosition.y +
            mutationSize.height * 0.78 -
            JUNCTION_SIZE.height / 2,
        });
      }
    });

  return nodes.map((node) => ({
    ...node,
    position: positions.get(node.id) ?? { x: 0, y: 0 },
  }));
}

function nodeSize(node: MutationGraphViewNode) {
  if (node.nodeType === "STATE") return STATE_SIZE;
  if (node.nodeType === "MUTATION") {
    return node.workOperation ? WORK_MUTATION_SIZE : MUTATION_SIZE;
  }
  return JUNCTION_SIZE;
}
