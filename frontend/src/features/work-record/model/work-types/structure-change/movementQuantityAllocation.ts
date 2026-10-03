export type MovementSourceQuantity = {
  sourceOrchidGroupId: number;
  inputQuantity: number;
};

export type MovementSourceAllocation = MovementSourceQuantity & {
  discardQuantity: number;
  movedQuantity: number;
};

export function allocateMovementQuantities(
  sources: MovementSourceQuantity[],
  totalMoved: number,
): MovementSourceAllocation[] {
  const sortedSources = [...sources].sort(
    (left, right) => left.sourceOrchidGroupId - right.sourceOrchidGroupId,
  );
  const totalInput = sortedSources.reduce(
    (total, source) => total + source.inputQuantity,
    0,
  );
  const totalDiscard = Math.max(0, totalInput - totalMoved);
  const allocations = sortedSources.map((source) => {
    const weightedDiscard = totalDiscard * source.inputQuantity;
    const discardQuantity =
      totalInput === 0 ? 0 : Math.floor(weightedDiscard / totalInput);
    return {
      ...source,
      discardQuantity,
      remainder: totalInput === 0 ? 0 : weightedDiscard % totalInput,
    };
  });
  let remainingDiscard =
    totalDiscard -
    allocations.reduce(
      (total, allocation) => total + allocation.discardQuantity,
      0,
    );
  const remainderOrder = [...allocations].sort(
    (left, right) =>
      right.remainder - left.remainder ||
      left.sourceOrchidGroupId - right.sourceOrchidGroupId,
  );
  const extraDiscardIds = new Set<number>();
  for (const allocation of remainderOrder) {
    if (remainingDiscard === 0) break;
    extraDiscardIds.add(allocation.sourceOrchidGroupId);
    remainingDiscard -= 1;
  }
  return allocations.map(
    ({ remainder: _remainder, ...allocation }): MovementSourceAllocation => {
      const discardQuantity =
        allocation.discardQuantity +
        (extraDiscardIds.has(allocation.sourceOrchidGroupId) ? 1 : 0);
      return {
        ...allocation,
        discardQuantity,
        movedQuantity: allocation.inputQuantity - discardQuantity,
      };
    },
  );
}
