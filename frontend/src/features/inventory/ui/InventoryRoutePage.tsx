import type { House } from "@/entities/farm/types";
import type { InventoryTab } from "@/shared/config/routes";
import {
  getInboundRecords,
  getMaterials,
  getVarieties,
  getVarietyGenera,
} from "../api/inventoryApi";
import { fetchApi } from "@/shared/api/client";
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

    const [varieties, varietyLookup] = await Promise.all([
      getVarieties({
        keyword: varietyKeyword,
        genus: varietyGenus,
        saleEnabled:
          varietySale === "사용"
            ? true
            : varietySale === "미사용"
              ? false
              : undefined,
        active:
          varietyStatus === "ACTIVE"
            ? true
            : varietyStatus === "INACTIVE"
              ? false
              : undefined,
        page,
        size,
      }),
      getVarietyGenera(),
    ]);

    return (
      <InventoryVarietyPage
        initialVarietyPage={varieties}
        varietyGenera={varietyLookup.genera}
        varietyOptions={varietyLookup.varieties}
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

    const [inboundRecords, varietyLookup, houses] = await Promise.all([
      getInboundRecords({
        inboundType:
          inboundType && inboundType !== "ALL"
            ? (inboundType as
                | "FLASK_SEEDLING"
                | "POTTED_SEEDLING"
                | "PRODUCT_POT"
                | "SAMPLE"
                | "ETC")
            : undefined,
        status:
          inboundStatus && inboundStatus !== "ALL"
            ? (inboundStatus as
                | "TEMP_STORED"
                | "POTTING_PENDING"
                | "POTTING_IN_PROGRESS"
                | "POTTED"
                | "PLACED"
                | "CANCELED")
            : undefined,
        variety: inboundKeyword,
        page,
        size,
      }),
      getVarietyGenera(),
      fetchApi<House[]>("/houses"),
    ]);

    return (
      <InventoryInboundPage
        houses={houses}
        initialInboundPage={inboundRecords}
        varietyOptions={varietyLookup.varieties}
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

  const materials = await getMaterials({
    keyword: materialKeyword,
    category:
      materialCategory && materialCategory !== "전체"
        ? materialCategory
        : undefined,
    manufacturer: materialManufacturer,
    active:
      materialStatus === "ACTIVE"
        ? true
        : materialStatus === "INACTIVE"
          ? false
          : undefined,
    page,
    size,
  });

  return <InventoryMaterialPage initialMaterialPage={materials} />;
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
