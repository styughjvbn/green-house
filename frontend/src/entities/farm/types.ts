export type BedZoneSide = "LEFT" | "RIGHT" | "CUSTOM" | "HANGING";

export type BedZoneType = "DEFAULT" | "CUSTOM" | "HANGING" | "TRAY" | "GRID";

export type PlacementCapacityMode =
  | "SPACIOUS"
  | "STANDARD"
  | "EXPANDED"
  | "COMPRESSED"
  | "TEMPORARY";

export type OrchidGroup = {
  id: number;
  bedZoneId: number;
  varietyId: number | null;
  genus: string | null;
  varietyName: string;
  quantity: number;
  potSize: string | null;
  potSizeCode:
    | "UNSPECIFIED"
    | "UNMAPPED"
    | "POT_2"
    | "POT_2_5"
    | "POT_3"
    | "POT_3_5"
    | "POT_4"
    | "POT_4_5"
    | "POT_5"
    | "POT_6"
    | "HANGING"
    | "ETC";
  ageYear: number | null;
  status: string;
  placementType: string | null;
  trayCount: number | null;
  splitPlacementAllowed: boolean;
  startPosition: number | null;
  endPosition: number | null;
  sortOrder: number;
  memo: string | null;
  houseId: number;
  houseNumber: number;
  physicalBedNumber: number;
  bedZoneName: string;
};

export type VarietyOption = {
  id: number;
  genus: string;
  name: string;
  defaultPotSize: string | null;
  active: boolean;
};

export type BedZoneCapacity = {
  id: number | null;
  placementType: string;
  potSize: string | null;
  capacityMode: PlacementCapacityMode;
  capacityValue: number;
  unitSpan: number;
  allowed: boolean;
  memo: string | null;
};

export type BedZonePlacementProfile = {
  bedZoneId: number;
  bedZoneName: string;
  houseNumber: number;
  physicalBedNumber: number;
  positionUnitCount: number | null;
  positionUnitLabel: string | null;
  capacities: BedZoneCapacity[];
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
  positionUnitCount: number | null;
  positionUnitLabel: string | null;
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

export type OrchidManagementViewMode = "REAL_DIRECTION" | "ROTATED" | "BY_BED";

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
  physicalBeds: PhysicalBed[];
};

export type FarmStatusMapData = {
  houses: HouseStatusSummary[];
};

export type VisibleBedCount = 2 | 3 | 4;

export type OrchidManagementViewport = {
  startBedId: number | null;
  bedCount: VisibleBedCount;
  beds: PhysicalBed[];
  hasPrevious: boolean;
  hasNext: boolean;
  summary: {
    orchidGroupCount: number;
    totalQuantity: number;
    abnormalCount: number;
    bedZoneCount: number;
  };
  bedOrder: Array<{
    id: number;
    houseId: number;
    houseNumber: number;
    number: number;
  }>;
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

export type SelectedBedZone = {
  type: "BED_ZONE";
  bedZoneId: number;
};

export type SelectedOrchidGroup = {
  type: "ORCHID_GROUP";
  orchidGroupId: number;
};

export type WorkRecordTargetType =
  | "FARM"
  | "HOUSE"
  | "PHYSICAL_BED"
  | "BED_ZONE"
  | "ORCHID_GROUP";

export type WorkTypeTemplate =
  | "PESTICIDE"
  | "FERTILIZER"
  | "REPOT"
  | "CLEANUP"
  | "DISCARD"
  | "STATUS"
  | "MEMO"
  | "MOVEMENT"
  | "MULTI_CREATE"
  | "CORRECTION";

export type WorkType = {
  id: number;
  code: string;
  name: string;
  template: WorkTypeTemplate;
  defaultType: boolean;
  systemType: boolean;
  active: boolean;
  sortOrder: number;
};

export type WorkRecord = {
  id: number;
  workTypeId: number | null;
  workType: string;
  workTypeTemplate: WorkTypeTemplate | null;
  workDate: string;
  targetType: WorkRecordTargetType;
  targetId: number | null;
  materialName: string | null;
  dilutionRatio: string | null;
  quantity: string | null;
  worker: string | null;
  fromBedZoneId: number | null;
  toBedZoneId: number | null;
  details: Record<string, unknown> | null;
  memo: string | null;
  status: "ACTIVE" | "CANCELED";
  canceledAt: string | null;
  cancelReason: string | null;
};

export type WorkOperationStatus =
  | "PLANNED"
  | "IN_PROGRESS"
  | "PAUSED"
  | "COMPLETED"
  | "CANCELED"
  | "CORRECTED";

export type WorkTargetExecutionStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "PARTIALLY_COMPLETED"
  | "COMPLETED"
  | "SKIPPED"
  | "CANCELED"
  | "FAILED";

export type WorkLocationSnapshot = {
  houseId: number;
  houseNumber: number;
  physicalBedId: number;
  physicalBedNumber: number;
  bedZoneId: number;
  bedZoneName: string;
  tempLocation?: string | null;
  pottingDueDate?: string | null;
};

export type WorkOperationTarget = {
  id: number | null;
  targetReferenceType: "ORCHID_GROUP" | "INBOUND_RECORD";
  orchidGroupId: number | null;
  inboundRecordId: number | null;
  inclusionSource:
    | "DIRECT"
    | "FARM"
    | "HOUSE"
    | "PHYSICAL_BED"
    | "BED_ZONE"
    | "DERIVED_GROUP"
    | "USER_COLLECTION"
    | "MANUAL_ADDITION"
    | "INBOUND_RECORD"
    | "LINEAGE"
    | null;
  varietyName: string;
  quantitySnapshot: number;
  ageYearSnapshot: number | null;
  potSizeCodeSnapshot: string | null;
  potSizeSnapshot: string | null;
  locationSnapshot: WorkLocationSnapshot;
  processedQuantity: number;
  remainingQuantity: number;
  executionStatus: WorkTargetExecutionStatus;
  startedAt: string | null;
  completedAt: string | null;
  effectAppliedAt: string | null;
  worker: string | null;
  resultDetails: Record<string, unknown> | null;
};

export type WorkTargetPreview = {
  orchidGroupCount: number;
  totalQuantity: number;
  targets: WorkOperationTarget[];
};

export type WorkOperation = {
  id: number;
  workTypeId: number;
  workTypeCode: string;
  workType: string;
  workTypeTemplate: WorkTypeTemplate;
  title: string;
  status: WorkOperationStatus;
  plannedStartDate: string;
  plannedEndDate: string | null;
  actualStartAt: string | null;
  actualEndAt: string | null;
  sourceScopeType:
    | WorkRecordTargetType
    | "NONE"
    | "DERIVED_GROUP"
    | "USER_COLLECTION"
    | "MANUAL_SELECTION"
    | "INBOUND_RECORD_SELECTION";
  sourceScopeId: number | null;
  sourceConditionSnapshot: Record<string, unknown>;
  targetSnapshotAt: string;
  details: Record<string, unknown> | null;
  worker: string | null;
  memo: string | null;
  progress: {
    total: number;
    pending: number;
    inProgress: number;
    partial: number;
    completed: number;
    skipped: number;
    canceled: number;
    failed: number;
    progressPercent: number;
  };
  targets: WorkOperationTarget[];
};

export type OrchidGroupWorkHistory = {
  sourceKind: "WORK_OPERATION" | "WORK_OPERATION_EFFECT" | "LEGACY_WORK_RECORD";
  workOperationId: number | null;
  legacyWorkRecordId: number | null;
  workTypeId: number | null;
  workType: string;
  title: string;
  workDate: string;
  status: string;
  propagated: boolean;
  sourceScopeType:
    | WorkRecordTargetType
    | "NONE"
    | "DERIVED_GROUP"
    | "USER_COLLECTION"
    | "MANUAL_SELECTION";
  sourceScopeId: number | null;
  locationSnapshot: WorkLocationSnapshot | null;
  currentLocation: WorkLocationSnapshot;
  worker: string | null;
  memo: string | null;
};

export type PartnerType = "WHOLESALE" | "RETAIL" | "AUCTION_HOUSE";

export type BusinessPartner = {
  id: number;
  name: string;
  partnerType: PartnerType;
  ownerName: string | null;
  phone: string | null;
  address: string | null;
  memo: string | null;
  active: boolean;
};

export type SettlementUnit = "SALES_SLIP" | "MONTHLY_BATCH" | "AUCTION_DATE";
export type PaymentDayMode = "CALENDAR_DAY" | "BUSINESS_DAY";

export type PartnerSettlementSettings = {
  id: number;
  partnerId: number;
  settlementUnit: SettlementUnit;
  paymentDelayDays: number;
  paymentDayMode: PaymentDayMode;
  autoMatchEnabled: boolean;
  autoSettleEnabled: boolean;
  amountTolerance: number;
  depositorAliases: string[];
  allowPrepayment: boolean;
  creditAutoApplyEnabled: boolean;
  ruleJson: Record<string, unknown> | null;
  memo: string | null;
};

export type SalesSlipItem = {
  id: number;
  auctionShipmentLotId: number | null;
  itemName: string;
  genus: string | null;
  spec: string | null;
  quantity: number;
  unitPrice: number;
  amount: number;
  memo: string | null;
  allocations: SalesSlipItemAllocation[];
};

export type SalesSlipItemAllocation = {
  id: number;
  orchidGroupId: number;
  varietyName: string;
  allocatedQuantity: number;
  availableQuantity: number;
  houseNumber: number;
  physicalBedNumber: number;
  bedZoneName: string;
};

export type SalesOrchidGroupOption = {
  id: number;
  varietyId: number | null;
  varietyName: string;
  genus: string;
  status: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  potSize: string | null;
  ageYear: number | null;
  houseNumber: number;
  physicalBedNumber: number;
  bedZoneName: string;
};

export type SalesSlip = {
  id: number;
  slipNumber: string;
  saleDate: string;
  salesType: "DIRECT" | "AUCTION";
  auctionShipmentId: number | null;
  auctionMarket: string | null;
  partner: BusinessPartner;
  totalAmount: number;
  expectedPaymentDate: string;
  paidAmount: number;
  remainingAmount: number;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string | null;
  memo: string | null;
  items: SalesSlipItem[];
};

export type AuctionShipmentOption = {
  id: number;
  shipmentDate: string;
  auctionHouseId: number;
  auctionMarket: string;
  lots: Array<{
    id: number;
    itemName: string;
    varietyName: string;
    shipmentGrade: string | null;
    shippedQuantity: number;
  }>;
};

export type AuctionLotStatus =
  | "SHIPPED"
  | "WAITING"
  | "IN_PROGRESS"
  | "SOLD"
  | "PARTIALLY_SOLD"
  | "FAILED"
  | "REAUCTION_WAITING"
  | "RETURN_INFERRED"
  | "PARTIALLY_RETURNED"
  | "RETURNED"
  | "QUANTITY_MISMATCH"
  | "REVIEW_REQUIRED"
  | "CANCELLED";

export type AuctionInspectionStatus =
  | "NORMAL"
  | "AUTO_MATCHED"
  | "CORRECTED_MATCH"
  | "MANUAL_REVIEW"
  | "MATCH_FAILED"
  | "QUANTITY_MISMATCH"
  | "RETURN_INFERRED"
  | "SOURCE_ERROR";

export type AuctionResultLine = {
  id: number;
  auctionDate: string;
  auctionGrade: string | null;
  quantity: number;
  unitPrice: number;
  amount: number;
  note: string | null;
  inspectionStatus: AuctionInspectionStatus;
};

export type AuctionAttemptStatus =
  | "SOLD"
  | "FAILED"
  | "PARTIALLY_SOLD"
  | "RETURN_INFERRED";

export type AuctionAttempt = {
  id: number;
  auctionDate: string;
  attemptNo: number;
  attemptStatus: AuctionAttemptStatus;
  failedReason: string | null;
  memo: string | null;
  resultLines: AuctionResultLine[];
};

export type AuctionStatusHistory = {
  id: number;
  previousStatus: AuctionLotStatus | null;
  newStatus: AuctionLotStatus;
  changedAt: string;
  reason: string;
  worker: string | null;
  memo: string | null;
};

export type AuctionLot = {
  id: number;
  shipmentDate: string;
  auctionMarket: string;
  itemName: string;
  varietyName: string;
  shipmentGrade: string | null;
  boxes: number | null;
  shippedQuantity: number;
  soldQuantity: number;
  waitingQuantity: number;
  returnedQuantity: number;
  returnConfirmableQuantity: number;
  returnConfirmedDate: string | null;
  currentStatus: AuctionLotStatus;
  latestAuctionDate: string | null;
  failedCount: number;
  totalAmount: number;
  inspectionStatus: AuctionInspectionStatus;
  memo: string | null;
  attempts: AuctionAttempt[];
  statusHistory: AuctionStatusHistory[];
};

export type AuctionLotPage = {
  content: AuctionLot[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AuctionTrackingSummary = {
  lotCount: number;
  shippedQuantity: number;
  soldQuantity: number;
  waitingQuantity: number;
  returnedQuantity: number;
  reviewRequiredCount: number;
  totalAmount: number;
};

export type AuctionSettlementStatus =
  | "CREATED"
  | "PAYMENT_WAITING"
  | "PARTIALLY_PAID"
  | "PAID"
  | "AMOUNT_MISMATCH"
  | "REVIEW_REQUIRED"
  | "CANCELLED";

export type AuctionSettlementLine = {
  id: number;
  auctionResultLineId: number;
  auctionShipmentLotId: number;
  shipmentDate: string;
  varietyName: string;
  shipmentGrade: string | null;
  quantity: number;
  unitPrice: number;
  amount: number;
  status: "UNPAID" | "PAID" | "PARTIALLY_PAID" | "EXCLUDED" | "REVIEW_REQUIRED";
};

export type AuctionSettlement = {
  id: number;
  auctionHouseId: number;
  auctionHouseName: string;
  auctionDate: string;
  resultReceivedAt: string | null;
  expectedPaymentDate: string | null;
  grossAmount: number;
  feeAmount: number;
  deductionAmount: number;
  expectedDepositAmount: number;
  paidAmount: number;
  remainingAmount: number;
  status: AuctionSettlementStatus;
  memo: string | null;
  confirmedAt: string | null;
  confirmedBy: string | null;
  lines: AuctionSettlementLine[];
};

export type PaymentTargetType = "SALES_SLIP" | "AUCTION_SETTLEMENT" | "NONE";

export type PartnerPaymentEvent = {
  id: number;
  partnerId: number;
  partnerName: string;
  eventType:
    | "PAYMENT_RECEIVED"
    | "PAYMENT_ALLOCATED"
    | "PREPAYMENT_RECEIVED"
    | "CREDIT_APPLIED"
    | "CREDIT_REFUND"
    | "AUTO_MATCH_CANDIDATE"
    | "AUTO_MATCH_CONFIRMED"
    | "MANUAL_MATCH_CONFIRMED"
    | "MATCH_REJECTED"
    | "PAYMENT_UNLINKED"
    | "ADJUSTMENT";
  eventDate: string;
  amount: number;
  unappliedAmount: number;
  targetType: PaymentTargetType;
  targetId: number;
  parentEventId: number | null;
  paymentMethod: string | null;
  depositorName: string | null;
  description: string | null;
  status: string;
  memo: string | null;
  createdBy: string | null;
};

export type PartnerBalanceSummary = {
  partnerId: number;
  partnerName: string;
  creditBalance: number;
  unappliedPaymentAmount: number;
  receivableBalance: number;
};
