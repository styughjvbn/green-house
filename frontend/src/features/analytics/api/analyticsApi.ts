import type {
  BusinessPartner,
  DashboardSummary,
  FarmStatusMapData,
  PartnerBalanceSummary,
  SalesSlip,
  WorkRecord,
} from "@/entities/farm/types";
import { fetchApi } from "@/shared/api/client";

export async function getAnalyticsData() {
  const [businessPartners, summary, mapData, workRecords, salesSlips] =
    await Promise.all([
      fetchApi<BusinessPartner[]>("/business-partners"),
      fetchApi<DashboardSummary>("/dashboard/summary"),
      fetchApi<FarmStatusMapData>("/farm-status/map"),
      fetchApi<WorkRecord[]>("/work-records"),
      fetchApi<SalesSlip[]>("/sales-slips"),
    ]);

  const partnerBalances = await Promise.all(
    businessPartners.map((partner) =>
      fetchApi<PartnerBalanceSummary>(
        `/business-partners/${partner.id}/balance-summary`,
      ),
    ),
  );

  return {
    businessPartners,
    mapData,
    partnerBalances,
    salesSlips,
    summary,
    workRecords,
  };
}
