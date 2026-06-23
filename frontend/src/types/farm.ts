export type BedZoneSide = "LEFT" | "RIGHT" | "CUSTOM" | "HANGING";

export type BedZoneType = "DEFAULT" | "CUSTOM" | "HANGING" | "TRAY" | "GRID";

export type OrchidGroup = {
  id: number;
  bedZoneId: number;
  genus: string | null;
  varietyName: string;
  quantity: number;
  potSize: string | null;
  ageYear: number | null;
  status: string;
  placementType: string | null;
  trayCount: number | null;
  sortOrder: number;
  memo: string | null;
  houseNumber: number;
  physicalBedNumber: number;
  bedZoneName: string;
};

export type BedZone = {
  id: number;
  physicalBedId: number;
  physicalBedNumber: number;
  houseId: number;
  houseNumber: number;
  name: string;
  side: BedZoneSide;
  zoneType: BedZoneType;
  sortOrder: number;
  active: boolean;
  memo: string | null;
  orchidGroups: OrchidGroup[];
};

export type PhysicalBed = {
  id: number;
  houseId: number;
  houseNumber: number;
  number: number;
  displayOrder: number;
  lengthCm: number | null;
  widthCm: number | null;
  wireCount: number | null;
  supportIntervalCm: number | null;
  memo: string | null;
  bedZones: BedZone[];
};

export type House = {
  id: number;
  number: number;
  name: string;
  memo: string | null;
  physicalBeds: PhysicalBed[];
};

export type FarmZoomLevel = "FARM" | "HOUSE" | "PHYSICAL_BED" | "BED_ZONE";

export type FarmStatusTargetType = "HOUSE" | "PHYSICAL_BED" | "BED_ZONE";

export type DashboardSummary = {
  houseCount: number;
  physicalBedCount: number;
  bedZoneCount: number;
  orchidGroupCount: number;
  warningCount: number;
  repotDueCount: number;
  latestWorkDate: string | null;
};

export type HouseStatusSummary = {
  houseId: number;
  houseNumber: number;
  houseName: string;
  orchidGroupCount: number;
  warningCount: number;
  repotDueCount: number;
  latestWorkDate: string | null;
};

export type FarmStatusMapData = {
  houses: HouseStatusSummary[];
};

export type FarmStatusOrchidGroupItem = {
  orchidGroupId: number;
  varietyName: string;
  genus: string | null;
  quantity: number;
  status: string;
  houseId: number;
  houseNumber: number;
  physicalBedId: number;
  physicalBedNumber: number;
  physicalBedName: string;
  bedZoneId: number;
  bedZoneName: string;
};

export type FarmStatusOrchidGroupList = {
  targetType: FarmStatusTargetType;
  targetId: number;
  targetName: string;
  items: FarmStatusOrchidGroupItem[];
};

export type FarmStatusZoomData = {
  level: Exclude<FarmZoomLevel, "FARM">;
  houseId: number;
  houseNumber: number;
  physicalBeds: PhysicalBed[];
  bedZones: BedZone[];
};

export type OrchidManagementViewMode = "REAL_DIRECTION" | "ROTATED" | "BY_BED";

export type SelectedBedZone = {
  type: "BED_ZONE";
  bedZoneId: number;
};

export type SelectedOrchidGroup = {
  type: "ORCHID_GROUP";
  orchidGroupId: number;
};
