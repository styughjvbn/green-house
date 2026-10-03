import {
  bundleMutationResults,
  layoutLineageGraph,
} from "../../../shared/lib/graph/lineageGraphLayout.js";
import type { MutationGraphEdge, MutationGraphNode } from "../model/types";

export type MutationGraphViewNode =
  | MutationGraphNode
  | { id: string; nodeType: "JUNCTION"; mutationId: number | null };

export type MutationGraphViewEdge = Omit<MutationGraphEdge, "edgeType"> & {
  edgeType: MutationGraphEdge["edgeType"] | "RESULT_BUNDLE" | "RESULT_BRANCH";
};

export type MutationGraphLayoutNode = MutationGraphViewNode & {
  position: { x: number; y: number };
};

export function buildReadableMutationGraph(
  nodes: MutationGraphNode[],
  edges: MutationGraphEdge[],
) {
  return bundleMutationResults(nodes, edges) as {
    nodes: MutationGraphViewNode[];
    edges: MutationGraphViewEdge[];
  };
}

export function layoutMutationGraph(
  nodes: MutationGraphViewNode[],
  edges: MutationGraphViewEdge[],
) {
  return layoutLineageGraph(nodes, edges) as MutationGraphLayoutNode[];
}
