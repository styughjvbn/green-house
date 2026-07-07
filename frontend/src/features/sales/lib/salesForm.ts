import type { BusinessPartner, SalesSlip } from "@/entities/farm/types";
import type {
  BusinessPartnerFilterState,
  BusinessPartnerForm,
  CreateBusinessPartnerPayload,
  CreateSalesSlipPayload,
  SalesAllocationForm,
  SalesFilterState,
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

export function createInitialSalesFilters(): SalesFilterState {
  return {
    from: "",
    to: "",
    partnerId: "",
    paymentStatus: "",
    salesStatus: "",
    keyword: "",
  };
}

export function createInitialBusinessPartnerFilters(): BusinessPartnerFilterState {
  return {
    partnerType: "",
    active: "",
    keyword: "",
  };
}

export function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

export function filterSalesSlips(
  salesSlips: SalesSlip[],
  filters: SalesFilterState,
): SalesSlip[] {
  const keyword = filters.keyword.trim().toLowerCase();

  return salesSlips.filter((slip) => {
    if (filters.from && slip.saleDate < filters.from) return false;
    if (filters.to && slip.saleDate > filters.to) return false;
    if (filters.partnerId && String(slip.partner.id) !== filters.partnerId)
      return false;
    if (filters.paymentStatus && slip.paymentStatus !== filters.paymentStatus)
      return false;
    if (filters.salesStatus && slip.salesStatus !== filters.salesStatus)
      return false;
    if (!keyword) return true;

    return [
      slip.slipNumber,
      slip.partner.name,
      slip.partner.ownerName,
      slip.partner.phone,
      slip.memo,
    ]
      .filter(Boolean)
      .some((value) => value?.toLowerCase().includes(keyword));
  });
}

export function filterBusinessPartners(
  partners: BusinessPartner[],
  filters: BusinessPartnerFilterState,
): BusinessPartner[] {
  const keyword = filters.keyword.trim().toLowerCase();

  return partners.filter((partner) => {
    if (filters.partnerType && partner.partnerType !== filters.partnerType)
      return false;
    if (filters.active) {
      const active = filters.active === "ACTIVE";
      if (partner.active !== active) return false;
    }
    if (!keyword) return true;

    return [
      partner.name,
      partner.ownerName,
      partner.phone,
      partner.address,
      partner.memo,
    ]
      .filter(Boolean)
      .some((value) => value?.toLowerCase().includes(keyword));
  });
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
