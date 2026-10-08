import assert from "node:assert/strict";
import test from "node:test";
import {
  SALES_NAV_ITEMS,
  SALES_V2_NAV_ITEMS,
  isSalesV2Tab,
  salesV2Href,
} from "../src/shared/config/routes/sales.ts";
import { readSelectedBusinessPartnerId } from "../src/features/sales/lib/salesRouteParams.ts";

test("v2 has four distinct menus and leaves legacy URLs intact", () => {
  assert.deepEqual(
    SALES_V2_NAV_ITEMS.map((item) => item.label),
    ["전표", "경매", "입금", "거래처"],
  );
  assert.deepEqual(
    SALES_NAV_ITEMS.map((item) => item.href),
    ["/sales/slips", "/sales/auction", "/sales/settlement", "/sales/partners"],
  );
  assert.equal(isSalesV2Tab("payments"), true);
  assert.equal(isSalesV2Tab("settlement"), false);
  assert.equal(isSalesV2Tab("constructor"), false);
  const url = new URL(
    salesV2Href("auction", { market: "서울 & 부산", lotId: 42 }),
    "http://localhost",
  );
  assert.equal(url.pathname, "/sales-v2/auction");
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
