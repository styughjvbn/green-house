import type {
  FarmStatusMapData,
  FarmStatusOrchidGroupItem,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
  OrchidGroup,
} from "@/entities/farm/types";

export type SelectedTarget = {
  type: FarmStatusTargetType;
  id: number;
};

export type SelectedFarmStatusOrchidGroup = FarmStatusOrchidGroupItem & {
  ageYear?: number | null;
  endPosition?: number | null;
  memo?: string | null;
  placementType?: string | null;
  potSize?: string | null;
  sortOrder?: number | null;
  splitPlacementAllowed?: boolean | null;
  startPosition?: number | null;
  trayCount?: number | null;
  varietyId?: number | null;
};

export type FarmStatusSearchState = {
  keyword: string;
  status: string;
};

export type FarmStatusLayoutMode = "NORMALIZED" | "ACTUAL";
export type FarmStatusColorMode = "STATUS" | "VARIETY" | "AGE";

export type FarmStatusFilterMatches = {
  bedZoneIds: Set<number>;
  houseIds: Set<number>;
  orchidGroupIds: Set<number>;
  physicalBedKeys: Set<string>;
};

export type FarmStatusSearchResult = OrchidGroup;

export type FarmStatusDerivedGroup = {
  groupKey: string;
  varietyName: string;
  genus: string | null;
  ageYear: number | null;
  potSizeCode: OrchidGroup["potSizeCode"];
  potSize: string | null;
  orchidGroupCount: number;
  totalQuantity: number;
};

export type FarmStatusCollection = {
  id: number;
  name: string;
  description: string | null;
  purpose: string | null;
  status: "ACTIVE" | "ARCHIVED";
  orchidGroupCount: number;
  totalQuantity: number;
  members: Array<{ orchidGroupId: number }>;
};

export type FarmStatusSearchGroup = {
  key: string;
  type: "DERIVED" | "COLLECTION";
  label: string;
  description: string;
};

export type FarmStatusMapProps = {
  mapData: FarmStatusMapData;
  initialSelection: FarmStatusOrchidGroupList | null;
  initialZoom: FarmStatusZoomData | null;
};
