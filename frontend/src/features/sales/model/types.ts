import type { AuctionLotStatus, PartnerType } from "@/entities/farm/types";
import type { components } from "@/shared/api/generated/openapi";
import type { SalesTab } from "@/shared/config/routes";

export type { SalesTab };

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
  unit: string;
  boxes: string;
  quantity: string;
  unitPrice: string;
  memo: string;
  allocations: SalesAllocationForm[];
  allocationMode: "AUTO" | "MANUAL";
};

export type SalesSlipForm = {
  recordOnly: boolean;
  salesType: "DIRECT" | "AUCTION";
  saleDate: string;
  partnerId: string;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string;
  receiptEnabled: boolean;
  receiptAmount: string;
  receiptDate: string;
  receiptMethod: NonNullable<
    components["schemas"]["SalesInitialReceiptRequest"]["method"]
  >;
  memo: string;
  items: SalesItemForm[];
};

export type SalesSlipFormMode = "create" | "edit";

export type AuctionFilterState = {
  from: string;
  to: string;
  market: string;
  variety: string;
  grade: string;
  status: AuctionLotStatus | "";
  keyword: string;
  reviewOnly: boolean;
  returnOnly: boolean;
  waitingOnly: boolean;
};

export type SalesFilterState = {
  salesType:
    | NonNullable<components["schemas"]["SalesSlipResponse"]["salesType"]>
    | "";
  from: string;
  to: string;
  partnerId: string;
  paymentStatus: string;
  salesStatus: string;
  keyword: string;
};

export type BusinessPartnerFilterState = {
  partnerType: PartnerType | "";
  active: "ACTIVE" | "INACTIVE" | "";
  keyword: string;
};
