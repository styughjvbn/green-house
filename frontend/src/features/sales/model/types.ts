import type { AuctionLotStatus, PartnerType } from "@/entities/farm/types";
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
