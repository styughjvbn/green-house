import type {
  BusinessPartner,
  SalesSlip,
  SalesOrchidGroupOption,
} from "@/entities/farm/types";
import type {
  CreateBusinessPartnerPayload,
  CreateSalesSlipPayload,
} from "../api/types";
import type {
  BusinessPartnerForm,
  SalesAllocationForm,
  SalesItemForm,
  SalesSlipForm,
} from "../model/types";

export function createEmptyBusinessPartnerForm(): BusinessPartnerForm {
  return {
    name: "",
    partnerType: "WHOLESALE",
    ownerName: "",
    phone: "",
    address: "",
    memo: "",
  };
}

export function toBusinessPartnerForm(
  partner: BusinessPartner,
): BusinessPartnerForm {
  return {
    name: partner.name,
    partnerType: partner.partnerType,
    ownerName: partner.ownerName ?? "",
    phone: partner.phone ?? "",
    address: partner.address ?? "",
    memo: partner.memo ?? "",
  };
}

export function createEmptySalesAllocation(): SalesAllocationForm {
  return {
    orchidGroupId: "",
    varietyName: "",
    genus: "",
    locationLabel: "",
    availableQuantity: 0,
    quantity: "1",
  };
}

export function createEmptySalesItem(): SalesItemForm {
  return {
    itemName: "",
    genus: "",
    spec: "",
    unit: "",
    boxes: "",
    quantity: "1",
    unitPrice: "0",
    memo: "",
    allocations: [],
    allocationMode: "AUTO",
  };
}

export function selectedSalesGroups(
  items: SalesItemForm[],
): SalesAllocationForm[] {
  const groups = new Map<string, SalesAllocationForm>();
  items
    .flatMap((item) => item.allocations)
    .forEach((allocation) => {
      if (!groups.has(allocation.orchidGroupId))
        groups.set(allocation.orchidGroupId, allocation);
    });
  return [...groups.values()];
}

/** Keep one selected group in the draft even when its automatic allocation is zero. */
export function addSelectedGroupsToSalesItems(
  items: SalesItemForm[],
  groups: SalesOrchidGroupOption[],
): SalesItemForm[] {
  const next = [...items];
  const selected = new Set(
    selectedSalesGroups(items).map((group) => group.orchidGroupId),
  );
  for (const group of groups) {
    const id = String(group.id);
    if (selected.has(id)) continue;
    const matches = (item: SalesItemForm) =>
      item.genus === group.genus && item.itemName === group.varietyName;
    let index = next.findIndex(matches);
    if (index < 0) {
      index = next.findIndex(
        (item) =>
          !item.itemName.trim() &&
          !item.genus.trim() &&
          item.allocations.length === 0,
      );
      if (index < 0) {
        index = next.length;
        next.push(createEmptySalesItem());
      }
      next[index] = {
        ...next[index],
        itemName: group.varietyName,
        genus: group.genus,
      };
    }
    next[index] = {
      ...next[index],
      allocations: [
        ...next[index].allocations,
        {
          orchidGroupId: id,
          varietyName: group.varietyName,
          genus: group.genus,
          locationLabel: `${group.houseNumber}동 ${group.physicalBedNumber}배드 ${group.bedZoneName}`,
          availableQuantity: group.availableQuantity,
          quantity: "0",
        },
      ],
    };
    selected.add(id);
  }
  return distributeSalesItems(next);
}

function weightedQuantities(
  weights: number[],
  target: number,
  ids: string[],
): number[] {
  const total = weights.reduce((sum, weight) => sum + weight, 0);
  const allocated = Math.min(target, total);
  if (allocated <= 0 || total <= 0) return weights.map(() => 0);
  const parts = weights.map((weight, index) => {
    const scaled = BigInt(allocated) * BigInt(weight);
    return {
      index,
      quantity: Number(scaled / BigInt(total)),
      remainder: scaled % BigInt(total),
    };
  });
  let leftover =
    allocated - parts.reduce((sum, part) => sum + part.quantity, 0);
  [...parts]
    .sort((a, b) =>
      a.remainder === b.remainder
        ? Number(ids[a.index]) - Number(ids[b.index])
        : a.remainder > b.remainder
          ? -1
          : 1,
    )
    .forEach((part) => {
      if (leftover > 0 && part.quantity < weights[part.index]) {
        part.quantity++;
        leftover--;
      }
    });
  return parts.map((part) => part.quantity);
}

export function distributeSalesItems(items: SalesItemForm[]): SalesItemForm[] {
  const groups = selectedSalesGroups(items);
  const remaining = new Map(
    groups.map((group) => [group.orchidGroupId, group.availableQuantity]),
  );
  // Keep adjusted and historical allocations fixed before calculating automatic quotas.
  items
    .filter((item) => item.allocationMode === "MANUAL")
    .flatMap((item) => item.allocations)
    .forEach((allocation) => {
      const id = allocation.orchidGroupId;
      remaining.set(
        id,
        (remaining.get(id) ?? 0) - (Number(allocation.quantity) || 0),
      );
    });
  const quotas = new Map<string, number>();
  const varieties = new Set(
    items
      .filter((item) => item.allocationMode === "AUTO")
      .map((item) => JSON.stringify([item.genus, item.itemName])),
  );
  for (const variety of varieties) {
    const [genus, name] = JSON.parse(variety) as [string, string];
    const eligible = groups.filter(
      (group) => group.genus === genus && group.varietyName === name,
    );
    const requested = items
      .filter(
        (item) =>
          item.allocationMode === "AUTO" &&
          item.genus === genus &&
          item.itemName === name,
      )
      .reduce((sum, item) => {
        const quantity = Number(item.quantity);
        return (
          sum + (Number.isSafeInteger(quantity) && quantity > 0 ? quantity : 0)
        );
      }, 0);
    const weights = eligible.map((group) =>
      Math.max(0, remaining.get(group.orchidGroupId) ?? 0),
    );
    const parts = weightedQuantities(
      weights,
      requested,
      eligible.map((group) => group.orchidGroupId),
    );
    eligible.forEach((group, index) =>
      quotas.set(group.orchidGroupId, parts[index]),
    );
  }
  return items.map((item) => {
    if (item.allocationMode === "MANUAL") return item;
    const eligible = groups.filter(
      (group) =>
        group.genus === item.genus && group.varietyName === item.itemName,
    );
    const requested = Number(item.quantity);
    const target =
      Number.isSafeInteger(requested) && requested > 0 ? requested : 0;
    const weights = eligible.map(
      (group) => quotas.get(group.orchidGroupId) ?? 0,
    );
    const parts = weightedQuantities(
      weights,
      target,
      eligible.map((group) => group.orchidGroupId),
    );
    const allocations = eligible.map((group, index) => {
      const quantity = parts[index];
      quotas.set(
        group.orchidGroupId,
        (quotas.get(group.orchidGroupId) ?? 0) - quantity,
      );
      return { ...group, quantity: String(quantity) };
    });
    return { ...item, allocations };
  });
}

export function salesAllocationIssue(items: SalesItemForm[]): string | null {
  const groups = selectedSalesGroups(items);
  const capacities = new Map(
    groups.map((group) => [group.orchidGroupId, group.availableQuantity]),
  );
  const used = new Map<string, number>();
  for (const item of items) {
    const quantity = Number(item.quantity);
    if (!Number.isSafeInteger(quantity) || quantity < 1)
      return "품목 수량은 1분 이상 입력하세요.";
    const allocations = item.allocations.filter(
      (allocation) => Number(allocation.quantity) > 0,
    );
    if (!allocations.length) return "각 품목에 난 묶음 배분이 필요합니다.";
    let sum = 0;
    for (const allocation of item.allocations) {
      const value = Number(allocation.quantity);
      if (!Number.isSafeInteger(value) || value < 0)
        return "배분 수량은 0 이상의 정수로 입력하세요.";
      if (
        allocation.genus !== item.genus ||
        allocation.varietyName !== item.itemName
      )
        return "품목과 난 묶음의 품종이 일치해야 합니다.";
      sum += value;
      const id = allocation.orchidGroupId;
      used.set(id, (used.get(id) ?? 0) + value);
    }
    if (sum !== quantity)
      return `${item.itemName} 품목의 배분 합계가 수량과 다릅니다.`;
  }
  for (const [id, quantity] of used) {
    if (quantity > (capacities.get(id) ?? 0))
      return "난 묶음의 배분 합계가 가용 수량을 초과합니다.";
  }
  return null;
}

export function createInitialSalesForm(businessDate: string): SalesSlipForm {
  return {
    salesType: "DIRECT",
    saleDate: businessDate,
    partnerId: "",
    paymentStatus: "미입금",
    salesStatus: "작성중",
    paymentMethod: "",
    receiptEnabled: false,
    receiptAmount: "",
    receiptDate: businessDate,
    receiptMethod: "CASH",
    memo: "",
    items: [createEmptySalesItem()],
  };
}

export function calculateSalesItemAmount(item: SalesItemForm): number {
  return Number(item.quantity || 0) * Number(item.unitPrice || 0);
}

export function calculateSalesItemAllocated(item: SalesItemForm): number {
  return item.allocations.reduce(
    (sum, allocation) => sum + Number(allocation.quantity || 0),
    0,
  );
}

export function calculateSalesTotal(items: SalesItemForm[]): number {
  return items.reduce((sum, item) => sum + calculateSalesItemAmount(item), 0);
}

export function nullableText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function toCreateBusinessPartnerPayload(
  form: BusinessPartnerForm,
): CreateBusinessPartnerPayload {
  return {
    name: form.name,
    partnerType: form.partnerType,
    ownerName: nullableText(form.ownerName),
    phone: nullableText(form.phone),
    address: nullableText(form.address),
    memo: nullableText(form.memo),
  };
}

export function toCreateSalesSlipPayload(
  form: SalesSlipForm,
): CreateSalesSlipPayload {
  return {
    // TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정.
    initialReceipt: null,
    salesType: form.salesType,
    saleDate: form.saleDate,
    partnerId: form.partnerId ? Number(form.partnerId) : null,
    auctionShipmentId: null,
    paymentStatus: form.paymentStatus,
    salesStatus: form.salesStatus,
    paymentMethod: nullableText(form.paymentMethod),
    memo: nullableText(form.memo),
    items: form.items.map((item) => ({
      itemName: item.itemName,
      genus: nullableText(item.genus),
      spec: nullableText(item.spec),
      unit: nullableText(item.unit),
      boxes: item.boxes ? Number(item.boxes) : null,
      quantity: Number(item.quantity),
      unitPrice: form.salesType === "AUCTION" ? 0 : Number(item.unitPrice),
      memo: nullableText(item.memo),
      allocations: item.allocations
        .filter((allocation) => Number(allocation.quantity) > 0)
        .map((allocation) => ({
          orchidGroupId: Number(allocation.orchidGroupId),
          quantity: Number(allocation.quantity),
        })),
    })),
  };
}

export function resetSalesSlipFormAfterSave(
  form: SalesSlipForm,
): SalesSlipForm {
  return {
    ...form,
    salesType: "DIRECT",
    paymentStatus: "미입금",
    salesStatus: "작성중",
    paymentMethod: "",
    receiptEnabled: false,
    receiptAmount: "",
    receiptMethod: "CASH",
    memo: "",
    items: [createEmptySalesItem()],
  };
}

export function toSalesSlipForm(salesSlip: SalesSlip): SalesSlipForm {
  return {
    salesType: salesSlip.salesType,
    saleDate: salesSlip.saleDate,
    partnerId: String(salesSlip.partner.id),
    paymentStatus: salesSlip.paymentStatus,
    salesStatus: salesSlip.salesStatus,
    paymentMethod: salesSlip.paymentMethod ?? "",
    receiptEnabled: false,
    receiptAmount: "",
    receiptDate: salesSlip.saleDate,
    receiptMethod: "CASH",
    memo: salesSlip.memo ?? "",
    items: normalizeEditedSalesItemCapacities(
      salesSlip.items.map((item) => ({
        itemName: item.itemName,
        genus: item.genus ?? "",
        spec: item.spec ?? "",
        unit: item.unit ?? "",
        boxes: item.boxes == null ? "" : String(item.boxes),
        quantity: String(item.quantity),
        unitPrice: String(item.unitPrice),
        memo: item.memo ?? "",
        allocationMode: "MANUAL" as const,
        allocations: item.allocations.map((allocation) => ({
          orchidGroupId: String(allocation.orchidGroupId),
          varietyName: allocation.varietyName,
          genus: item.genus ?? "",
          locationLabel: `${allocation.houseNumber}동 ${allocation.physicalBedNumber}배드 ${allocation.bedZoneName}`,
          availableQuantity:
            allocation.availableQuantity + allocation.allocatedQuantity,
          quantity: String(allocation.allocatedQuantity),
        })),
      })),
    ),
  };
}

function normalizeEditedSalesItemCapacities(
  items: SalesItemForm[],
): SalesItemForm[] {
  const free = new Map<string, number>();
  const allocated = new Map<string, number>();
  items
    .flatMap((item) => item.allocations)
    .forEach((allocation) => {
      const id = allocation.orchidGroupId;
      free.set(id, allocation.availableQuantity - Number(allocation.quantity));
      allocated.set(id, (allocated.get(id) ?? 0) + Number(allocation.quantity));
    });
  return items.map((item) => ({
    ...item,
    allocations: item.allocations.map((allocation) => ({
      ...allocation,
      availableQuantity:
        (free.get(allocation.orchidGroupId) ?? 0) +
        (allocated.get(allocation.orchidGroupId) ?? 0),
    })),
  }));
}
