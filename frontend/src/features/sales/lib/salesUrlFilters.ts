import type {
  AuctionFilterState,
  BusinessPartnerFilterState,
  SalesFilterState,
} from "../model/types";

export const SALES_FILTER_KEYS: Array<keyof SalesFilterState> = [
  "from",
  "to",
  "partnerId",
  "paymentStatus",
  "salesStatus",
  "keyword",
];

export const BUSINESS_PARTNER_FILTER_KEYS: Array<
  keyof BusinessPartnerFilterState
> = ["partnerType", "active", "keyword"];

export const AUCTION_FILTER_KEYS: Array<keyof AuctionFilterState> = [
  "from",
  "to",
  "market",
  "variety",
  "grade",
  "status",
  "keyword",
  "reviewOnly",
  "returnOnly",
  "waitingOnly",
];

export function createInitialSalesFilters(): SalesFilterState {
  return {
    from: "",
    to: "",
    partnerId: "",
    paymentStatus: "",
    salesStatus: "",
    keyword: "",
  };
}

export function createInitialBusinessPartnerFilters(): BusinessPartnerFilterState {
  return {
    partnerType: "",
    active: "",
    keyword: "",
  };
}

export function createInitialAuctionFilters(): AuctionFilterState {
  return {
    from: "",
    to: "",
    market: "",
    variety: "",
    grade: "",
    status: "",
    keyword: "",
    reviewOnly: false,
    returnOnly: false,
    waitingOnly: false,
  };
}

export function writeSalesFilterParams(
  params: URLSearchParams,
  filters: SalesFilterState,
) {
  SALES_FILTER_KEYS.forEach((key) => {
    setStringParam(params, key, filters[key]);
  });
}

export function writeBusinessPartnerFilterParams(
  params: URLSearchParams,
  filters: BusinessPartnerFilterState,
) {
  BUSINESS_PARTNER_FILTER_KEYS.forEach((key) => {
    setStringParam(params, key, filters[key]);
  });
}

export function writeAuctionFilterParams(
  params: URLSearchParams,
  filters: AuctionFilterState,
) {
  AUCTION_FILTER_KEYS.forEach((key) => {
    const value = filters[key];

    if (typeof value === "boolean") {
      setBooleanParam(params, key, value);
      return;
    }

    setStringParam(params, key, value);
  });
}

function setStringParam(params: URLSearchParams, key: string, value: string) {
  const normalized = value.trim();
  if (!normalized) {
    params.delete(key);
    return;
  }

  params.set(key, normalized);
}

function setBooleanParam(params: URLSearchParams, key: string, value: boolean) {
  if (value) {
    params.set(key, "true");
    return;
  }

  params.delete(key);
}
