import type {
  AuctionLotPage,
  AuctionTrackingSummary,
  AuctionSettlement,
  BusinessPartner,
  PartnerType,
  SalesSlip,
} from "@/entities/farm/types";

export type SalesManagerProps = {
  initialBusinessPartners: BusinessPartner[];
  initialSalesSlips: SalesSlip[];
  initialAuctionPage: AuctionLotPage;
  initialAuctionSummary: AuctionTrackingSummary;
  initialAuctionSettlements: AuctionSettlement[];
};

export type BusinessPartnerForm = {
  name: string;
  partnerType: PartnerType;
  ownerName: string;
  phone: string;
  address: string;
  memo: string;
};

export type SalesAllocationForm = {
  orchidGroupId: string;
  varietyName: string;
  genus: string;
  locationLabel: string;
  availableQuantity: number;
  quantity: string;
};

export type SalesItemForm = {
  itemName: string;
  genus: string;
  spec: string;
  quantity: string;
  unitPrice: string;
  memo: string;
  allocations: SalesAllocationForm[];
};

export type SalesSlipForm = {
  salesType: "DIRECT" | "AUCTION";
  saleDate: string;
  partnerId: string;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string;
  memo: string;
  items: SalesItemForm[];
};

export type CreateBusinessPartnerPayload = {
  name: string;
  partnerType: PartnerType;
  ownerName: string | null;
  phone: string | null;
  address: string | null;
  memo: string | null;
};

export type CreateSalesSlipPayload = {
  salesType: "DIRECT" | "AUCTION";
  saleDate: string;
  partnerId: number | null;
  auctionShipmentId: number | null;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string | null;
  memo: string | null;
  items: Array<{
    itemName: string;
    genus: string | null;
    spec: string | null;
    quantity: number;
    unitPrice: number;
    memo: string | null;
    allocations: Array<{
      orchidGroupId: number;
      quantity: number;
    }>;
  }>;
};

export type SalesTab = "SLIPS" | "AUCTION" | "SETTLEMENT" | "PARTNERS";

export type AuctionFilterState = {
  from: string;
  to: string;
  market: string;
  variety: string;
  grade: string;
  status: string;
  keyword: string;
  reviewOnly: boolean;
  returnOnly: boolean;
  waitingOnly: boolean;
};

export type SalesFilterState = {
  from: string;
  to: string;
  partnerId: string;
  paymentStatus: string;
  salesStatus: string;
  keyword: string;
};

export type BusinessPartnerFilterState = {
  partnerType: string;
  active: string;
  keyword: string;
};
