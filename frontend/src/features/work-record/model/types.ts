import type {
  BedZone,
  OrchidGroup,
  WorkOperationStatus,
  WorkRecordTargetType,
} from "@/entities/farm/types";

export type WorkOperationFilterState = {
  from: string;
  keyword: string;
  status: WorkOperationStatus | "";
  to: string;
};

export type WorkTargetSelectionOptions = {
  orchidGroups: OrchidGroup[];
  bedZones: BedZone[];
};

export type CompletedWorkOperationPayload = {
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

export type WorkOperationFormState = {
  workTypeId: string;
  sourceScopeType: WorkOperationScopeType;
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

type WorkOperationScopeType =
  | "FARM"
  | "DERIVED_GROUP"
  | "USER_COLLECTION"
  | "MANUAL_SELECTION"
  | "INBOUND_RECORD_SELECTION";

export type InboundPottingCandidate = {
  id: number;
  varietyId: number;
  varietyName: string;
  status: string;
  estimatedQuantity: number | null;
  actualQuantity: number | null;
  tempLocation: string | null;
  pottingDueDate: string | null;
  potSize: string | null;
};

export type WorkDerivedGroupOption = {
  groupKey: string;
  varietyName: string;
  ageYear: number | null;
  potSize: string | null;
  orchidGroupCount: number;
  totalQuantity: number;
};

type WorkCollectionMemberOption = {
  orchidGroupId: number;
};

export type WorkCollectionOption = {
  id: number;
  name: string;
  status: "ACTIVE" | "ARCHIVED";
  orchidGroupCount: number;
  totalQuantity: number;
  members: WorkCollectionMemberOption[];
};

export type WorkTargetPreviewPayload = {
  scopeType: WorkOperationScopeType;
  scopeId?: number;
  scopeKey?: string;
  orchidGroupIds?: number[];
};

export type WorkTargetSelectionScope =
  | {
      type: "DERIVED_GROUP";
      scopeKey: string;
      label: string;
      memberIds: number[];
    }
  | {
      type: "USER_COLLECTION";
      collectionId: number;
      label: string;
      memberIds: number[];
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
