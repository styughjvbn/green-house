import {
  getAuctionLots,
  getAuctionSettlements,
  getAuctionTrackingSummary,
  getBusinessPartners,
  getSalesSlipPage,
} from "../api/salesApi";
import {
  createInitialAuctionFilters,
  createInitialBusinessPartnerFilters,
  createInitialSalesFilters,
} from "../lib/salesUrlFilters";
import type { SalesTab } from "../model/types";
import type {
  AuctionFilterState,
  BusinessPartnerFilterState,
  SalesFilterState,
} from "../model/types";
import { SalesAuctionPage } from "./SalesAuctionPage";
import { SalesPartnersPage } from "./SalesPartnersPage";
import { SalesSettlementPage } from "./SalesSettlementPage";
import { SalesSlipsPage } from "./SalesSlipsPage";

export async function SalesRoutePage({
  activeTab,
  createSlip = false,
  searchParams,
}: {
  activeTab: SalesTab;
  createSlip?: boolean;
  searchParams?: Record<string, string | string[] | undefined>;
}) {
  if (activeTab === "auction") {
    const filters = readAuctionFilters(searchParams);
    const page = readNumberParam(searchParams, "page", 0);
    const size = readNumberParam(searchParams, "size", 20);
    const [auctionPage, auctionSummary] = await Promise.all([
      getAuctionLots(filters, page, size),
      getAuctionTrackingSummary(),
    ]);

    return (
      <SalesAuctionPage
        key={createRouteStateKey(filters, page, size)}
        initialPage={auctionPage}
        initialSummary={auctionSummary}
        initialFilters={filters}
      />
    );
  }

  if (activeTab === "settlement") {
    const auctionSettlements = await getAuctionSettlements();
    return <SalesSettlementPage initialSettlements={auctionSettlements} />;
  }

  if (activeTab === "partners") {
    const filters = readBusinessPartnerFilters(searchParams);
    const page = readNumberParam(searchParams, "page", 0);
    const size = readNumberParam(searchParams, "size", 10);
    const partners = await getBusinessPartners();
    return (
      <SalesPartnersPage
        key={createRouteStateKey(filters, page, size)}
        initialFilters={filters}
        initialPage={createBusinessPartnerPage(partners, page, size)}
      />
    );
  }

  const filters = readSalesFilters(searchParams);
  const page = readNumberParam(searchParams, "page", 0);
  const size = readNumberParam(searchParams, "size", 10);
  const [partners, salesSlips] = await Promise.all([
    getBusinessPartners(),
    getSalesSlipPage(filters, page, size),
  ]);

  return (
    <SalesSlipsPage
      key={createRouteStateKey(filters, page, size)}
      initialBusinessPartnerPage={createBusinessPartnerPage(partners)}
      initialFilters={filters}
      initialPage={salesSlips}
      initialShowCreateSlip={createSlip}
    />
  );
}

function readSalesFilters(
  searchParams: Record<string, string | string[] | undefined> | undefined,
): SalesFilterState {
  return {
    ...createInitialSalesFilters(),
    from: readSearchParam(searchParams, "from") ?? "",
    to: readSearchParam(searchParams, "to") ?? "",
    partnerId: readSearchParam(searchParams, "partnerId") ?? "",
    paymentStatus: readSearchParam(searchParams, "paymentStatus") ?? "",
    salesStatus: readSearchParam(searchParams, "salesStatus") ?? "",
    keyword: readSearchParam(searchParams, "keyword") ?? "",
  };
}

function readBusinessPartnerFilters(
  searchParams: Record<string, string | string[] | undefined> | undefined,
): BusinessPartnerFilterState {
  return {
    ...createInitialBusinessPartnerFilters(),
    partnerType: readSearchParam(searchParams, "partnerType") ?? "",
    active: readSearchParam(searchParams, "active") ?? "",
    keyword: readSearchParam(searchParams, "keyword") ?? "",
  };
}

function readAuctionFilters(
  searchParams: Record<string, string | string[] | undefined> | undefined,
): AuctionFilterState {
  return {
    ...createInitialAuctionFilters(),
    from: readSearchParam(searchParams, "from") ?? "",
    to: readSearchParam(searchParams, "to") ?? "",
    market: readSearchParam(searchParams, "market") ?? "",
    variety: readSearchParam(searchParams, "variety") ?? "",
    grade: readSearchParam(searchParams, "grade") ?? "",
    status: readSearchParam(searchParams, "status") ?? "",
    keyword: readSearchParam(searchParams, "keyword") ?? "",
    reviewOnly: readBooleanParam(searchParams, "reviewOnly"),
    returnOnly: readBooleanParam(searchParams, "returnOnly"),
    waitingOnly: readBooleanParam(searchParams, "waitingOnly"),
  };
}

function readSearchParam(
  searchParams: Record<string, string | string[] | undefined> | undefined,
  key: string,
) {
  const value = searchParams?.[key];

  if (Array.isArray(value)) {
    return value[0];
  }

  return value;
}

function readNumberParam(
  searchParams: Record<string, string | string[] | undefined> | undefined,
  key: string,
  defaultValue: number,
) {
  const value = readSearchParam(searchParams, key);
  const parsed = value ? Number(value) : defaultValue;

  return Number.isFinite(parsed) ? parsed : defaultValue;
}

function readBooleanParam(
  searchParams: Record<string, string | string[] | undefined> | undefined,
  key: string,
) {
  return readSearchParam(searchParams, key) === "true";
}

function createRouteStateKey(
  filters: SalesFilterState | BusinessPartnerFilterState | AuctionFilterState,
  page: number,
  size: number,
) {
  return JSON.stringify({ filters, page, size });
}

function createBusinessPartnerPage(
  partners: Awaited<ReturnType<typeof getBusinessPartners>>,
  page = 0,
  size = Math.max(1, partners.length),
) {
  return {
    content: partners,
    page,
    size,
    totalElements: partners.length,
    totalPages: Math.max(1, Math.ceil(partners.length / size)),
  };
}
