import dagre from "@dagrejs/dagre";

const DEFAULT_SIZES = {
  STATE: { width: 270, height: 176 },
  MUTATION: { width: 250, height: 112 },
  JUNCTION: { width: 22, height: 22 },
};

export function bundleMutationResults(nodes, edges) {
  const resultEdgesByMutation = new Map();
  edges.forEach((edge) => {
    if (
      edge.edgeType === "STATE_OUTPUT" &&
      (edge.entryRole === "RESULT" || edge.relationType === "RESULT")
    ) {
      const resultEdges = resultEdgesByMutation.get(edge.sourceNodeId) ?? [];
      resultEdges.push(edge);
      resultEdgesByMutation.set(edge.sourceNodeId, resultEdges);
    }
  });
  const bundledIds = new Set(
    [...resultEdgesByMutation.values()]
      .filter((resultEdges) => resultEdges.length > 1)
      .flatMap((resultEdges) => resultEdges.map((edge) => edge.id)),
  );
  const viewNodes = [...nodes];
  const viewEdges = edges.filter((edge) => !bundledIds.has(edge.id));
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
      ...resultEdges[0],
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

export function layoutLineageGraph(nodes, edges, sizes = DEFAULT_SIZES) {
  const graph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  graph.setGraph({
    rankdir: "LR",
    ranksep: 80,
    nodesep: 48,
    marginx: 28,
    marginy: 28,
  });
  const sizeOf = (node) =>
    node.nodeType === "MUTATION" && node.workOperation
      ? { width: 250, height: 178 }
      : (sizes[node.nodeType] ?? { width: 240, height: 120 });
  nodes.forEach((node) => graph.setNode(node.id, { ...sizeOf(node) }));
  edges
    .filter((edge) => edge.edgeType !== "MUTATION_RELATION")
    .forEach((edge) => graph.setEdge(edge.sourceNodeId, edge.targetNodeId));
  dagre.layout(graph);
  return nodes.map((node) => {
    const size = sizeOf(node);
    const position = graph.node(node.id);
    return {
      ...node,
      position: {
        x: position.x - size.width / 2,
        y: position.y - size.height / 2,
      },
    };
  });
}
