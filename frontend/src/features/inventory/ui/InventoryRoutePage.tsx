import type { InventoryTab } from "@/shared/config/routes";
import type {
  InboundFilterState,
  MaterialFilterState,
  VarietyFilterState,
} from "../model/types";
import {
  getInboundRecords,
  getMaterials,
  getVarieties,
  getVarietyGenera,
} from "../api/inventoryApi";
import { InventoryInboundPage } from "./InventoryInboundPage";
import { InventoryMaterialPage } from "./InventoryMaterialPage";
import { InventoryVarietyPage } from "./InventoryVarietyPage";

export async function InventoryRoutePage({
  activeTab,
  searchParams,
}: {
  activeTab: InventoryTab;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = await searchParams;

  if (activeTab === "variety") {
    const varietyKeyword = readSearchParam(
      resolvedSearchParams,
      "varietyKeyword",
    );
    const varietyGenus = readSearchParam(resolvedSearchParams, "varietyGenus");
    const varietySale = readSearchParam(resolvedSearchParams, "varietySale");
    const varietyStatus = readSearchParam(
      resolvedSearchParams,
      "varietyStatus",
    );
    const page = readNumberParam(resolvedSearchParams, "page", 0);
    const size = readNumberParam(resolvedSearchParams, "size", 10);
    const initialFilters: VarietyFilterState = {
      keyword: varietyKeyword ?? "",
      genus: varietyGenus && varietyGenus !== "전체" ? varietyGenus : "",
      status:
        varietyStatus === "ACTIVE" || varietyStatus === "INACTIVE"
          ? varietyStatus
          : "",
      saleEnabled:
        varietySale === "사용" || varietySale === "true"
          ? "true"
          : varietySale === "미사용" || varietySale === "false"
            ? "false"
            : "",
    };

    const [varieties, varietyLookup] = await Promise.all([
      getVarieties({
        keyword: initialFilters.keyword || undefined,
        genus: initialFilters.genus || undefined,
        saleEnabled: toBoolean(initialFilters.saleEnabled),
        active: toActive(initialFilters.status),
        page,
        size,
      }),
      getVarietyGenera(),
    ]);

    return (
      <InventoryVarietyPage
        initialFilters={initialFilters}
        initialVarietyPage={varieties}
        initialVarietyLookup={varietyLookup}
      />
    );
  }

  if (activeTab === "inbound") {
    const inboundKeyword = readSearchParam(
      resolvedSearchParams,
      "inboundKeyword",
    );
    const inboundType = readSearchParam(resolvedSearchParams, "inboundType");
    const inboundStatus = readSearchParam(
      resolvedSearchParams,
      "inboundStatus",
    );
    const page = readNumberParam(resolvedSearchParams, "page", 0);
    const size = readNumberParam(resolvedSearchParams, "size", 10);
    const initialFilters: InboundFilterState = {
      keyword: inboundKeyword ?? "",
      inboundType:
        inboundType && inboundType !== "ALL"
          ? (inboundType as InboundFilterState["inboundType"])
          : "",
      status:
        inboundStatus && inboundStatus !== "ALL"
          ? (inboundStatus as InboundFilterState["status"])
          : "",
    };

    const [inboundRecords, varietyLookup] = await Promise.all([
      getInboundRecords({
        inboundType: initialFilters.inboundType || undefined,
        status: initialFilters.status || undefined,
        variety: initialFilters.keyword || undefined,
        page,
        size,
      }),
      getVarietyGenera(),
    ]);

    return (
      <InventoryInboundPage
        initialFilters={initialFilters}
        initialInboundPage={inboundRecords}
        initialVarietyLookup={varietyLookup}
      />
    );
  }

  const materialKeyword = readSearchParam(
    resolvedSearchParams,
    "materialKeyword",
  );
  const materialCategory = readSearchParam(
    resolvedSearchParams,
    "materialCategory",
  );
  const materialManufacturer = readSearchParam(
    resolvedSearchParams,
    "materialManufacturer",
  );
  const materialStatus = readSearchParam(
    resolvedSearchParams,
    "materialStatus",
  );
  const page = readNumberParam(resolvedSearchParams, "page", 0);
  const size = readNumberParam(resolvedSearchParams, "size", 10);
  const initialFilters: MaterialFilterState = {
    keyword: materialKeyword ?? "",
    category:
      materialCategory && materialCategory !== "전체" ? materialCategory : "",
    manufacturer: materialManufacturer ?? "",
    status:
      materialStatus === "ACTIVE" || materialStatus === "INACTIVE"
        ? materialStatus
        : "",
  };

  const materials = await getMaterials({
    keyword: initialFilters.keyword || undefined,
    category: initialFilters.category || undefined,
    manufacturer: initialFilters.manufacturer || undefined,
    active: toActive(initialFilters.status),
    page,
    size,
  });

  return (
    <InventoryMaterialPage
      initialFilters={initialFilters}
      initialMaterialPage={materials}
    />
  );
}

function toActive(status: "ACTIVE" | "INACTIVE" | "") {
  return status ? status === "ACTIVE" : undefined;
}

function toBoolean(value: "true" | "false" | "") {
  return value ? value === "true" : undefined;
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
