import type { AuctionLotStatus } from "@/entities/farm/types";
import type {
  AuctionFilterState,
  BusinessPartnerFilterState,
  SalesFilterState,
} from "../model/types";

export type RouteSearchParams =
  | Record<string, string | string[] | undefined>
  | undefined;

export type SalesRouteState<Filters> = {
  filters: Filters;
  page: number;
  size: number;
};

export type SearchParamReader = {
  get(name: string): string | null;
};

const AUCTION_LOT_STATUSES: AuctionLotStatus[] = [
  "SHIPPED",
  "WAITING",
  "IN_PROGRESS",
  "SOLD",
  "PARTIALLY_SOLD",
  "FAILED",
  "REAUCTION_WAITING",
  "RETURN_INFERRED",
  "PARTIALLY_RETURNED",
  "RETURNED",
  "QUANTITY_MISMATCH",
  "REVIEW_REQUIRED",
  "CANCELLED",
];

export function createServerSearchParamReader(
  resolvedSearchParams: Record<string, string | string[] | undefined>,
): SearchParamReader {
  return {
    get(name) {
      const value = resolvedSearchParams?.[name];
      return Array.isArray(value) ? (value[0] ?? null) : (value ?? null);
    },
  };
}

export function readSalesRouteState(
  params: SearchParamReader,
): SalesRouteState<SalesFilterState> {
  return {
    filters: {
      from: params.get("from") ?? "",
      to: params.get("to") ?? "",
      partnerId: params.get("partnerId") ?? "",
      paymentStatus: params.get("paymentStatus") ?? "",
      salesStatus: params.get("salesStatus") ?? "",
      keyword: params.get("keyword") ?? "",
    },
    page: readBoundedIntegerValue(
      params.get("page"),
      0,
      0,
      Number.MAX_SAFE_INTEGER,
    ),
    size: readBoundedIntegerValue(params.get("size"), 10, 1, 100),
  };
}

export function readBusinessPartnerRouteState(
  params: SearchParamReader,
): SalesRouteState<BusinessPartnerFilterState> {
  return {
    filters: {
      partnerType: readEnumValue(params.get("partnerType"), [
        "WHOLESALE",
        "RETAIL",
        "AUCTION_HOUSE",
      ]),
      active: readEnumValue(params.get("active"), ["ACTIVE", "INACTIVE"]),
      keyword: params.get("keyword") ?? "",
    },
    page: readBoundedIntegerValue(
      params.get("page"),
      0,
      0,
      Number.MAX_SAFE_INTEGER,
    ),
    size: readBoundedIntegerValue(params.get("size"), 10, 1, 100),
  };
}

export function readAuctionRouteState(
  params: SearchParamReader,
): SalesRouteState<AuctionFilterState> {
  return {
    filters: {
      from: params.get("from") ?? "",
      to: params.get("to") ?? "",
      market: params.get("market") ?? "",
      variety: params.get("variety") ?? "",
      grade: params.get("grade") ?? "",
      status: readEnumValue(params.get("status"), AUCTION_LOT_STATUSES),
      keyword: params.get("keyword") ?? "",
      reviewOnly: params.get("reviewOnly") === "true",
      returnOnly: params.get("returnOnly") === "true",
      waitingOnly: params.get("waitingOnly") === "true",
    },
    page: readBoundedIntegerValue(
      params.get("page"),
      0,
      0,
      Number.MAX_SAFE_INTEGER,
    ),
    size: readBoundedIntegerValue(params.get("size"), 20, 1, 100),
  };
}

export function readCreateSlip(params: SearchParamReader) {
  return params.get("createSlip") === "1";
}

function readBoundedIntegerValue(
  value: string | null,
  defaultValue: number,
  minimum: number,
  maximum: number,
) {
  const parsed = value ? Number(value) : defaultValue;
  if (!Number.isInteger(parsed)) {
    return defaultValue;
  }
  return Math.min(Math.max(parsed, minimum), maximum);
}

function readEnumValue<const Value extends string>(
  value: string | null,
  allowedValues: readonly Value[],
): Value | "" {
  return value != null && allowedValues.includes(value as Value)
    ? (value as Value)
    : "";
}
