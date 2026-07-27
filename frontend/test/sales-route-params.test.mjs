import assert from "node:assert/strict";
import test from "node:test";
import {
  readBooleanParam,
  readEnumParam,
  readPageParam,
  readPageSizeParam,
  readSearchParam,
} from "../src/features/sales/lib/salesRouteParams.ts";

test("page and size parameters are bounded", () => {
  assert.equal(readPageParam({ page: "-1" }), 0);
  assert.equal(readPageParam({ page: "3" }), 3);
  assert.equal(readPageParam({ page: "1.5" }), 0);
  assert.equal(readPageSizeParam({ size: "0" }, 10), 1);
  assert.equal(readPageSizeParam({ size: "200" }, 10), 100);
  assert.equal(readPageSizeParam({ size: "invalid" }, 20), 20);
});

test("single, repeated, and boolean parameters are read consistently", () => {
  assert.equal(
    readSearchParam({ keyword: ["first", "second"] }, "keyword"),
    "first",
  );
  assert.equal(readBooleanParam({ reviewOnly: "true" }, "reviewOnly"), true);
  assert.equal(readBooleanParam({ reviewOnly: "false" }, "reviewOnly"), false);
  assert.equal(
    readEnumParam({ partnerType: "RETAIL" }, "partnerType", [
      "WHOLESALE",
      "RETAIL",
    ]),
    "RETAIL",
  );
  assert.equal(
    readEnumParam({ partnerType: "INVALID" }, "partnerType", [
      "WHOLESALE",
      "RETAIL",
    ]),
    "",
  );
});
