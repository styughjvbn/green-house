import type {
  InboundFilterState,
  MaterialFilterState,
  VarietyFilterState,
} from "../model/types";

export const MATERIAL_FILTER_KEYS = [
  "materialCategory",
  "materialKeyword",
  "materialManufacturer",
  "materialStatus",
] as const;

export const VARIETY_FILTER_KEYS = [
  "varietyGenus",
  "varietyKeyword",
  "varietyStatus",
  "varietySale",
] as const;

export const INBOUND_FILTER_KEYS = [
  "inboundType",
  "inboundStatus",
  "inboundKeyword",
] as const;

export function createEmptyMaterialFilters(): MaterialFilterState {
  return {
    category: "",
    keyword: "",
    manufacturer: "",
    status: "",
  };
}

export function createEmptyVarietyFilters(): VarietyFilterState {
  return {
    genus: "",
    keyword: "",
    status: "",
    saleEnabled: "",
  };
}

export function createEmptyInboundFilters(): InboundFilterState {
  return {
    inboundType: "",
    status: "",
    keyword: "",
  };
}

export function writeMaterialFilterParams(
  params: URLSearchParams,
  filters: MaterialFilterState,
) {
  setParam(params, "materialCategory", filters.category);
  setParam(params, "materialKeyword", filters.keyword);
  setParam(params, "materialManufacturer", filters.manufacturer);
  setParam(params, "materialStatus", filters.status);
}

export function writeVarietyFilterParams(
  params: URLSearchParams,
  filters: VarietyFilterState,
) {
  setParam(params, "varietyGenus", filters.genus);
  setParam(params, "varietyKeyword", filters.keyword);
  setParam(params, "varietyStatus", filters.status);
  setParam(params, "varietySale", filters.saleEnabled);
}

export function writeInboundFilterParams(
  params: URLSearchParams,
  filters: InboundFilterState,
) {
  setParam(params, "inboundType", filters.inboundType);
  setParam(params, "inboundStatus", filters.status);
  setParam(params, "inboundKeyword", filters.keyword);
}

function setParam(params: URLSearchParams, key: string, value: string) {
  const normalized = value.trim();
  if (!normalized) {
    params.delete(key);
    return;
  }

  params.set(key, normalized);
}
