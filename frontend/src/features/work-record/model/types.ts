import type {
  BedZone,
  HouseStatusSummary,
  OrchidGroup,
  PhysicalBed,
  WorkRecord,
  WorkOperation,
  WorkTargetPreview,
  WorkRecordTargetType,
  WorkType,
} from "@/entities/farm/types";

export type WorkRecordManagerProps = {
  initialRecords: WorkRecord[];
  houses: HouseStatusSummary[];
  workTypes: WorkType[];
};

export type WorkRecordFormState = {
  workTypeId: string;
  workDate: string;
  targetType: WorkRecordTargetType;
  houseId: string;
  physicalBedId: string;
  bedZoneId: string;
  orchidGroupId: string;
  materialName: string;
  dilutionRatio: string;
  quantity: string;
  worker: string;
  memo: string;
};

export type WorkRecordTargetOptions = {
  physicalBeds: PhysicalBed[];
  bedZones: BedZone[];
  orchidGroups: OrchidGroup[];
};

export type CreateWorkRecordPayload = {
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

export type WorkRecordFilterState = {
  targetType: "" | WorkRecordTargetType;
  workType: string;
  from: string;
  to: string;
  worker: string;
  keyword: string;
};

export type WorkOperationFormState = {
  houseId: string;
  title: string;
  plannedStartDate: string;
  plannedEndDate: string;
  materialName: string;
  dilutionRatio: string;
  quantity: string;
  worker: string;
  memo: string;
};

export type WorkTargetPreviewPayload = {
  scopeType: "HOUSE";
  scopeId: number;
};

export type CreateWorkOperationPayload = {
  workTypeId: number;
  title: string;
  plannedStartDate: string;
  plannedEndDate: string | null;
  sourceScopeType: "HOUSE";
  sourceScopeId: number;
  details: Record<string, unknown>;
  worker: string | null;
  memo: string | null;
  excludedOrchidGroupIds: number[];
};

export type WorkOperationUiState = {
  operation: WorkOperation | null;
  preview: WorkTargetPreview | null;
};
