import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type { Customer, SalesSlip } from "@/entities/farm/types";
import type {
  AuctionLot,
  AuctionLotPage,
  AuctionLotStatus,
  AuctionTrackingSummary,
  AuctionShipmentOption,
} from "@/entities/farm/types";
import type {
  AuctionFilterState,
  CreateCustomerPayload,
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

export function getCustomers() {
  return fetchApi<Customer[]>("/customers");
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

export function confirmAuctionReturn(
  lotId: number,
  payload: { worker: string | null; memo: string | null },
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

export function createCustomer(
  payload: CreateCustomerPayload,
): Promise<Customer> {
  return requestJson<Customer>(
    "/customers",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "거래처를 저장하지 못했습니다.",
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
