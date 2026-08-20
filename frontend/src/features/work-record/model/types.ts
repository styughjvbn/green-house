import type {
  BedZone,
  OrchidGroup,
  WorkOperationStatus,
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
  derivedGroupKey: string;
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

export type WorkOperationDetailField = {
  key: string;
  label: string;
  value: string;
};

export type WorkExecutionSource = {
  orchidGroupId: number;
  inputQuantity: number | null;
  beforeQuantity: number | null;
  afterQuantity: number | null;
  remainingQuantity: number | null;
  beforeStatus: string | null;
  afterStatus: string | null;
  fromBedZoneId: number | null;
  releasedStartPosition: number | null;
  releasedEndPosition: number | null;
};

export type WorkExecutionResult = {
  orchidGroupId: number | null;
  quantity: number | null;
  purpose: string | null;
  bedZoneId: number | null;
  startPosition: number | null;
  endPosition: number | null;
  potSize: string | null;
  ageYear: number | null;
  placementType: string | null;
  trayCount: number | null;
  memo: string | null;
  varietyName: string | null;
  location: {
    houseNumber: number;
    physicalBedNumber: number;
    bedZoneName: string;
  } | null;
};

export type WorkExecutionDetail = {
  id: number;
  executionKey: string;
  resultType: string;
  appliedAt: string;
  canceledAt: string | null;
  worker: string | null;
  targetId: number | null;
  inboundRecordId: number | null;
  sources: WorkExecutionSource[];
  results: WorkExecutionResult[];
  lossQuantity: number | null;
  actualQuantity: number | null;
  reason: string | null;
  linkedWorkOperationId: number | null;
};

export type WorkCorrectionDetail = {
  id: number;
  workOperationId: number;
  title: string;
  workDate: string;
  createdAt: string;
  worker: string | null;
  reason: string;
  adjustments: Array<{
    orchidGroupId: number;
    beforeQuantity: number;
    afterQuantity: number;
    beforeStatus: string;
    afterStatus: string;
  }>;
};

export type WorkOperationDetail = {
  summary: Pick<
    import("@/entities/farm/types").WorkOperation,
    | "id"
    | "workTypeId"
    | "workTypeCode"
    | "workType"
    | "title"
    | "status"
    | "plannedStartDate"
    | "plannedEndDate"
    | "actualStartAt"
    | "actualEndAt"
    | "worker"
    | "memo"
  >;
  fields: WorkOperationDetailField[];
  executions: WorkExecutionDetail[];
  corrections: WorkCorrectionDetail[];
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

export type WorkTargetSourcePayload =
  | { sourceScopeType: "FARM" }
  | {
      sourceScopeType: "DERIVED_GROUP";
      sourceDerivedGroupKey: string;
    }
  | {
      sourceScopeType: "USER_COLLECTION";
      sourceScopeId: number;
    }
  | {
      sourceScopeType: "MANUAL_SELECTION";
      sourceOrchidGroupIds: number[];
    };

export type WorkTargetPreviewPayload = WorkTargetSourcePayload;

export type WorkTargetGroupChoice =
  | {
      type: "DERIVED_GROUP";
      derivedGroupKey: string;
      label: string;
      memberIds: number[];
    }
  | {
      type: "USER_COLLECTION";
      collectionId: number;
      label: string;
      memberIds: number[];
    };

export type CreateWorkOperationPayload = WorkTargetSourcePayload & {
  workTypeId: number;
  title: string;
  plannedStartDate: string;
  plannedEndDate: string | null;
  details: Record<string, unknown>;
  worker: string | null;
  memo: string | null;
  excludedOrchidGroupIds: number[];
};
