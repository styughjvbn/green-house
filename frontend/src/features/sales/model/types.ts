import type { Customer, SalesSlip } from "@/entities/farm/types";

export type SalesManagerProps = {
  initialCustomers: Customer[];
  initialSalesSlips: SalesSlip[];
  initialAuctionPage: import("@/entities/farm/types").AuctionLotPage;
  initialAuctionSummary: import("@/entities/farm/types").AuctionTrackingSummary;
};

export type CustomerForm = {
  name: string;
  ownerName: string;
  phone: string;
  address: string;
  memo: string;
};

export type SalesItemForm = {
  itemName: string;
  genus: string;
  spec: string;
  quantity: string;
  unitPrice: string;
  memo: string;
};

export type SalesSlipForm = {
  salesType: "DIRECT" | "AUCTION";
  saleDate: string;
  customerId: string;
  auctionShipmentId: string;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string;
  memo: string;
  items: SalesItemForm[];
};

export type CreateCustomerPayload = {
  name: string;
  ownerName: string | null;
  phone: string | null;
  address: string | null;
  memo: string | null;
};

export type CreateSalesSlipPayload = {
  salesType: "DIRECT" | "AUCTION";
  saleDate: string;
  customerId: number | null;
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
  }>;
};

export type SalesTab = "SLIPS" | "AUCTION" | "SETTLEMENT" | "CUSTOMERS";

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
  customerId: string;
  paymentStatus: string;
  salesStatus: string;
  keyword: string;
};
