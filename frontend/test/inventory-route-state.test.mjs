import assert from "node:assert/strict";
import test from "node:test";
import {
  createServerSearchParamReader,
  readInboundRouteState,
  readMaterialRouteState,
  readVarietyRouteState,
} from "../src/features/inventory/lib/inventoryRouteState.ts";

test("material route state parses filters and bounded pagination", () => {
  const params = createServerSearchParamReader({
    materialCategory: ["농약", "비료"],
    materialKeyword: "살균제",
    materialManufacturer: "제조사",
    materialStatus: "ACTIVE",
    page: "2",
    size: "25",
  });

  assert.deepEqual(readMaterialRouteState(params), {
    filters: {
      category: "농약",
      keyword: "살균제",
      manufacturer: "제조사",
      status: "ACTIVE",
    },
    page: 2,
    size: 25,
  });
});

test("variety route state normalizes legacy values and invalid pagination", () => {
  const params = createServerSearchParamReader({
    varietyGenus: "전체",
    varietySale: "사용",
    varietyStatus: "UNKNOWN",
    page: "-1",
    size: "200",
  });

  assert.deepEqual(readVarietyRouteState(params), {
    filters: {
      genus: "",
      keyword: "",
      status: "",
      saleEnabled: "true",
    },
    page: 0,
    size: 100,
  });
});

test("inbound route state accepts only supported enum values", () => {
  const params = createServerSearchParamReader({
    inboundType: "PRODUCT_POT",
    inboundStatus: "PLACED",
    inboundKeyword: "호접란",
  });

  assert.deepEqual(readInboundRouteState(params), {
    filters: {
      inboundType: "PRODUCT_POT",
      status: "PLACED",
      keyword: "호접란",
    },
    page: 0,
    size: 10,
  });
});
