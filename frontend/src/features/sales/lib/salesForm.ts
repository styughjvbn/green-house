import type { BusinessPartner, SalesSlip } from "@/entities/farm/types";
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
    quantity: "1",
    unitPrice: "0",
    memo: "",
    allocations: [],
  };
}

export function createInitialSalesForm(
  partners: BusinessPartner[],
  today = todayIsoDate(),
): SalesSlipForm {
  const directPartner = partners.find(
    (partner) => partner.partnerType !== "AUCTION_HOUSE",
  );
  return {
    salesType: "DIRECT",
    saleDate: today,
    partnerId: directPartner ? String(directPartner.id) : "",
    paymentStatus: "미입금",
    salesStatus: "작성중",
    paymentMethod: "",
    memo: "",
    items: [createEmptySalesItem()],
  };
}

export function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
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
      quantity: Number(item.quantity),
      unitPrice: Number(item.unitPrice),
      memo: nullableText(item.memo),
      allocations: item.allocations.map((allocation) => ({
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
    memo: salesSlip.memo ?? "",
    items: salesSlip.items.map((item) => ({
      itemName: item.itemName,
      genus: item.genus ?? "",
      spec: item.spec ?? "",
      quantity: String(item.quantity),
      unitPrice: String(item.unitPrice),
      memo: item.memo ?? "",
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
  };
}
