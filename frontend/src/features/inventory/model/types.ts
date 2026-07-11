export type InventoryStatus = "ACTIVE" | "INACTIVE";

export interface InventoryPageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Variety {
  id: number;
  code: string;
  genus: string;
  name: string;
  alias: string;
  potSize: string;
  saleEnabled: boolean;
  status: InventoryStatus;
  description: string;
  memo: string;
  registeredAt: string;
  updatedAt: string;
  connectedGroupCount: number;
  totalQuantity: number;
  saleableQuantity: number;
  recentInboundDate: string | null;
  recentWorkDate: string | null;
  connectedGroups: ConnectedOrchidGroup[];
}

export interface ConnectedOrchidGroup {
  id: number;
  location: string;
  quantity: number;
  status: "정상" | "주의" | "이상";
  latestWork: string | null;
}

export interface VarietyPayload {
  genus: string;
  name: string;
  alias: string;
  defaultPotSize: string;
  saleEnabled: boolean;
  description: string;
  memo: string;
}

export interface MaterialPayload {
  category: "농약" | "비료" | "자재";
  name: string;
  manufacturer: string;
  specification: string;
  stockQuantity: string;
  storageLocation: string;
  usage: string;
}

export type InboundType =
  | "FLASK_SEEDLING"
  | "POTTED_SEEDLING"
  | "PRODUCT_POT"
  | "SAMPLE"
  | "ETC";

export type InboundStatus =
  | "TEMP_STORED"
  | "POTTING_PENDING"
  | "POTTED"
  | "PLACED"
  | "CANCELED";

export interface InboundRecord {
  id: number;
  inboundDate: string;
  inboundType: InboundType;
  varietyId: number;
  genus: string;
  varietyName: string;
  status: InboundStatus;
  bottleCount: number | null;
  estimatedQuantity: number | null;
  actualQuantity: number | null;
  tempLocation: string | null;
  pottingDueDate: string | null;
  pottingDate: string | null;
  potSize: string | null;
  ageYear: number | null;
  growthStage: string | null;
  placementType: string | null;
  trayCount: number | null;
  bedZoneId: number | null;
  currentLocation: string | null;
  createdOrchidGroupId: number | null;
  worker: string | null;
  memo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InboundRecordPayload {
  inboundDate: string;
  inboundType: InboundType;
  varietyId?: number;
  newVariety?: {
    genus: string;
    name: string;
    defaultPotSize: string;
    memo: string;
  };
  bottleCount?: number;
  estimatedQuantity?: number;
  actualQuantity?: number;
  tempLocation?: string;
  pottingDueDate?: string;
  potSize?: string;
  ageYear?: number;
  growthStage?: string;
  placementType?: string;
  trayCount?: number;
  bedZoneId?: number;
  startPosition?: number;
  endPosition?: number;
  status?: InboundStatus;
  worker?: string;
  memo?: string;
}

export interface InboundRecordUpdatePayload {
  inboundDate: string;
  bottleCount?: number;
  estimatedQuantity?: number;
  actualQuantity?: number;
  tempLocation?: string;
  pottingDueDate?: string;
  potSize?: string;
  ageYear?: number;
  growthStage?: string;
  placementType?: string;
  trayCount?: number;
  worker?: string;
  memo?: string;
}

export interface InboundPottingPayload {
  pottingDate: string;
  actualQuantity: number;
  potSize?: string;
  ageYear?: number;
  growthStage?: string;
  placementType?: string;
  trayCount?: number;
  bedZoneId: number;
  startPosition?: number;
  endPosition?: number;
  worker?: string;
  memo?: string;
}

export interface Material {
  id: number;
  code: string;
  category: "농약" | "비료" | "자재";
  name: string;
  manufacturer: string;
  specification: string;
  stockQuantity: string;
  storageLocation: string;
  usage: string;
  status: InventoryStatus;
  registeredAt: string;
  updatedAt: string;
}
