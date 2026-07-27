import type {
  InboundFilterState,
  InboundStatus,
  InboundType,
  MaterialFilterState,
  VarietyFilterState,
} from "../model/types";

export type InventoryRouteState<Filters> = {
  filters: Filters;
  page: number;
  size: number;
};

export type SearchParamReader = {
  get(name: string): string | null;
};

export type ServerSearchParams = Record<string, string | string[] | undefined>;

const INBOUND_TYPES: InboundType[] = [
  "FLASK_SEEDLING",
  "POTTED_SEEDLING",
  "PRODUCT_POT",
  "SAMPLE",
  "ETC",
];

const INBOUND_STATUSES: InboundStatus[] = [
  "TEMP_STORED",
  "POTTING_PENDING",
  "POTTING_IN_PROGRESS",
  "POTTED",
  "PLACED",
  "CANCELED",
];

export function createServerSearchParamReader(
  searchParams: ServerSearchParams,
): SearchParamReader {
  return {
    get(name) {
      const value = searchParams[name];
      return Array.isArray(value) ? (value[0] ?? null) : (value ?? null);
    },
  };
}

export function readMaterialRouteState(
  params: SearchParamReader,
): InventoryRouteState<MaterialFilterState> {
  return {
    filters: {
      category: normalizeAll(params.get("materialCategory")),
      keyword: params.get("materialKeyword") ?? "",
      manufacturer: params.get("materialManufacturer") ?? "",
      status: readEnum(params.get("materialStatus"), ["ACTIVE", "INACTIVE"]),
    },
    page: readPage(params.get("page")),
    size: readSize(params.get("size")),
  };
}

export function readVarietyRouteState(
  params: SearchParamReader,
): InventoryRouteState<VarietyFilterState> {
  const saleEnabled = params.get("varietySale");
  return {
    filters: {
      genus: normalizeAll(params.get("varietyGenus")),
      keyword: params.get("varietyKeyword") ?? "",
      status: readEnum(params.get("varietyStatus"), ["ACTIVE", "INACTIVE"]),
      saleEnabled:
        saleEnabled === "사용" || saleEnabled === "true"
          ? "true"
          : saleEnabled === "미사용" || saleEnabled === "false"
            ? "false"
            : "",
    },
    page: readPage(params.get("page")),
    size: readSize(params.get("size")),
  };
}

export function readInboundRouteState(
  params: SearchParamReader,
): InventoryRouteState<InboundFilterState> {
  return {
    filters: {
      inboundType: readEnum(params.get("inboundType"), INBOUND_TYPES),
      status: readEnum(params.get("inboundStatus"), INBOUND_STATUSES),
      keyword: params.get("inboundKeyword") ?? "",
    },
    page: readPage(params.get("page")),
    size: readSize(params.get("size")),
  };
}

function normalizeAll(value: string | null) {
  return value && value !== "전체" && value !== "ALL" ? value : "";
}

function readEnum<const Value extends string>(
  value: string | null,
  allowedValues: readonly Value[],
): Value | "" {
  return value != null && allowedValues.includes(value as Value)
    ? (value as Value)
    : "";
}

function readPage(value: string | null) {
  return readBoundedInteger(value, 0, 0, Number.MAX_SAFE_INTEGER);
}

function readSize(value: string | null) {
  return readBoundedInteger(value, 10, 1, 100);
}

function readBoundedInteger(
  value: string | null,
  fallback: number,
  minimum: number,
  maximum: number,
) {
  const parsed = value == null ? fallback : Number(value);
  if (!Number.isInteger(parsed)) return fallback;
  return Math.min(Math.max(parsed, minimum), maximum);
}
