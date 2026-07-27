import assert from "node:assert/strict";
import test from "node:test";
import {
  createServerSearchParamReader,
  readAuctionRouteState,
  readBusinessPartnerRouteState,
  readCreateSlip,
  readSalesRouteState,
} from "../src/features/sales/lib/salesRouteParams.ts";

test("sales route state reads filters, paging, and create request", () => {
  const params = createServerSearchParamReader({
    from: "2026-07-01",
    partnerId: ["3", "5"],
    paymentStatus: "미입금",
    keyword: "거래처",
    page: "2",
    size: "25",
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
  });
  assert.equal(readCreateSlip(params), true);
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
