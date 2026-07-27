import type {
  InboundFilterState,
  MaterialFilterState,
  VarietyFilterState,
} from "./types";

export const inventoryQueryKeys = {
  all: ["inventory"] as const,
  materials: {
    all: ["inventory", "materials"] as const,
    page: (filters: MaterialFilterState, page: number, size: number) =>
      ["inventory", "materials", filters, page, size] as const,
  },
  varieties: {
    all: ["inventory", "varieties"] as const,
    page: (filters: VarietyFilterState, page: number, size: number) =>
      ["inventory", "varieties", filters, page, size] as const,
    lookup: ["inventory", "varieties", "lookup"] as const,
    groups: (varietyId: number) =>
      ["inventory", "varieties", "groups", varietyId] as const,
  },
  inbound: {
    all: ["inventory", "inbound"] as const,
    page: (filters: InboundFilterState, page: number, size: number) =>
      ["inventory", "inbound", filters, page, size] as const,
  },
  houses: ["inventory", "houses"] as const,
};
