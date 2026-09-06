import type { PaymentTargetType } from "@/entities/farm/types";
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
  payments: {
    target: (targetType: PaymentTargetType, targetId: number) =>
      ["sales", "paymentEvents", targetType, targetId] as const,
    receivedPage: (
      targetType: PaymentTargetType,
      targetId: number,
      page: number,
      size: number,
    ) =>
      [
        "sales",
        "paymentEvents",
        targetType,
        targetId,
        "PAYMENT_RECEIVED",
        page,
        size,
      ] as const,
  },
  auction: {
    all: ["sales", "auctionTracking"] as const,
    lots: (filters: AuctionFilterState, page: number, size: number) =>
      ["sales", "auctionTracking", "lots", filters, page, size] as const,
    summary: ["sales", "auctionTracking", "summary"] as const,
    settlementPages: ["sales", "auctionSettlements", "pages"] as const,
    settlementPage: (page: number, size: number) =>
      ["sales", "auctionSettlements", "pages", page, size] as const,
    settlementSummary: ["sales", "auctionSettlements", "summary"] as const,
    settlementDetail: (id: number) =>
      ["sales", "auctionSettlements", "detail", id] as const,
  },
};
