import assert from "node:assert/strict";
import test from "node:test";
import {
  SALES_NAV_ITEMS,
  isSalesTab,
  salesHref,
} from "../src/shared/config/routes/sales.ts";
import { readSelectedBusinessPartnerId } from "../src/features/sales/lib/salesRouteParams.ts";

test("sales keeps auction settlement and adds a payment submenu", () => {
  assert.deepEqual(
    SALES_NAV_ITEMS.map((item) => item.href),
    [
      "/sales/slips",
      "/sales/auction",
      "/sales/settlement",
      "/sales/payments",
      "/sales/partners",
    ],
  );
  assert.equal(
    SALES_NAV_ITEMS.find((item) => item.tab === "settlement").label,
    "경매 정산",
  );
  assert.equal(
    SALES_NAV_ITEMS.find((item) => item.tab === "payments").label,
    "수납 관리",
  );
  assert.equal(isSalesTab("payments"), true);
  assert.equal(isSalesTab("settlement"), true);
  assert.equal(isSalesTab("constructor"), false);
  const url = new URL(
    salesHref("auction", { market: "서울 & 부산", lotId: 42 }),
    "http://localhost",
  );
  assert.equal(url.pathname, "/sales/auction");
  assert.equal(url.searchParams.get("market"), "서울 & 부산");
  assert.equal(url.searchParams.get("lotId"), "42");
});
test("partner detail selection survives reload and rejects invalid identifiers", () => {
  assert.equal(
    readSelectedBusinessPartnerId(new URLSearchParams("partnerId=7")),
    7,
  );
  for (const id of ["0", "-1", "1.5", "NaN", "9007199254740992"])
    assert.equal(
      readSelectedBusinessPartnerId(new URLSearchParams({ partnerId: id })),
      null,
    );
});
