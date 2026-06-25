import type {
  BedZone,
  FarmStatusMapData,
  House,
  OrchidGroup,
  SelectedBedZone,
  SelectedOrchidGroup,
  WorkRecord,
  WorkRecordTargetType,
} from "@/entities/farm/types";

export type OrchidSelection = SelectedBedZone | SelectedOrchidGroup;

export type MutationMode = "CREATE" | "EDIT" | "MOVE" | "WORK_RECORD" | null;

export type DragState = {
  orchidGroupId: number;
  overBedZoneId: number | null;
} | null;

export type OrchidFormState = {
  genus: string;
  varietyName: string;
  quantity: string;
  potSize: string;
  ageYear: string;
  status: string;
  placementType: string;
  trayCount: string;
  memo: string;
};

export type MutationPayload = {
  genus: string | null;
  varietyName: string;
  quantity: number;
  potSize: string | null;
  ageYear: number | null;
  status: string;
  placementType: string | null;
  trayCount: number | null;
  memo: string | null;
};

export type WorkRecordQuickFormState = {
  workType: string;
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
  workType: string;
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

export type OrchidManagementMapProps = {
  mapData: FarmStatusMapData;
  house: House;
  workTypes: string[];
};

export type OrchidMutationContext = {
  selectedOrchidGroup: OrchidGroup | null;
  selectedBedZone: BedZone | null;
  resolvedZone: BedZone | null;
};
