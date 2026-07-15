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

export type WorkTargetSelectionOptions = {
  orchidGroups: OrchidGroup[];
  bedZones: BedZone[];
};

export type CreateWorkRecordPayload = {
  workTypeId: number;
  workDate: string;
  targetType: WorkRecordTargetType;
  targetId: number | null;
  orchidGroupIds: number[];
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
  sourceScopeType: WorkOperationScopeType;
  houseId: string;
  scopeKey: string;
  collectionId: string;
  title: string;
  plannedStartDate: string;
  plannedEndDate: string;
  materialName: string;
  dilutionRatio: string;
  quantity: string;
  worker: string;
  memo: string;
};

export type WorkOperationScopeType =
  | "HOUSE"
  | "DERIVED_GROUP"
  | "USER_COLLECTION"
  | "MANUAL_SELECTION";

export type WorkDerivedGroupOption = {
  groupKey: string;
  varietyName: string;
  ageYear: number | null;
  potSize: string | null;
  orchidGroupCount: number;
  totalQuantity: number;
};

export type WorkCollectionOption = {
  id: number;
  name: string;
  orchidGroupCount: number;
  totalQuantity: number;
};

export type WorkOperationScopeOptions = {
  derivedGroups: WorkDerivedGroupOption[];
  collections: WorkCollectionOption[];
  orchidGroups: OrchidGroup[];
};

export type WorkTargetPreviewPayload = {
  scopeType: WorkOperationScopeType;
  scopeId?: number;
  scopeKey?: string;
  orchidGroupIds?: number[];
};

export type CreateWorkOperationPayload = {
  workTypeId: number;
  title: string;
  plannedStartDate: string;
  plannedEndDate: string | null;
  sourceScopeType: WorkOperationScopeType;
  sourceScopeId?: number;
  sourceScopeKey?: string;
  sourceOrchidGroupIds?: number[];
  details: Record<string, unknown>;
  worker: string | null;
  memo: string | null;
  excludedOrchidGroupIds: number[];
};

export type WorkOperationUiState = {
  operation: WorkOperation | null;
  preview: WorkTargetPreview | null;
};
