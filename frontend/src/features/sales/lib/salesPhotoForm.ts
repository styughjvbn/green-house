import type { SalesSlipPhotoExtraction } from "../api/types";
import type { SalesSlipForm } from "../model/types";
import { createEmptySalesItem } from "./salesForm";

export function withRecognizedSalesSlip(
  form: SalesSlipForm,
  result: SalesSlipPhotoExtraction,
  partnerId?: string,
): SalesSlipForm {
  const date = result.saleDate;
  const validDate = Boolean(
    date &&
    /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    !Number.isNaN(Date.parse(date)) &&
    new Date(date).toISOString().slice(0, 10) === date,
  );
  const items = result.items?.map((item) => ({
    ...createEmptySalesItem(),
    itemName: item.itemName ?? "",
    genus: item.genus ?? "",
    spec: item.spec ?? "",
    unit: item.unit == null ? "" : String(item.unit),
    memo: item.memo ?? "",
    boxes: item.boxes != null && item.boxes > 0 ? String(item.boxes) : "",
    quantity:
      item.quantity != null && item.quantity > 0 ? String(item.quantity) : "",
    unitPrice:
      form.salesType === "AUCTION"
        ? "0"
        : item.unitPrice != null && item.unitPrice >= 0
          ? String(item.unitPrice)
          : "",
  }));
  return {
    ...form,
    saleDate: validDate ? date! : form.saleDate,
    partnerId: partnerId ?? form.partnerId,
    memo: result.memo ?? form.memo,
    items: items?.length ? items : form.items,
  };
}
