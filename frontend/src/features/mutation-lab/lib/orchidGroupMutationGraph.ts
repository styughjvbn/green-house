import type {
  MutationEntry,
  MutationState,
  OrchidGroupMutation,
} from "../model/types";

export type OrchidGroupMutationGraphPoint = {
  mutationId: number;
  mutationType: OrchidGroupMutation["mutationType"];
  sourceDomain: OrchidGroupMutation["sourceDomain"];
  occurredAt: string;
  effectiveBusinessDate: string;
  revision: number;
  quantity: number | null;
  reservedQuantity: number | null;
  availableQuantity: number | null;
  status: string | null;
  bedZoneId: number | null;
  entry: MutationEntry;
};

export type OrchidGroupMutationGraphEdge = {
  id: string;
  sourceMutationId: number;
  targetMutationId: number;
  kind: "revision" | "relation";
  relationType?: string;
};

export type OrchidGroupMutationGraph = {
  points: OrchidGroupMutationGraphPoint[];
  edges: OrchidGroupMutationGraphEdge[];
};

export function buildOrchidGroupMutationFlow(
  mutations: OrchidGroupMutation[],
  orchidGroupId: number,
): OrchidGroupMutationGraph {
  const points = buildOrchidGroupMutationGraph(mutations, orchidGroupId);
  const mutationIds = new Set(points.map((point) => point.mutationId));
  const revisionEdges = points.slice(1).map((point, index) => ({
    id: `revision-${points[index].mutationId}-${point.mutationId}`,
    sourceMutationId: points[index].mutationId,
    targetMutationId: point.mutationId,
    kind: "revision" as const,
  }));
  const relations = new Map<number, OrchidGroupMutation["relations"][number]>();
  mutations.forEach((mutation) => {
    mutation.relations.forEach((relation) =>
      relations.set(relation.id, relation),
    );
  });
  const relationEdges = [...relations.values()]
    .filter(
      (relation) =>
        mutationIds.has(relation.mutationId) &&
        mutationIds.has(relation.relatedMutationId) &&
        relation.mutationId !== relation.relatedMutationId,
    )
    .map((relation) => ({
      id: `relation-${relation.id}`,
      sourceMutationId: relation.mutationId,
      targetMutationId: relation.relatedMutationId,
      kind: "relation" as const,
      relationType: relation.relationType,
    }));

  return { points, edges: [...revisionEdges, ...relationEdges] };
}

export function buildOrchidGroupMutationGraph(
  mutations: OrchidGroupMutation[],
  orchidGroupId: number,
): OrchidGroupMutationGraphPoint[] {
  return mutations
    .flatMap((mutation) =>
      mutation.entries
        .filter((entry) => entry.orchidGroupId === orchidGroupId)
        .map((entry) => toGraphPoint(mutation, entry)),
    )
    .sort(
      (left, right) =>
        left.revision - right.revision || left.mutationId - right.mutationId,
    );
}

function toGraphPoint(
  mutation: OrchidGroupMutation,
  entry: MutationEntry,
): OrchidGroupMutationGraphPoint {
  const state = entry.afterState;
  const quantity = stateNumber(state, "quantity");
  const reservedQuantity = stateNumber(state, "reservedQuantity");
  return {
    mutationId: mutation.id,
    mutationType: mutation.mutationType,
    sourceDomain: mutation.sourceDomain,
    occurredAt: mutation.occurredAt,
    effectiveBusinessDate: mutation.effectiveBusinessDate,
    revision: entry.stateRevisionAfter,
    quantity,
    reservedQuantity,
    availableQuantity:
      quantity == null || reservedQuantity == null
        ? null
        : quantity - reservedQuantity,
    status: state?.status ?? null,
    bedZoneId: state?.bedZoneId ?? null,
    entry,
  };
}

function stateNumber(
  state: MutationState | null | undefined,
  key: "quantity" | "reservedQuantity",
) {
  const value = state?.[key];
  return typeof value === "number" ? value : null;
}
