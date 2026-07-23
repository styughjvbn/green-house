import type {
  BedZone,
  FarmStatusMapData,
  House,
  OrchidGroup,
  OrchidGroupWorkHistory,
  SelectedBedZone,
  SelectedOrchidGroup,
  WorkRecordTargetType,
  WorkOperation,
  WorkType,
  VisibleBedCount,
} from "@/entities/farm/types";

export type SelectedHouse = {
  type: "HOUSE";
  houseId: number;
};

export type SelectedPhysicalBed = {
  type: "PHYSICAL_BED";
  physicalBedId: number;
};

export type OrchidSelection =
  | SelectedHouse
  | SelectedPhysicalBed
  | SelectedBedZone
  | SelectedOrchidGroup;

export type OrchidListSelection =
  | SelectedHouse
  | SelectedPhysicalBed
  | SelectedBedZone;

export type MutationMode = "CREATE" | "EDIT" | "MOVE" | "WORK_RECORD" | null;

export type MapCellRangePick = {
  active: boolean;
  completed: boolean;
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

export type OrchidFormDraft = {
  form: OrchidFormState;
  selectedVariety: {
    id: number;
    genus: string;
    name: string;
    defaultPotSize: string | null;
    active: boolean;
  } | null;
};

export type MutationPayload = {
  bedZoneId?: number;
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

export type MultiCreateOrchidGroupRow = {
  orchidGroup: MutationPayload & { bedZoneId: number };
  collectionIds: number[];
};

export type MultiCreateWorkResult = {
  operation: { id: number; status: string; title: string };
  createdOrchidGroups: OrchidGroup[];
};

export type MultiCreateCancellationEligibility = {
  workOperationId: number;
  cancelable: boolean;
  createdOrchidGroupIds: number[];
  blockers: Array<{ code: string; message: string; count: number }>;
};

export type RepotResultOrchidGroupRow = {
  bedZoneId: number;
  quantity: number;
  potSize: string | null;
  ageYear: number | null;
  placementType: string | null;
  trayCount: number | null;
  splitPlacementAllowed: boolean;
  startPosition: number;
  endPosition: number;
  memo: string | null;
};

export type RepotWorkResult = {
  operation: { id: number; status: string; title: string };
  sourceOrchidGroup: OrchidGroup;
  resultOrchidGroups: OrchidGroup[];
  inputQuantity: number;
  lossQuantity: number;
  lossReason: string | null;
};

export type WorkOperationCorrectionAdjustment = {
  orchidGroupId: number;
  beforeQuantity: number;
  afterQuantity: number;
  beforeStatus: string;
  afterStatus: string;
};

export type WorkOperationCorrectionItem = {
  id: number;
  reason: string;
  createdAt: string;
  correctionOperation: WorkOperation;
  effectDetails: {
    adjustments?: WorkOperationCorrectionAdjustment[];
  };
};

export type WorkOperationCorrections = {
  originalOperation: WorkOperation;
  corrections: WorkOperationCorrectionItem[];
};

export type OrchidGroupLineageRelationType =
  | "CREATED_FROM_INBOUND"
  | "REPOTTED_TO"
  | "SPLIT_TO"
  | "MERGED_TO"
  | "POTTED_TO"
  | "CORRECTED_TO";

export type OrchidGroupLineageItem = {
  id: number;
  relationType: OrchidGroupLineageRelationType;
  workOperationId: number;
  sourceQuantity: number;
  resultQuantity: number;
  createdAt: string;
  sourceOrchidGroup: OrchidGroup;
  resultOrchidGroup: OrchidGroup;
};

export type OrchidGroupLineage = {
  orchidGroupId: number;
  sources: OrchidGroupLineageItem[];
  results: OrchidGroupLineageItem[];
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
  targetType: WorkRecordTargetType | "MANUAL_SELECTION";
  targetId: number | null;
  targetIds: number[];
  materialName: string;
  dilutionRatio: string;
  quantity: string;
  worker: string;
  memo: string;
};

export type WorkRecordQuickPayload = {
  workTypeId: number;
  workDate: string;
  targetType: WorkRecordTargetType | "MANUAL_SELECTION";
  targetId: number | null;
  targetIds: number[];
  materialName: string | null;
  dilutionRatio: string | null;
  quantity: string | null;
  worker: string | null;
  memo: string | null;
};

export type WorkRecordSummary = {
  latestRecords: OrchidGroupWorkHistory[];
  latestByType: {
    pesticide: OrchidGroupWorkHistory | null;
    fertilizer: OrchidGroupWorkHistory | null;
    repot: OrchidGroupWorkHistory | null;
  };
};

export type OrchidManagementSearchState = {
  keyword: string;
  status: string;
};

export type WorkHistoryPage = {
  content: OrchidGroupWorkHistory[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type OrchidGroupCollectionMember = {
  membershipId: number;
  orchidGroupId: number;
  varietyName: string;
  quantity: number;
  status: string;
  potSize: string | null;
  ageYear: number | null;
  houseNumber: number;
  physicalBedNumber: number;
  bedZoneName: string;
  joinedAt: string;
};

export type OrchidGroupCollection = {
  id: number;
  name: string;
  description: string | null;
  purpose: string | null;
  status: "ACTIVE" | "ARCHIVED";
  orchidGroupCount: number;
  totalQuantity: number;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  members: OrchidGroupCollectionMember[];
};

export type DerivedOrchidGroup = {
  groupKey: string;
  varietyId: number;
  varietyName: string;
  genus: string | null;
  ageYear: number | null;
  potSizeCode: OrchidGroup["potSizeCode"];
  potSize: string | null;
  orchidGroupCount: number;
  totalQuantity: number;
  locationCount: number;
};

export type OrchidManagementMapProps = {
  mapData: FarmStatusMapData;
  house: House;
  initialStartBedId: number | null;
  initialVisibleBedCount: VisibleBedCount;
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
