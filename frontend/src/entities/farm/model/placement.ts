export type FarmPlacementSelection = {
  bedZoneId: number;
  startCell: number;
  endCell: number;
  startPosition: number;
  endPosition: number;
  label: string;
};

export type FarmPlacementReference = FarmPlacementSelection & {
  kind: "SOURCE" | "RESULT" | "SAVED_RESULT";
};
