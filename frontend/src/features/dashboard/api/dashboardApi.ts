import type {
  DashboardSummary,
  FarmStatusMapData,
  SalesSlip,
  WorkRecord,
} from "@/entities/farm/types";
import { fetchApi } from "@/shared/api/client";

export async function getDashboardData() {
  const [summary, mapData, workRecords, salesSlips] = await Promise.all([
    fetchApi<DashboardSummary>("/dashboard/summary"),
    fetchApi<FarmStatusMapData>("/farm-status/map"),
    fetchApi<WorkRecord[]>("/work-records"),
    fetchApi<SalesSlip[]>("/sales-slips"),
  ]);

  return {
    mapData,
    salesSlips,
    summary,
    workRecords,
  };
}
