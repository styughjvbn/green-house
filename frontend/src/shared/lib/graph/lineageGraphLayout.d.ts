export type LineageGraphNode = {
  id: string;
  nodeType: string;
  mutationId?: number | null;
  workOperation?: unknown;
};

export type LineageGraphEdge = {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  edgeType: string;
  entryRole?: string | null;
  relationType?: string | null;
};

export type LineageJunctionNode = {
  id: string;
  nodeType: "JUNCTION";
  mutationId: number | null;
};

export function bundleMutationResults<
  TNode extends LineageGraphNode,
  TEdge extends LineageGraphEdge,
>(
  nodes: TNode[],
  edges: TEdge[],
): {
  nodes: Array<TNode | LineageJunctionNode>;
  edges: Array<TEdge & { edgeType: string }>;
};

export function layoutLineageGraph<TNode extends LineageGraphNode>(
  nodes: TNode[],
  edges: LineageGraphEdge[],
  sizes?: Record<string, { width: number; height: number }>,
): Array<TNode & { position: { x: number; y: number } }>;
