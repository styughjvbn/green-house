import test from "node:test";
import assert from "node:assert/strict";
import {
  createEmptySalesItem,
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
const { withRecognizedSalesSlip, withConnectedSalesGroups } =
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

const selectedGroup = (id, varietyId = 42, overrides = {}) => ({
  id,
  varietyId,
  varietyName: "선택한 품종",
  genus: "선택한 속",
  availableQuantity: 10,
  houseNumber: 1,
  physicalBedNumber: 2,
  bedZoneName: "앞",
  ...overrides,
});

for (const extractedId of [null, 99]) {
  test(`photo group connection replaces ${extractedId == null ? "missing" : "incorrect"} AI identity through allocation and save`, () => {
    const initial = withRecognizedSalesSlip(
      createInitialSalesForm("2026-10-21"),
      {
        items: [
          {
            varietyId: extractedId,
            itemName: "오인식 품종",
            genus: "오인식 속",
            quantity: 3,
            unitPrice: 1250,
            spec: "중",
            unit: 1,
            boxes: 3,
            memo: "원문 메모",
          },
        ],
      },
    );
    initial.recordOnly = false;
    initial.items[0].fieldReviews = {
      ITEM_NAME: "품종 확인",
      GENUS: "속 확인",
      QUANTITY: "수량 확인",
    };
    const form = withConnectedSalesGroups(
      initial,
      [0],
      [selectedGroup(10), selectedGroup(11)],
    );
    const item = form.items[0];
    assert.equal(item.varietyId, "42");
    assert.equal(item.itemName, "선택한 품종");
    assert.equal(item.genus, "선택한 속");
    assert.ok(
      item.allocations.every((allocation) => allocation.varietyId === "42"),
    );
    assert.equal(salesAllocationIssue(form.items), null);
    assert.deepEqual(item.fieldReviews, { QUANTITY: "수량 확인" });
    const payload = toCreateSalesSlipPayload(form).items[0];
    assert.equal(payload.varietyId, 42);
    assert.equal(payload.quantity, 3);
    assert.equal(payload.unitPrice, 1250);
    assert.equal(payload.spec, "중");
    assert.equal(payload.memo, "원문 메모");
    assert.equal(
      payload.allocations.reduce(
        (sum, allocation) => sum + allocation.quantity,
        0,
      ),
      3,
    );
    assert.equal(initial.items[0].varietyId, extractedId == null ? "" : "99");
    assert.deepEqual(initial.items[0].allocations, []);
  });
}

test("photo group connection updates every split row and preserves other manual allocations", () => {
  const form = createInitialSalesForm("2026-10-21");
  form.items = [
    {
      ...createEmptySalesItem(),
      itemName: "오인식",
      quantity: "2",
      unitPrice: "1000",
    },
    {
      ...createEmptySalesItem(),
      varietyId: "99",
      itemName: "오인식",
      quantity: "3",
      unitPrice: "2000",
    },
    {
      ...createEmptySalesItem(),
      varietyId: "77",
      itemName: "다른 품종",
      genus: "다른 속",
      quantity: "4",
      allocationMode: "MANUAL",
      allocations: [
        {
          orchidGroupId: "20",
          varietyId: "77",
          varietyName: "다른 품종",
          genus: "다른 속",
          availableQuantity: 10,
          quantity: "4",
        },
      ],
    },
  ];
  const result = withConnectedSalesGroups(form, [0, 1], [selectedGroup(10)]);
  assert.deepEqual(
    result.items.map((item) => item.varietyId),
    ["42", "42", "77"],
  );
  assert.deepEqual(
    result.items.map((item) => item.unitPrice),
    ["1000", "2000", "0"],
  );
  assert.deepEqual(result.items[2], form.items[2]);
  assert.equal(salesAllocationIssue(result.items), null);
});

test("photo connection uses variety identity across name changes and rejects conflicting or unknown identities", () => {
  const form = createInitialSalesForm("2026-10-21");
  const sameId = withConnectedSalesGroups(
    form,
    [0],
    [
      selectedGroup(10),
      selectedGroup(11, 42, { varietyName: "다른 표기", genus: "변경된 속" }),
    ],
  );
  assert.equal(salesAllocationIssue(sameId.items), null);
  for (const groups of [
    [selectedGroup(10), selectedGroup(11, 99)],
    [selectedGroup(10), selectedGroup(11, null)],
    [selectedGroup(10, null)],
  ]) {
    assert.throws(() => withConnectedSalesGroups(form, [0], groups), /품종/);
  }
  assert.equal(withConnectedSalesGroups(form, [0], []), form);
});
