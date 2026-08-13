export type FarmPlacementSelection = {
  bedZoneId: number;
  startCell: number;
  endCell: number;
  startPosition: number;
  endPosition: number;
  label: string;
};

export type FarmPlacementReference = FarmPlacementSelection & {
  kind:
    | "SOURCE"
    | "RESULT"
    | "OTHER_VARIETY_SOURCE"
    | "OTHER_VARIETY_RESULT"
    | "SAVED_RESULT";
};
