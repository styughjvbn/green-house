export type InventoryStatus = "ACTIVE" | "INACTIVE";

export interface Variety {
  id: number;
  code: string;
  genus: string;
  name: string;
  potSize: string;
  saleEnabled: boolean;
  status: InventoryStatus;
  description: string;
  memo: string;
  registeredAt: string;
  updatedAt: string;
  connectedGroups: ConnectedOrchidGroup[];
}

export interface ConnectedOrchidGroup {
  id: number;
  location: string;
  quantity: number;
  status: "정상" | "주의" | "이상";
  latestWork: string;
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
}
