import assert from "node:assert/strict";
import test from "node:test";
import { createInitialSalesForm } from "../src/features/sales/lib/salesForm.ts";

test("sales form uses the business date supplied by the backend context", () => {
  const form = createInitialSalesForm("2026-07-22");

  assert.equal(form.saleDate, "2026-07-22");
  assert.equal(form.partnerId, "");
});
