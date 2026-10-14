import assert from "node:assert/strict";
import test from "node:test";
import {
  SALES_NAV_ITEMS,
  isSalesTab,
  salesHref,
} from "../src/shared/config/routes/sales.ts";
import { readSelectedBusinessPartnerId } from "../src/features/sales/lib/salesRouteParams.ts";

test("sales exposes only supported menus and rejects paused routes", () => {
  assert.deepEqual(
    SALES_NAV_ITEMS.map((item) => item.href),
    ["/sales/slips", "/sales/auction", "/sales/partners"],
  );
  for (const tab of ["slips", "auction", "partners"])
    assert.equal(isSalesTab(tab), true);
  for (const tab of ["payments", "settlement", "constructor", "unknown"])
    assert.equal(isSalesTab(tab), false);
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
