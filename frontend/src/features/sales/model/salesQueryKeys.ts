import type {
  AuctionFilterState,
  BusinessPartnerFilterState,
  SalesFilterState,
} from "./types";

export const salesQueryKeys = {
  all: ["sales"] as const,
  slips: {
    all: ["sales", "slips"] as const,
    pages: ["sales", "slips", "pages"] as const,
    page: (filters: SalesFilterState, page: number, size: number) =>
      ["sales", "slips", "pages", filters, page, size] as const,
    detail: (salesSlipId: number) =>
      ["sales", "slips", "detail", salesSlipId] as const,
  },
  partners: {
    all: ["sales", "businessPartners"] as const,
    pages: ["sales", "businessPartners", "pages"] as const,
    page: (filters: BusinessPartnerFilterState, page: number, size: number) =>
      ["sales", "businessPartners", "pages", filters, page, size] as const,
    lookup: ["sales", "businessPartners", "lookup"] as const,
  },
  auction: {
    all: ["sales", "auctionTracking"] as const,
    lots: (filters: AuctionFilterState, page: number, size: number) =>
      ["sales", "auctionTracking", "lots", filters, page, size] as const,
    summary: ["sales", "auctionTracking", "summary"] as const,
    settlements: ["sales", "auctionSettlements"] as const,
  },
};
