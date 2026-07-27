import { fetchApi } from "@/shared/api/client";
import type { SalesSlip, SalesSlipPage } from "@/entities/farm/types";

export function getPrintableSalesSlips(page = 0, size = 10) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return fetchApi<SalesSlipPage>(`/sales-slips/print?${params}`);
}

export function getSalesSlipPrintData(salesSlipId: number) {
  return fetchApi<SalesSlip>(`/sales-slips/${salesSlipId}/print`);
}
