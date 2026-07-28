import type {
  AuctionAttemptStatus,
  AuctionInspectionStatus,
  AuctionLotStatus,
  PartnerSettlementSettings,
  PartnerType,
} from "@/entities/farm/types";

export type BusinessPartnerPayload = {
  name: string;
  partnerType: PartnerType;
  ownerName: string | null;
  phone: string | null;
  address: string | null;
  memo: string | null;
};

export type CreateBusinessPartnerPayload = BusinessPartnerPayload;
export type UpdateBusinessPartnerPayload = BusinessPartnerPayload;

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

export type AuctionResultLinePayload = {
  auctionGrade: string | null;
  quantity: number;
  unitPrice: number;
  note: string | null;
  inspectionStatus: AuctionInspectionStatus | null;
};

export type AuctionResultFormPayload = {
  auctionDate: string;
  attemptStatus: AuctionAttemptStatus;
  failedReason: string | null;
  memo: string | null;
  resultLines?: AuctionResultLinePayload[];
};

export type CreateAuctionResultPayload = AuctionResultFormPayload & {
  attemptNo: number | null;
};

export type AuctionReturnPayload = {
  returnedQuantity: number;
  returnDate: string;
  worker: string | null;
  memo: string | null;
};

export type AuctionQuantityAdjustmentPayload = {
  soldQuantity: number;
  waitingQuantity: number;
  returnedQuantity: number;
  worker: string | null;
  memo: string | null;
};

export type AuctionLotStatusPayload = {
  status: AuctionLotStatus;
  reason: string;
  worker: string | null;
  memo: string | null;
};

export type ManualPaymentPayload = {
  amount: number;
  paymentDate: string;
  paymentMethod: string | null;
  depositorName: string | null;
  worker: string | null;
  memo: string | null;
};

export type PartnerSettlementSettingsPayload = Omit<
  PartnerSettlementSettings,
  "id" | "partnerId"
>;
