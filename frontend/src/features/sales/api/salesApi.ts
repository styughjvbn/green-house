import { API_BASE_URL } from "@/shared/api/client";
import type { Customer, SalesSlip } from "@/entities/farm/types";
import type { CreateCustomerPayload, CreateSalesSlipPayload } from "../model/types";

type ApiSuccess<T> = {
  data: T;
  message: string | null;
};

type ApiFailure = {
  error?: {
    message?: string;
  };
};

async function requestJson<T>(path: string, init: RequestInit, fallbackMessage: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);
  const payload = (await response.json()) as ApiSuccess<T> | ApiFailure;

  if (!response.ok) {
    throw new Error("error" in payload ? payload.error?.message ?? fallbackMessage : fallbackMessage);
  }

  return (payload as ApiSuccess<T>).data;
}

export function createCustomer(payload: CreateCustomerPayload): Promise<Customer> {
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

export function createSalesSlip(payload: CreateSalesSlipPayload): Promise<SalesSlip> {
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

