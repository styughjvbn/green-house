import type {
  AuctionAttemptStatus,
  AuctionInspectionStatus,
  AuctionLotStatus,
  PartnerSettlementSettings,
  PartnerType,
} from "@/entities/farm/types";
import type { components, operations } from "@/shared/api/generated/openapi";

export type SalesCreationRequestKey = NonNullable<
  NonNullable<
    operations["createSalesSlip"]["parameters"]["header"]
  >["Idempotency-Key"]
>;

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

export type CreateAuctionResultPayload = AuctionResultFormPayload &
  Pick<components["schemas"]["AuctionLotResultRequest"], "idempotencyKey"> & {
    attemptNo: number | null;
  };

export type AuctionReturnPayload = Pick<
  components["schemas"]["AuctionLotReturnRequest"],
  "idempotencyKey"
> & {
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
  idempotencyKey: string;
  paymentMethod: string | null;
  depositorName: string | null;
  worker: string | null;
  memo: string | null;
};

export type PartnerSettlementSettingsPayload = Omit<
  PartnerSettlementSettings,
  "id" | "partnerId" | "capabilities"
>;

export type AuctionFollowUp = Required<
  components["schemas"]["AuctionFollowUpResponse"]
>;
export type AuctionArrival = Required<
  components["schemas"]["AuctionArrivalResponse"]
>;
export type AuctionFollowUpMethod = NonNullable<AuctionFollowUp["method"]>;
export type AuctionFollowUpPayload =
  components["schemas"]["AuctionFollowUpCommand"];
export type AuctionArrivalPayload =
  components["schemas"]["AuctionArrivalCommand"];
export type AuctionArrivalCancellationPayload =
  components["schemas"]["CancelAuctionArrivalCommand"];
export type AuctionFollowUpResult = {
  followUp: AuctionFollowUp;
  arrival: AuctionArrival | null;
};
