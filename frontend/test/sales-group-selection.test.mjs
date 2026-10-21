import test from "node:test";
import assert from "node:assert/strict";
import {
  addSelectedGroupsToSalesItems,
  createEmptySalesItem,
  createInitialSalesForm,
  distributeSalesItems,
  salesAllocationIssue,
  selectedSalesGroups,
  toCreateSalesSlipPayload,
  toSalesSlipForm,
} from "../src/features/sales/lib/salesForm.ts";

const group = (
  id,
  availableQuantity,
  varietyName = "품종 A",
  genus = "호접란",
) => ({
  id,
  varietyId: varietyName === "품종 A" ? 42 : 43,
  varietyName,
  genus,
  availableQuantity,
  houseNumber: 1,
  physicalBedNumber: 2,
  bedZoneName: "앞",
});
const quantities = (item) =>
  Object.fromEntries(
    item.allocations.map((line) => [line.orchidGroupId, Number(line.quantity)]),
  );

test("one selection creates items by variety and allocates each requested quantity", () => {
  const items = addSelectedGroupsToSalesItems(
    [createEmptySalesItem()],
    [group(1, 100), group(2, 50), group(3, 40, "품종 B")],
  );
  assert.deepEqual(
    items.map((item) => item.itemName),
    ["품종 A", "품종 B"],
  );
  assert.equal(selectedSalesGroups(items).length, 3);
  assert.deepEqual(quantities(items[0]), { 1: 1, 2: 0 });
  assert.deepEqual(quantities(items[1]), { 3: 1 });
  assert.equal(salesAllocationIssue(items), null);
});

test("auction slip ignores a price left in the draft while direct slip keeps it", () => {
  const direct = createInitialSalesForm("2026-10-10");
  direct.items[0].unitPrice = "12500";
  assert.equal(toCreateSalesSlipPayload(direct).items[0].unitPrice, 12500);
  assert.equal(
    toCreateSalesSlipPayload({ ...direct, salesType: "AUCTION" }).items[0]
      .unitPrice,
    0,
  );
});

test("two price or grade items share group capacity in proportion, including integer remainders", () => {
  const first = addSelectedGroupsToSalesItems(
    [createEmptySalesItem()],
    [group(1, 100), group(2, 50)],
  )[0];
  const second = {
    ...createEmptySalesItem(),
    varietyId: first.varietyId,
    genus: first.genus,
    itemName: first.itemName,
    spec: "B급",
    quantity: "40",
  };
  const items = distributeSalesItems([
    { ...first, spec: "A급", quantity: "90" },
    second,
  ]);
  assert.deepEqual(quantities(items[0]), { 1: 60, 2: 30 });
  assert.deepEqual(quantities(items[1]), { 1: 27, 2: 13 });
  assert.equal(salesAllocationIssue(items), null);
  const payload = toCreateSalesSlipPayload({
    ...createInitialSalesForm("2026-10-10"),
    items,
  });
  assert.deepEqual(
    payload.items.map((item) => item.allocations),
    [
      [
        { orchidGroupId: 1, quantity: 60 },
        { orchidGroupId: 2, quantity: 30 },
      ],
      [
        { orchidGroupId: 1, quantity: 27 },
        { orchidGroupId: 2, quantity: 13 },
      ],
    ],
  );
});

test("whole variety quota avoids assigning all small items to the same group", () => {
  const first = addSelectedGroupsToSalesItems(
    [createEmptySalesItem()],
    [group(1, 80), group(2, 50)],
  )[0];
  const second = {
    ...createEmptySalesItem(),
    varietyId: first.varietyId,
    genus: first.genus,
    itemName: first.itemName,
  };
  const items = distributeSalesItems([first, second]);
  assert.deepEqual(quantities(items[0]), { 1: 1, 2: 0 });
  assert.deepEqual(quantities(items[1]), { 1: 0, 2: 1 });
});

test("manual allocation stays fixed, remaining automatic item adjusts, overcapacity is rejected", () => {
  const first = addSelectedGroupsToSalesItems(
    [createEmptySalesItem()],
    [group(1, 100), group(2, 50)],
  )[0];
  const second = {
    ...createEmptySalesItem(),
    varietyId: first.varietyId,
    genus: first.genus,
    itemName: first.itemName,
    quantity: "40",
  };
  const items = distributeSalesItems([{ ...first, quantity: "90" }, second]);
  const adjusted = distributeSalesItems([
    {
      ...items[0],
      allocationMode: "MANUAL",
      allocations: [
        { ...items[0].allocations[0], quantity: "50" },
        { ...items[0].allocations[1], quantity: "40" },
      ],
    },
    items[1],
  ]);
  assert.deepEqual(quantities(adjusted[0]), { 1: 50, 2: 40 });
  assert.deepEqual(quantities(adjusted[1]), { 1: 33, 2: 7 });
  assert.equal(salesAllocationIssue(adjusted), null);
  const tooMuch = distributeSalesItems([
    { ...items[0], quantity: "120" },
    { ...items[1], quantity: "40" },
  ]);
  assert.match(salesAllocationIssue(tooMuch), /배분 합계/);
});

test("zero draft allocations are omitted from the server payload", () => {
  const items = addSelectedGroupsToSalesItems(
    [createEmptySalesItem()],
    [group(1, 100), group(2, 50)],
  );
  const payload = toCreateSalesSlipPayload({
    ...createInitialSalesForm("2026-10-10"),
    items,
  });
  assert.deepEqual(payload.items[0].allocations, [
    { orchidGroupId: 1, quantity: 1 },
  ]);
});

test("editing a slip restores shared group capacity and preserves its historical split", () => {
  const line = (quantity) => ({
    orchidGroupId: 1,
    varietyName: "품종 A",
    houseNumber: 1,
    physicalBedNumber: 2,
    bedZoneName: "앞",
    availableQuantity: 20,
    allocatedQuantity: quantity,
  });
  const slip = {
    salesType: "DIRECT",
    saleDate: "2026-10-10",
    partner: { id: 5 },
    paymentStatus: "미입금",
    salesStatus: "작성중",
    items: [
      {
        varietyId: 42,
        itemName: "품종 A",
        genus: "호접란",
        spec: "A급",
        quantity: 30,
        unitPrice: 1000,
        allocations: [line(30)],
      },
      {
        varietyId: 42,
        itemName: "품종 A",
        genus: "호접란",
        spec: "B급",
        quantity: 20,
        unitPrice: 500,
        allocations: [line(20)],
      },
    ],
  };
  const form = toSalesSlipForm(slip);
  assert.deepEqual(
    form.items.map((item) => item.allocations[0].availableQuantity),
    [70, 70],
  );
  assert.deepEqual(
    form.items.map((item) => item.allocations[0].quantity),
    ["30", "20"],
  );
  assert.equal(salesAllocationIssue(form.items), null);
  const changed = distributeSalesItems([
    { ...form.items[0], quantity: "40", allocationMode: "AUTO" },
    form.items[1],
  ]);
  assert.deepEqual(
    changed.map((item) => item.allocations[0].quantity),
    ["40", "20"],
  );
});

test("variety identity survives group linking and renames without replacing the item snapshot", () => {
  const original = {
    ...createEmptySalesItem(),
    varietyId: "42",
    itemName: "과거 이름",
    genus: "과거 속",
    quantity: "2",
  };
  const items = addSelectedGroupsToSalesItems(
    [original],
    [{ ...group(1, 10, "현재 이름", "현재 속"), varietyId: 42 }],
  );
  assert.equal(items.length, 1);
  assert.equal(items[0].varietyId, "42");
  assert.equal(items[0].itemName, "과거 이름");
  assert.equal(items[0].genus, "과거 속");
  assert.equal(salesAllocationIssue(items), null);
  const form = { ...createInitialSalesForm("2026-10-21"), items };
  assert.equal(toCreateSalesSlipPayload(form).items[0].varietyId, 42);
});
