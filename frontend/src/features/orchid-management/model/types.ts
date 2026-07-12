import type {
  BedZone,
  FarmStatusMapData,
  House,
  OrchidGroup,
  SelectedBedZone,
  SelectedOrchidGroup,
  WorkRecord,
  WorkRecordTargetType,
  WorkType,
} from "@/entities/farm/types";

export type OrchidSelection = SelectedBedZone | SelectedOrchidGroup;

export type MutationMode = "CREATE" | "EDIT" | "MOVE" | "WORK_RECORD" | null;

export type DragState = {
  orchidGroupId: number;
  overBedZoneId: number | null;
} | null;

export type MapCellRangePick = {
  active: boolean;
  excludeOrchidGroupId: number | null;
  targetBedZoneId: number | null;
  startCell: number | null;
  endCell: number | null;
  version: number;
};

export type OrchidFormState = {
  varietyId: string;
  varietyQuery: string;
  genus: string;
  varietyName: string;
  quantity: string;
  potSize: string;
  ageYear: string;
  status: string;
  placementType: string;
  trayCount: string;
  splitPlacementAllowed: boolean;
  startPosition: string;
  endPosition: string;
  memo: string;
};

export type MutationPayload = {
  varietyId: number;
  quantity: number;
  potSize: string | null;
  ageYear: number | null;
  status: string;
  placementType: string | null;
  trayCount: number | null;
  splitPlacementAllowed: boolean;
  startPosition: number | null;
  endPosition: number | null;
  memo: string | null;
};

export type PreciseMovePayload = {
  toBedZoneId: number;
  startPosition?: number | null;
  endPosition?: number | null;
  memo: string;
};

export type WorkRecordQuickFormState = {
  workTypeId: string;
  workDate: string;
  targetType: WorkRecordTargetType;
  targetId: number | null;
  materialName: string;
  dilutionRatio: string;
  quantity: string;
  worker: string;
  memo: string;
};

export type WorkRecordQuickPayload = {
  workTypeId: number;
  workDate: string;
  targetType: WorkRecordTargetType;
  targetId: number | null;
  materialName: string | null;
  dilutionRatio: string | null;
  quantity: string | null;
  worker: string | null;
  memo: string | null;
};

export type WorkRecordSummary = {
  latestRecords: WorkRecord[];
  latestByType: {
    pesticide: WorkRecord | null;
    fertilizer: WorkRecord | null;
    repot: WorkRecord | null;
  };
};

export type OrchidManagementSearchState = {
  keyword: string;
  status: string;
};

export type OrchidManagementMapProps = {
  mapData: FarmStatusMapData;
  house: House;
  initialSelectedOrchidGroupId: number | null;
  initialSelectedPhysicalBedId?: number | null;
  initialSelectedBedZoneId?: number | null;
  initialSearchFilters?: OrchidManagementSearchState;
  workTypes: WorkType[];
};

export type OrchidMutationContext = {
  selectedOrchidGroup: OrchidGroup | null;
  selectedBedZone: BedZone | null;
  resolvedZone: BedZone | null;
};
