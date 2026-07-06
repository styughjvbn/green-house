import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type {
  BusinessPartner,
  PartnerPaymentEvent,
  PartnerSettlementSettings,
  SalesSlip,
} from "@/entities/farm/types";
import type {
  AuctionLot,
  AuctionAttemptStatus,
  AuctionInspectionStatus,
  AuctionLotPage,
  AuctionLotStatus,
  AuctionTrackingSummary,
  AuctionShipmentOption,
  AuctionSettlement,
  AuctionSettlementStatus,
} from "@/entities/farm/types";
import type {
  AuctionFilterState,
  CreateBusinessPartnerPayload,
  CreateSalesSlipPayload,
} from "../model/types";

type ApiSuccess<T> = {
  data: T;
  message: string | null;
};

type ApiFailure = {
  error?: {
    message?: string;
    details?: string[];
  };
};

async function requestJson<T>(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);
  const payload = (await response.json()) as ApiSuccess<T> | ApiFailure;

  if (!response.ok) {
    const apiError = "error" in payload ? payload.error : undefined;
    const detail = apiError?.details?.find(Boolean);
    throw new Error(detail ?? apiError?.message ?? fallbackMessage);
  }

  return (payload as ApiSuccess<T>).data;
}

export function getBusinessPartners() {
  return fetchApi<BusinessPartner[]>("/business-partners");
}

export function getSalesSlips() {
  return fetchApi<SalesSlip[]>("/sales-slips");
}

export function getAuctionShipmentOptions() {
  return fetchApi<AuctionShipmentOption[]>("/sales-slips/auction-shipments");
}

export function getAuctionLots(
  filters?: Partial<AuctionFilterState>,
  page = 0,
  size = 20,
) {
  const params = new URLSearchParams();
  Object.entries(filters ?? {}).forEach(([key, value]) => {
    if (value !== "" && value !== false && value != null) {
      params.set(key, String(value));
    }
  });
  params.set("page", String(page));
  params.set("size", String(size));
  const query = params.size > 0 ? `?${params}` : "";
  return fetchApi<AuctionLotPage>(`/auction-lots${query}`);
}

export function getAuctionTrackingSummary() {
  return fetchApi<AuctionTrackingSummary>("/auction-tracking/summary");
}

export function getAuctionSettlements(filters?: {
  auctionHouseId?: number;
  from?: string;
  to?: string;
  status?: AuctionSettlementStatus;
}) {
  const params = new URLSearchParams();
  Object.entries(filters ?? {}).forEach(([key, value]) => {
    if (value != null && value !== "") params.set(key, String(value));
  });
  const query = params.size > 0 ? `?${params}` : "";
  return fetchApi<AuctionSettlement[]>(`/auction-settlements${query}`);
}

export function rebuildAuctionSettlement(
  auctionHouseId: number,
  auctionDate: string,
) {
  const params = new URLSearchParams({
    auctionHouseId: String(auctionHouseId),
    auctionDate,
  });
  return requestJson<AuctionSettlement>(
    `/auction-settlements/rebuild?${params}`,
    { method: "POST" },
    "경매 정산을 다시 계산하지 못했습니다.",
  );
}

export type ManualPaymentPayload = {
  amount: number;
  paymentDate: string;
  paymentMethod: string | null;
  depositorName: string | null;
  worker: string | null;
  memo: string | null;
};

export function confirmAuctionSettlementPayment(
  settlementId: number,
  payload: ManualPaymentPayload,
) {
  return requestJson<AuctionSettlement>(
    `/auction-settlements/${settlementId}/confirm-payment`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "입금을 확인하지 못했습니다.",
  );
}

export function confirmSalesSlipPayment(
  salesSlipId: number,
  payload: ManualPaymentPayload,
) {
  return requestJson<SalesSlip>(
    `/sales-slips/${salesSlipId}/confirm-payment`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "입금을 확인하지 못했습니다.",
  );
}

export function getPaymentEvents(
  targetType: "SALES_SLIP" | "AUCTION_SETTLEMENT",
  targetId: number,
) {
  const params = new URLSearchParams({
    targetType,
    targetId: String(targetId),
  });
  return fetchApi<PartnerPaymentEvent[]>(`/partner-payment-events?${params}`);
}

export function confirmAuctionReturn(
  lotId: number,
  payload: {
    returnedQuantity: number;
    returnDate: string;
    worker: string | null;
    memo: string | null;
  },
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/confirm-return`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "반환 상태를 확정하지 못했습니다.",
  );
}

export function adjustAuctionQuantity(
  lotId: number,
  payload: {
    soldQuantity: number;
    waitingQuantity: number;
    returnedQuantity: number;
    worker: string | null;
    memo: string | null;
  },
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/adjust-quantity`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "수량을 보정하지 못했습니다.",
  );
}

export function createAuctionResult(
  lotId: number,
  payload: {
    auctionDate: string;
    attemptNo: number | null;
    attemptStatus: AuctionAttemptStatus;
    failedReason: string | null;
    memo: string | null;
    resultLines?: Array<{
      auctionGrade: string | null;
      quantity: number;
      unitPrice: number;
      note: string | null;
      inspectionStatus: AuctionInspectionStatus | null;
    }>;
  },
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/results`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "경매 결과를 저장하지 못했습니다.",
  );
}

export function changeAuctionLotStatus(
  lotId: number,
  payload: {
    status: AuctionLotStatus;
    reason: string;
    worker: string | null;
    memo: string | null;
  },
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/status`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "상태를 변경하지 못했습니다.",
  );
}

export function createBusinessPartner(
  payload: CreateBusinessPartnerPayload,
): Promise<BusinessPartner> {
  return requestJson<BusinessPartner>(
    "/business-partners",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "거래처를 저장하지 못했습니다.",
  );
}

export function getPartnerSettlementSettings(partnerId: number) {
  return fetchApi<PartnerSettlementSettings>(
    `/business-partners/${partnerId}/settlement-settings`,
  );
}

export function updatePartnerSettlementSettings(
  partnerId: number,
  payload: Omit<PartnerSettlementSettings, "id" | "partnerId">,
) {
  return requestJson<PartnerSettlementSettings>(
    `/business-partners/${partnerId}/settlement-settings`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "정산 설정을 저장하지 못했습니다.",
  );
}

export function createSalesSlip(
  payload: CreateSalesSlipPayload,
): Promise<SalesSlip> {
  return requestJson<SalesSlip>(
    "/sales-slips",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "판매 전표를 저장하지 못했습니다.",
  );
}
