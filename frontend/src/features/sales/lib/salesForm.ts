import type { Customer, SalesSlip } from "@/entities/farm/types";
import type {
  CreateCustomerPayload,
  CreateSalesSlipPayload,
  CustomerForm,
  SalesFilterState,
  SalesItemForm,
  SalesSlipForm,
} from "../model/types";

export function createEmptyCustomerForm(): CustomerForm {
  return {
    name: "",
    ownerName: "",
    phone: "",
    address: "",
    memo: "",
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
  };
}

export function createInitialSalesForm(
  customers: Customer[],
  today = todayIsoDate(),
): SalesSlipForm {
  return {
    salesType: "DIRECT",
    saleDate: today,
    customerId: customers[0] ? String(customers[0].id) : "",
    auctionShipmentId: "",
    paymentStatus: "미입금",
    salesStatus: "작성중",
    paymentMethod: "",
    memo: "",
    items: [createEmptySalesItem()],
  };
}

export function createInitialSalesFilters(
  today = todayIsoDate(),
): SalesFilterState {
  const from = new Date(today);
  from.setDate(from.getDate() - 30);

  return {
    from: from.toISOString().slice(0, 10),
    to: today,
    customerId: "",
    paymentStatus: "",
    salesStatus: "",
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
    if (filters.from && slip.saleDate < filters.from) {
      return false;
    }
    if (filters.to && slip.saleDate > filters.to) {
      return false;
    }
    if (filters.customerId && String(slip.customer.id) !== filters.customerId) {
      return false;
    }
    if (filters.paymentStatus && slip.paymentStatus !== filters.paymentStatus) {
      return false;
    }
    if (filters.salesStatus && slip.salesStatus !== filters.salesStatus) {
      return false;
    }
    if (!keyword) {
      return true;
    }

    return [
      slip.slipNumber,
      slip.customer.name,
      slip.customer.ownerName,
      slip.customer.phone,
      slip.memo,
    ]
      .filter(Boolean)
      .some((value) => value?.toLowerCase().includes(keyword));
  });
}

export function calculateSalesItemAmount(item: SalesItemForm): number {
  return Number(item.quantity || 0) * Number(item.unitPrice || 0);
}

export function calculateSalesTotal(items: SalesItemForm[]): number {
  return items.reduce((sum, item) => sum + calculateSalesItemAmount(item), 0);
}

export function nullableText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function toCreateCustomerPayload(
  form: CustomerForm,
): CreateCustomerPayload {
  return {
    name: form.name,
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
    customerId: form.salesType === "DIRECT" ? Number(form.customerId) : null,
    auctionShipmentId:
      form.salesType === "AUCTION" ? Number(form.auctionShipmentId) : null,
    paymentStatus: form.paymentStatus,
    salesStatus: form.salesStatus,
    paymentMethod: nullableText(form.paymentMethod),
    memo: nullableText(form.memo),
    items: (form.salesType === "DIRECT" ? form.items : []).map((item) => ({
      itemName: item.itemName,
      genus: nullableText(item.genus),
      spec: nullableText(item.spec),
      quantity: Number(item.quantity),
      unitPrice: Number(item.unitPrice),
      memo: nullableText(item.memo),
    })),
  };
}

export function resetSalesSlipFormAfterSave(
  form: SalesSlipForm,
): SalesSlipForm {
  return {
    ...form,
    salesType: "DIRECT",
    auctionShipmentId: "",
    memo: "",
    items: [createEmptySalesItem()],
  };
}
