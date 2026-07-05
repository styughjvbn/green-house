import type {
  DashboardSummary,
  FarmStatusMapData,
  SalesSlip,
  WorkRecord,
} from "@/entities/farm/types";

export type AnalyticsTab = "SALES" | "VARIETY" | "CUSTOMER" | "SPACE" | "WORK";

export type AnalyticsPageProps = {
  mapData: FarmStatusMapData;
  salesSlips: SalesSlip[];
  summary: DashboardSummary;
  workRecords: WorkRecord[];
};

export type RankedValue = {
  label: string;
  value: number;
  secondary?: string;
};

export type AnalyticsViewModel = {
  currentMonthSales: number;
  shippedQuantity: number;
  unpaidAmount: number;
  saleableQuantity: number;
  monthlySales: RankedValue[];
  varietySales: RankedValue[];
  partnerSales: RankedValue[];
  paymentBreakdown: RankedValue[];
  recentSlips: SalesSlip[];
  unpaidSlips: SalesSlip[];
};

export type AnalyticsFilters = {
  dateFrom: string;
  dateTo: string;
  house: string;
  bed: string;
  zone: string;
  variety: string;
  partner: string;
};
