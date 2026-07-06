import type {
  BusinessPartner,
  DashboardSummary,
  FarmStatusMapData,
  PartnerBalanceSummary,
  SalesSlip,
  WorkRecord,
} from "@/entities/farm/types";

export type AnalyticsTab = "SALES" | "VARIETY" | "CUSTOMER" | "SPACE" | "WORK";

export type AnalyticsPageProps = {
  businessPartners: BusinessPartner[];
  partnerBalances: PartnerBalanceSummary[];
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

export type PartnerAnalyticsStat = {
  partnerId: number;
  partnerName: string;
  partnerType: BusinessPartner["partnerType"];
  totalSales: number;
  transactionCount: number;
  unpaidAmount: number;
  paidAmount: number;
  receivableBalance: number;
  creditBalance: number;
  unappliedPaymentAmount: number;
  latestSaleDate: string | null;
};

export type AnalyticsViewModel = {
  currentMonthSales: number;
  shippedQuantity: number;
  unpaidAmount: number;
  saleableQuantity: number;
  monthlySales: RankedValue[];
  varietySales: RankedValue[];
  partnerSales: RankedValue[];
  partnerStats: PartnerAnalyticsStat[];
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
