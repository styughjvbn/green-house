import test from "node:test";
import assert from "node:assert/strict";
import {
  createInitialSalesForm,
  toCreateSalesSlipPayload,
  resetSalesSlipFormAfterSave,
} from "../src/features/sales/lib/salesForm.ts";
import { salesPaymentLabel } from "../src/features/sales/lib/salesDisplay.ts";

test("paused receipts are excluded even when an old draft enables collection", () => {
  const form = createInitialSalesForm("2026-10-09");
  assert.equal(toCreateSalesSlipPayload(form).initialReceipt, null);
  const receipt = {
    ...form,
    receiptEnabled: true,
    receiptAmount: "2000",
    receiptMethod: "CASH",
  };
  for (const salesStatus of ["작성중", "출고 완료"]) {
    const payload = toCreateSalesSlipPayload({ ...receipt, salesStatus });
    assert.equal(payload.initialReceipt, null);
    assert.equal(payload.salesStatus, salesStatus);
    assert.equal(payload.paymentStatus, "미입금");
    assert.equal(payload.paymentMethod, null);
  }
  assert.equal(
    toCreateSalesSlipPayload({ ...receipt, salesType: "AUCTION" })
      .initialReceipt,
    null,
  );
  assert.equal(resetSalesSlipFormAfterSave(receipt).receiptEnabled, false);
});

test("receipt labels preserve backend stored status values", () => {
  for (const [value, label] of [
    ["미입금", "미수납"],
    ["부분입금", "일부 수납"],
    ["부분 입금", "일부 수납"],
    ["입금 완료", "수납 완료"],
    ["정산 대기", "정산 대기"],
  ])
    assert.equal(salesPaymentLabel(value), label);
});
