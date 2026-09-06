import assert from "node:assert/strict";
import test from "node:test";
import {
  createServerSearchParamReader,
  readAuctionRouteState,
  readBusinessPartnerRouteState,
  readCreateSlip,
  readSalesRouteState,
  readSettlementRouteState,
} from "../src/features/sales/lib/salesRouteParams.ts";

test("sales route state reads filters, paging, and create request", () => {
  const params = createServerSearchParamReader({
    from: "2026-07-01",
    partnerId: ["3", "5"],
    paymentStatus: "미입금",
    keyword: "거래처",
    page: "2",
    size: "25",
    slipId: "42",
    createSlip: "1",
  });

  assert.deepEqual(readSalesRouteState(params), {
    filters: {
      from: "2026-07-01",
      to: "",
      partnerId: "3",
      paymentStatus: "미입금",
      salesStatus: "",
      keyword: "거래처",
    },
    page: 2,
    size: 25,
    selectedSlipId: 42,
  });
  assert.equal(readCreateSlip(params), true);
});

test("settlement pages and detail selection have the same server and browser URL state", () => {
  const values = { page: "2", size: "20", settlementId: "42" };
  const expected = { page: 2, size: 20, selectedSettlementId: 42 };
  assert.deepEqual(
    readSettlementRouteState(createServerSearchParamReader(values)),
    expected,
  );
  assert.deepEqual(
    readSettlementRouteState(new URLSearchParams(values)),
    expected,
  );
});

test("settlement URLs normalize page bounds and reject invalid detail identifiers", () => {
  assert.deepEqual(readSettlementRouteState(new URLSearchParams()), {
    page: 0,
    size: 10,
    selectedSettlementId: null,
  });
  assert.deepEqual(
    readSettlementRouteState(
      new URLSearchParams("page=-1&size=200&settlementId=-2"),
    ),
    {
      page: 0,
      size: 100,
      selectedSettlementId: null,
    },
  );
  for (const value of ["NaN", "Infinity", "1.5", "not-a-number"]) {
    assert.deepEqual(
      readSettlementRouteState(
        new URLSearchParams({ page: value, size: value, settlementId: value }),
      ),
      {
        page: 0,
        size: 10,
        selectedSettlementId: null,
      },
    );
  }
  assert.equal(
    readSettlementRouteState(new URLSearchParams("page=9999999999&size=0"))
      .page,
    2_147_483_647,
  );
  assert.equal(readSettlementRouteState(new URLSearchParams("size=0")).size, 1);
});

test("sales route state ignores invalid selected slip identifiers", () => {
  for (const slipId of ["0", "-1", "1.5", "not-a-number"]) {
    const state = readSalesRouteState(
      createServerSearchParamReader({ slipId }),
    );
    assert.equal(state.selectedSlipId, null);
  }
});

test("partner and auction route state reject unsupported enum values", () => {
  const params = createServerSearchParamReader({
    partnerType: "INVALID",
    active: "INACTIVE",
    status: "UNKNOWN",
    reviewOnly: "true",
    returnOnly: "false",
    waitingOnly: "true",
    page: "-1",
    size: "200",
  });

  assert.deepEqual(readBusinessPartnerRouteState(params), {
    filters: {
      partnerType: "",
      active: "INACTIVE",
      keyword: "",
    },
    page: 0,
    size: 100,
  });
  assert.deepEqual(readAuctionRouteState(params), {
    filters: {
      from: "",
      to: "",
      market: "",
      variety: "",
      grade: "",
      status: "",
      keyword: "",
      reviewOnly: true,
      returnOnly: false,
      waitingOnly: true,
    },
    page: 0,
    size: 100,
  });
});
