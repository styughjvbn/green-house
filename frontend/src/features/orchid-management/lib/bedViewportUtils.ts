export type BedOrderItem = {
  id: number;
  houseId: number;
  houseNumber: number;
  number: number;
};

export type ViewportRequestKey = {
  startBedId: number;
  bedCount: 2 | 3 | 4;
};

export function clampBedViewportIndex(index: number, bedLength: number) {
  return Math.min(Math.max(index, 0), Math.max(0, bedLength - 1));
}

export function viewportRequestKeys(
  bedOrder: BedOrderItem[],
  startIndex: number,
  bedCount: 2 | 3 | 4,
): ViewportRequestKey[] {
  const startBedIds = [startIndex - bedCount, startIndex, startIndex + bedCount]
    .map((index) => clampBedViewportIndex(index, bedOrder.length))
    .map((index) => bedOrder[index]?.id)
    .filter((startBedId): startBedId is number => startBedId != null);

  return [...new Set(startBedIds)].map((startBedId) => ({
    startBedId,
    bedCount,
  }));
}

export function mergeViewportRequestKeys(
  current: ViewportRequestKey[],
  next: ViewportRequestKey[],
) {
  const merged = new Map(
    current.map((key) => [`${key.startBedId}:${key.bedCount}`, key]),
  );
  next.forEach((key) => merged.set(`${key.startBedId}:${key.bedCount}`, key));
  return [...merged.values()];
}
