import test from "node:test";
import assert from "node:assert/strict";
import {
  createInitialSalesForm,
  salesAllocationIssue,
  toCreateSalesSlipPayload,
  resetSalesSlipFormAfterSave,
  withSalesRecordingMode,
} from "../src/features/sales/lib/salesForm.ts";

test("completed records preserve facts entered after selecting the registration mode", () => {
  for (const salesType of ["DIRECT", "AUCTION"]) {
    const original = createInitialSalesForm("2026-10-12");
    original.salesType = salesType;
    original.saleDate = "2026-09-01";
    original.items[0] = {
      ...original.items[0],
      varietyId: "42",
      itemName: "미니퍼플",
      genus: "팔레놉시스",
      quantity: "150",
      unitPrice: "1000",
      unit: "10",
      boxes: "3",
      allocations: [
        { orchidGroupId: "12", quantity: "150", availableQuantity: 10 },
      ],
    };
    const form = withSalesRecordingMode(original, true);
    assert.equal(
      form.salesStatus,
      salesType === "DIRECT" ? "출고 완료" : "출하 완료",
    );
    assert.equal(form.saleDate, "2026-09-01");
    assert.equal(form.items[0].itemName, "");
    assert.deepEqual(form.items[0].allocations, []);
    // Registration modes use different item inputs. Enter recorded facts after switching.
    form.items[0] = { ...original.items[0], allocations: [] };
    assert.equal(salesAllocationIssue(form.items, form.recordOnly), null);
    const payload = toCreateSalesSlipPayload(form);
    assert.equal(payload.recordOnly, true);
    assert.equal(payload.initialReceipt, null);
    assert.equal(payload.items[0].quantity, 150);
    assert.equal(payload.items[0].boxes, 3);
    assert.equal(payload.items[0].unit, 10);
    assert.equal(payload.items[0].unitPrice, salesType === "DIRECT" ? 1000 : 0);
    assert.deepEqual(payload.items[0].allocations, []);
    assert.equal(resetSalesSlipFormAfterSave(form).recordOnly, false);
    const normal = withSalesRecordingMode(form, false);
    assert.equal(normal.salesStatus, "작성중");
    assert.equal(normal.items[0].itemName, "");
    assert.match(salesAllocationIssue(normal.items), /난 묶음 배분/);
    assert.equal(toCreateSalesSlipPayload(normal).recordOnly, undefined);
    // Changing registration mode must not mutate the old draft.
    assert.equal(original.items[0].allocations.length, 1);
  }
});

test("selecting the current registration mode preserves the draft", () => {
  const form = withSalesRecordingMode(
    createInitialSalesForm("2026-10-12"),
    true,
  );
  form.items[0].itemName = "미니퍼플";
  form.items[0].quantity = "150";
  assert.equal(withSalesRecordingMode(form, true), form);
  assert.equal(form.items[0].quantity, "150");
});

test("recorded items still require a variety name and positive integer quantity", () => {
  const form = withSalesRecordingMode(
    createInitialSalesForm("2026-10-12"),
    true,
  );
  assert.match(salesAllocationIssue(form.items, true), /품종명/);
  form.items[0].itemName = "미니퍼플";
  assert.match(salesAllocationIssue(form.items, true), /품종을 선택/);
  form.items[0].varietyId = "42";
  assert.equal(salesAllocationIssue(form.items, true), null);
  for (const quantity of ["0", "-1", "1.5", "", "abc"])
    assert.match(
      salesAllocationIssue([{ ...form.items[0], quantity }], true),
      /품목 수량/,
    );
});

test("record payload never forwards stale allocation selections", () => {
  const form = withSalesRecordingMode(
    createInitialSalesForm("2026-10-12"),
    true,
  );
  form.items[0].allocations = [{ orchidGroupId: "12", quantity: "1" }];
  assert.deepEqual(toCreateSalesSlipPayload(form).items[0].allocations, []);
});
