import type {
  BusinessPartner,
  DashboardSummary,
  FarmStatusMapData,
  House,
  PartnerBalanceSummary,
  WorkOperationStatus,
  WorkTypeTemplate,
} from "@/entities/farm/types";

export type AnalyticsTab = "SALES" | "VARIETY" | "CUSTOMER" | "SPACE" | "WORK";

export type AnalyticsPageProps = {
  activeTab?: AnalyticsTab;
  businessPartners: BusinessPartner[];
  houses: House[];
  partnerBalances: PartnerBalanceSummary[];
  mapData: FarmStatusMapData;
  partnerAnalytics: PartnerAnalyticsData | null;
  salesAnalytics: SalesAnalyticsData | null;
  salesSlips: AnalyticsSlipSummary[];
  summary: DashboardSummary;
  workAnalytics: WorkAnalyticsData | null;
};

export type RankedValue = {
  label: string;
  value: number;
  secondary?: string;
};

export type SalesInsight = {
  tone: "green" | "red" | "orange" | "blue";
  text: string;
  actionLabel?: string;
  actionHref?: string;
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

export type AnalyticsSlipSummary = {
  id: number;
  slipNumber: string;
  saleDate: string;
  partnerName: string;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  paymentStatus: string;
  salesStatus: string;
};

export type SalesAnalyticsData = {
  currentMonthSales: number;
  shippedQuantity: number;
  unpaidAmount: number;
  monthlySales: RankedValue[];
  varietySales: RankedValue[];
  partnerSales: RankedValue[];
  paymentBreakdown: RankedValue[];
  recentSlips: AnalyticsSlipSummary[];
  unpaidSlips: AnalyticsSlipSummary[];
  salesInsights: SalesInsight[];
};

export type PartnerAnalyticsData = {
  partnerStats: PartnerAnalyticsStat[];
  partnerSales: RankedValue[];
};

export type WorkAnalyticsData = {
  totalCount: number;
  movementCount: number;
  statusCount: number;
  latestWorkDate: string | null;
  workTypeCounts: RankedValue[];
  recentRecords: WorkAnalyticsItem[];
};

export type WorkAnalyticsItem = {
  id: number;
  workDate: string;
  workType: string;
  workTypeTemplate: WorkTypeTemplate;
  title: string;
  sourceScopeType:
    | "FARM"
    | "HOUSE"
    | "PHYSICAL_BED"
    | "BED_ZONE"
    | "ORCHID_GROUP"
    | "NONE"
    | "DERIVED_GROUP"
    | "USER_COLLECTION"
    | "MANUAL_SELECTION"
    | "INBOUND_RECORD_SELECTION";
  worker: string | null;
  memo: string | null;
  status: WorkOperationStatus;
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
  recentSlips: AnalyticsSlipSummary[];
  salesInsights: SalesInsight[];
  unpaidSlips: AnalyticsSlipSummary[];
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
