import {
  getAuctionLots,
  getAuctionSettlements,
  getAuctionTrackingSummary,
  getBusinessPartnerPage,
  getBusinessPartners,
  getSalesSlipPage,
} from "../api/salesApi";
import {
  createInitialAuctionFilters,
  createInitialBusinessPartnerFilters,
  createInitialSalesFilters,
} from "../lib/salesUrlFilters";
import {
  readBooleanParam,
  readEnumParam,
  readPageParam,
  readPageSizeParam,
  readSearchParam,
  type RouteSearchParams,
} from "../lib/salesRouteParams";
import { auctionStatusOptions } from "../lib/auctionDisplay";
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
  searchParams?: RouteSearchParams;
}) {
  if (activeTab === "auction") {
    const filters = readAuctionFilters(searchParams);
    const page = readPageParam(searchParams);
    const size = readPageSizeParam(searchParams, 20);
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
    const page = readPageParam(searchParams);
    const size = readPageSizeParam(searchParams, 10);
    const partners = await getBusinessPartnerPage(filters, page, size);
    return (
      <SalesPartnersPage
        key={createRouteStateKey(filters, page, size)}
        initialPage={partners}
        initialFilters={filters}
      />
    );
  }

  const filters = readSalesFilters(searchParams);
  const page = readPageParam(searchParams);
  const size = readPageSizeParam(searchParams, 10);
  const [partners, salesSlips] = await Promise.all([
    getBusinessPartners(),
    getSalesSlipPage(filters, page, size),
  ]);

  return (
    <SalesSlipsPage
      key={createRouteStateKey(filters, page, size)}
      initialBusinessPartners={partners}
      initialFilters={filters}
      initialPage={salesSlips}
      initialShowCreateSlip={createSlip}
    />
  );
}

function readSalesFilters(searchParams: RouteSearchParams): SalesFilterState {
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
  searchParams: RouteSearchParams,
): BusinessPartnerFilterState {
  return {
    ...createInitialBusinessPartnerFilters(),
    partnerType: readEnumParam(searchParams, "partnerType", [
      "WHOLESALE",
      "RETAIL",
      "AUCTION_HOUSE",
    ]),
    active: readEnumParam(searchParams, "active", ["ACTIVE", "INACTIVE"]),
    keyword: readSearchParam(searchParams, "keyword") ?? "",
  };
}

function readAuctionFilters(
  searchParams: RouteSearchParams,
): AuctionFilterState {
  return {
    ...createInitialAuctionFilters(),
    from: readSearchParam(searchParams, "from") ?? "",
    to: readSearchParam(searchParams, "to") ?? "",
    market: readSearchParam(searchParams, "market") ?? "",
    variety: readSearchParam(searchParams, "variety") ?? "",
    grade: readSearchParam(searchParams, "grade") ?? "",
    status: readEnumParam(
      searchParams,
      "status",
      auctionStatusOptions.map(([value]) => value),
    ),
    keyword: readSearchParam(searchParams, "keyword") ?? "",
    reviewOnly: readBooleanParam(searchParams, "reviewOnly"),
    returnOnly: readBooleanParam(searchParams, "returnOnly"),
    waitingOnly: readBooleanParam(searchParams, "waitingOnly"),
  };
}

function createRouteStateKey(
  filters: SalesFilterState | BusinessPartnerFilterState | AuctionFilterState,
  page: number,
  size: number,
) {
  return JSON.stringify({ filters, page, size });
}
