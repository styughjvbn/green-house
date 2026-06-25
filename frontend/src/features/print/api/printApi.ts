import { fetchApi } from "@/shared/api/client";
import type { SalesSlip } from "@/entities/farm/types";

export function getPrintableSalesSlips() {
  return fetchApi<SalesSlip[]>("/sales-slips");
}

export function getSalesSlipPrintData(salesSlipId: number) {
  return fetchApi<SalesSlip>(`/sales-slips/${salesSlipId}/print`);
}
