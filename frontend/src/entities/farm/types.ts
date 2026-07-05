export type BedZoneSide = "LEFT" | "RIGHT" | "CUSTOM" | "HANGING";

export type BedZoneType = "DEFAULT" | "CUSTOM" | "HANGING" | "TRAY" | "GRID";

export type PlacementCapacityMode =
  | "SPACIOUS"
  | "STANDARD"
  | "EXPANDED"
  | "COMPRESSED"
  | "TEMPORARY";

export type BedZoneSegmentType = "START" | "MIDDLE" | "END" | "CUSTOM";

export type OrchidGroupSegmentPlacement = {
  id: number;
  segmentId: number;
  segmentName: string;
  quantity: number;
  trayCount: number | null;
  placementMode: PlacementCapacityMode;
  reorganizeDueDate: string | null;
  memo: string | null;
};

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
  splitPlacementAllowed: boolean;
  segmentPlacements: OrchidGroupSegmentPlacement[];
  sortOrder: number;
  memo: string | null;
  houseNumber: number;
  physicalBedNumber: number;
  bedZoneName: string;
};

export type BedZoneSegmentCapacity = {
  id: number | null;
  placementType: string;
  potSize: string | null;
  capacityMode: PlacementCapacityMode;
  capacityValue: number;
  allowed: boolean;
  memo: string | null;
};

export type BedZoneSegment = {
  id: number | null;
  name: string;
  segmentType: BedZoneSegmentType;
  sortOrder: number;
  memo: string | null;
  capacities: BedZoneSegmentCapacity[];
};

export type BedZonePlacementProfile = {
  bedZoneId: number;
  bedZoneName: string;
  houseNumber: number;
  physicalBedNumber: number;
  hasUnassignedGroups: boolean;
  segments: BedZoneSegment[];
};

export type PlacementRecommendationStatus =
  | "RECOMMENDED"
  | "POSSIBLE"
  | "WARNING"
  | "UNAVAILABLE";

export type PlacementRecommendationAllocation = {
  segmentId: number;
  segmentName: string;
  quantity: number;
  occupancyUnits: number;
  remainingUnits: number;
};

export type PlacementRecommendationCandidate = {
  bedZoneId: number;
  bedZoneName: string;
  houseId: number;
  houseNumber: number;
  physicalBedId: number;
  physicalBedNumber: number;
  status: PlacementRecommendationStatus;
  requiredMode: PlacementCapacityMode | null;
  allocations: PlacementRecommendationAllocation[];
  warnings: string[];
};

export type PlacementRecommendation = {
  orchidGroupId: number;
  varietyName: string;
  requirement: {
    placementType: string;
    potSize: string | null;
    quantity: number;
    occupancyUnits: number;
    splitAllowed: boolean;
  };
  candidates: PlacementRecommendationCandidate[];
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
  | "STATUS"
  | "MEMO"
  | "MOVEMENT";

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
  memo: string | null;
};

export type Customer = {
  id: number;
  name: string;
  ownerName: string | null;
  phone: string | null;
  address: string | null;
  memo: string | null;
};

export type SalesSlipItem = {
  id: number;
  orchidGroupId: number | null;
  auctionShipmentLotId: number | null;
  itemName: string;
  genus: string | null;
  spec: string | null;
  quantity: number;
  unitPrice: number;
  amount: number;
  memo: string | null;
};

export type SalesSlip = {
  id: number;
  slipNumber: string;
  saleDate: string;
  salesType: "DIRECT" | "AUCTION";
  auctionShipmentId: number | null;
  auctionMarket: string | null;
  customer: Customer;
  totalAmount: number;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string | null;
  memo: string | null;
  items: SalesSlipItem[];
};

export type AuctionShipmentOption = {
  id: number;
  shipmentDate: string;
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

export type AuctionAttempt = {
  id: number;
  auctionDate: string;
  attemptNo: number;
  attemptStatus: string;
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
