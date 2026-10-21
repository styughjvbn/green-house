import test from "node:test";
import assert from "node:assert/strict";
import {
  createInitialSalesForm,
  salesAllocationIssue,
  toCreateSalesSlipPayload,
} from "../src/features/sales/lib/salesForm.ts";
import { registerHooks } from "node:module";
const hooks = registerHooks({
  resolve(specifier, context, nextResolve) {
    return nextResolve(
      specifier.startsWith(".") && !/\.[a-z]+$/i.test(specifier)
        ? `${specifier}.ts`
        : specifier,
      context,
    );
  },
});
const { withRecognizedSalesSlip } =
  await import("../src/features/sales/lib/salesPhotoForm.ts");
const { createSalesSlipDraftActions } =
  await import("../src/features/sales/model/salesSlipDraftActions.ts");
hooks.deregister();

test("recognized variety IDs survive extraction, item splitting and the save payload", () => {
  let form = withRecognizedSalesSlip(createInitialSalesForm("2026-10-21"), {
    items: [
      {
        varietyId: 42,
        itemName: "품종 A",
        genus: "속 A",
        quantity: 2,
        unitPrice: 100,
      },
    ],
  });
  assert.equal(form.items[0].varietyId, "42");
  assert.equal(salesAllocationIssue(form.items, true), null);
  const actions = createSalesSlipDraftActions((update) => {
    form = update(form);
  });
  actions.addSalesItem(0);
  assert.equal(form.items[1].varietyId, "42");
  assert.equal(toCreateSalesSlipPayload(form).items[0].varietyId, 42);
  actions.updateItem(0, "itemName", "다른 품종");
  assert.equal(form.items[0].varietyId, "");
  assert.match(salesAllocationIssue(form.items, true), /품종을 선택/);
  assert.equal(form.items[1].varietyId, "42");
  assert.equal(toCreateSalesSlipPayload(form).items[0].varietyId, null);
});
