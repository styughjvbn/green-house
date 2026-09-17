export function resolvePositionUnitCount(
  value: number | null | undefined,
): number {
  return Math.max(1, Math.floor(value ?? 28));
}

export function resolveActualPlacementMaxPosition(
  positionUnitCounts: Array<number | null | undefined>,
): number {
  return Math.max(1, ...positionUnitCounts.map(resolvePositionUnitCount));
}
