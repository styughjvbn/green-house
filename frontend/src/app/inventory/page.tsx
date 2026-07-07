import type { House } from "@/entities/farm/types";
import {
  getInboundRecords,
  getMaterials,
  getVarieties,
  InventoryPage,
} from "@/features/inventory";
import { fetchApi } from "@/shared/api/client";

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  const varietyKeyword = readSearchParam(
    resolvedSearchParams,
    "varietyKeyword",
  );
  const varietyGenus = readSearchParam(resolvedSearchParams, "varietyGenus");
  const varietySale = readSearchParam(resolvedSearchParams, "varietySale");
  const varietyStatus = readSearchParam(resolvedSearchParams, "varietyStatus");
  const varietyPage = readNumberParam(resolvedSearchParams, "varietyPage", 0);
  const varietySize = readNumberParam(resolvedSearchParams, "varietySize", 10);
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
  const materialPage = readNumberParam(resolvedSearchParams, "materialPage", 0);
  const materialSize = readNumberParam(
    resolvedSearchParams,
    "materialSize",
    10,
  );
  const inboundKeyword = readSearchParam(
    resolvedSearchParams,
    "inboundKeyword",
  );
  const inboundType = readSearchParam(resolvedSearchParams, "inboundType");
  const inboundStatus = readSearchParam(resolvedSearchParams, "inboundStatus");
  const inboundPage = readNumberParam(resolvedSearchParams, "inboundPage", 0);
  const inboundSize = readNumberParam(resolvedSearchParams, "inboundSize", 10);

  const [varieties, varietyOptions, inboundRecords, materials, houses] =
    await Promise.all([
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
        page: varietyPage,
        size: varietySize,
      }),
      getVarieties({
        active: true,
        page: 0,
        size: 100,
      }),
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
                | "POTTED"
                | "PLACED"
                | "CANCELED")
            : undefined,
        variety: inboundKeyword,
        page: inboundPage,
        size: inboundSize,
      }),
      getMaterials({
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
        page: materialPage,
        size: materialSize,
      }),
      fetchApi<House[]>("/houses"),
    ]);

  return (
    <InventoryPage
      initialActiveTab={readSearchParam(resolvedSearchParams, "tab")}
      houses={houses}
      initialInboundPage={inboundRecords}
      initialMaterialPage={materials}
      initialVarietyPage={varieties}
      varietyOptions={varietyOptions.content}
    />
  );
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
