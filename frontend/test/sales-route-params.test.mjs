import assert from "node:assert/strict";
import test from "node:test";
import {
  createServerSearchParamReader,
  readAuctionRouteState,
  readBusinessPartnerRouteState,
  readCreateSlip,
  readSalesRouteState,
  readProceedsRouteState,
  readPaymentHistoryPage,
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

test("proceeds pages and detail selection have the same server and browser URL state", () => {
  const values = { page: "2", size: "20", proceedsId: "42" };
  const expected = { page: 2, size: 20, selectedProceedsId: 42 };
  assert.deepEqual(
    readProceedsRouteState(createServerSearchParamReader(values)),
    expected,
  );
  assert.deepEqual(
    readProceedsRouteState(new URLSearchParams(values)),
    expected,
  );
});

test("payment history URL distinguishes closed, first, and later pages", () => {
  assert.equal(readPaymentHistoryPage(new URLSearchParams()), null);
  for (const value of ["", "0", "-1", "invalid", "Infinity", "1.5"]) {
    assert.equal(
      readPaymentHistoryPage(new URLSearchParams({ paymentPage: value })),
      0,
    );
  }
  assert.equal(readPaymentHistoryPage(new URLSearchParams("paymentPage=2")), 2);
  assert.equal(
    readPaymentHistoryPage(
      createServerSearchParamReader({ paymentPage: ["2", "4"] }),
    ),
    2,
  );
});

test("proceeds URLs normalize page bounds and reject invalid detail identifiers", () => {
  assert.deepEqual(readProceedsRouteState(new URLSearchParams()), {
    page: 0,
    size: 10,
    selectedProceedsId: null,
  });
  assert.deepEqual(
    readProceedsRouteState(
      new URLSearchParams("page=-1&size=200&proceedsId=-2"),
    ),
    {
      page: 0,
      size: 100,
      selectedProceedsId: null,
    },
  );
  for (const value of ["NaN", "Infinity", "1.5", "not-a-number"]) {
    assert.deepEqual(
      readProceedsRouteState(
        new URLSearchParams({ page: value, size: value, proceedsId: value }),
      ),
      {
        page: 0,
        size: 10,
        selectedProceedsId: null,
      },
    );
  }
  assert.equal(
    readProceedsRouteState(new URLSearchParams("page=9999999999&size=0")).page,
    2_147_483_647,
  );
  assert.equal(readProceedsRouteState(new URLSearchParams("size=0")).size, 1);
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
    selectedLotId: null,
    arrivalPage: 0,
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

test("legacy settlement identifiers do not select a proceeds target", () => {
  assert.equal(
    readProceedsRouteState(new URLSearchParams("settlementId=123"))
      .selectedProceedsId,
    null,
  );
});

test("auction selection and arrival history survive URL reload and reject invalid identifiers", () => {
  const state = readAuctionRouteState(
    new URLSearchParams("lotId=42&arrivalPage=2"),
  );
  assert.equal(state.selectedLotId, 42);
  assert.equal(state.arrivalPage, 2);
  for (const value of ["0", "-1", "NaN", "1.5", "9007199254740992"]) {
    assert.equal(
      readAuctionRouteState(new URLSearchParams({ lotId: value }))
        .selectedLotId,
      null,
    );
  }
  assert.equal(
    readAuctionRouteState(new URLSearchParams("arrivalPage=-1")).arrivalPage,
    0,
  );
});

test("v2 proceeds paging is independent from lot filters and paging", () => {
  const params = new URLSearchParams(
    "page=3&size=20&proceedsPage=1&proceedsSize=50&lotId=42&proceedsId=12&market=서울",
  );
  assert.deepEqual(readProceedsRouteState(params, "auction"), {
    page: 1,
    size: 50,
    selectedProceedsId: 12,
  });
  assert.equal(readAuctionRouteState(params).page, 3);
  assert.equal(readAuctionRouteState(params).selectedLotId, 42);
  assert.deepEqual(readProceedsRouteState(params), {
    page: 3,
    size: 20,
    selectedProceedsId: 12,
  });
});
