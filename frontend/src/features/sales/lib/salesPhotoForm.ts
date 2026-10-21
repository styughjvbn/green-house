import type { SalesOrchidGroupOption } from "@/entities/farm/types";
import type { SalesSlipPhotoExtraction } from "../api/types";
import type {
  SalesSlipForm,
  SalesItemForm,
  SalesReviewField,
  SalesFieldReviews,
} from "../model/types";
import { createEmptySalesItem, distributeSalesItems } from "./salesForm";

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
  const reviewsFor = (itemIndex: number | null): SalesFieldReviews =>
    Object.fromEntries(
      (result.reviewFields ?? [])
        .filter(
          (review) =>
            (review.itemIndex ?? null) === itemIndex &&
            review.field &&
            review.reason &&
            !(
              form.salesType === "AUCTION" &&
              ["UNIT_PRICE", "AMOUNT"].includes(review.field)
            ),
        )
        .map((review) => [review.field, review.reason]),
    );
  const items = result.items?.map((item, index) => ({
    ...createEmptySalesItem(),
    fieldReviews: reviewsFor(index),
    varietyId: item.varietyId == null ? "" : String(item.varietyId),
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
    fieldReviews: reviewsFor(null),
    saleDate: validDate ? date! : form.saleDate,
    partnerId: partnerId ?? form.partnerId,
    memo: result.memo ?? form.memo,
    items: items?.length ? items : form.items,
  };
}

const formFields: Partial<Record<SalesReviewField, keyof SalesSlipForm>> = {
  SALE_DATE: "saleDate",
  PARTNER: "partnerId",
  SALES_TYPE: "salesType",
  SLIP_MEMO: "memo",
};
const itemFields: Partial<Record<SalesReviewField, keyof SalesItemForm>> = {
  ITEM_NAME: "itemName",
  GENUS: "genus",
  GRADE: "spec",
  UNIT: "unit",
  BOXES: "boxes",
  QUANTITY: "quantity",
  UNIT_PRICE: "unitPrice",
  ITEM_MEMO: "memo",
};

function clearChanged<T extends object>(
  before: T,
  after: T,
  reviews: SalesFieldReviews | undefined,
  fields: Partial<Record<SalesReviewField, keyof T>>,
): SalesFieldReviews | undefined {
  if (!reviews) return reviews;
  return Object.fromEntries(
    Object.entries(reviews).filter(([field]) => {
      const key = fields[field as SalesReviewField];
      return key == null || before[key] === after[key];
    }),
  );
}

export function clearEditedSalesReviews(
  before: SalesSlipForm,
  after: SalesSlipForm,
): SalesSlipForm {
  return {
    ...after,
    fieldReviews:
      before.fieldReviews === after.fieldReviews
        ? clearChanged(before, after, after.fieldReviews, formFields)
        : after.fieldReviews,
    items: after.items.map((item) => {
      // Follow the review object across row insertion/removal; new extraction marks must survive.
      const previous =
        item.fieldReviews &&
        before.items.find((row) => row.fieldReviews === item.fieldReviews);
      if (!previous) return item;
      const fieldReviews = clearChanged(
        previous,
        item,
        item.fieldReviews,
        itemFields,
      );
      if (
        fieldReviews &&
        (previous.quantity !== item.quantity ||
          previous.unitPrice !== item.unitPrice)
      )
        delete fieldReviews.AMOUNT;
      if (fieldReviews && after.salesType === "AUCTION") {
        delete fieldReviews.UNIT_PRICE;
        delete fieldReviews.AMOUNT;
      }
      return { ...item, fieldReviews };
    }),
  };
}

export function confirmSalesReview(
  form: SalesSlipForm,
  field: SalesReviewField,
  indices?: number[],
): SalesSlipForm {
  const clear = (reviews: SalesFieldReviews | undefined) => {
    const next = { ...reviews };
    delete next[field];
    return next;
  };
  return indices === undefined
    ? { ...form, fieldReviews: clear(form.fieldReviews) }
    : {
        ...form,
        items: form.items.map((item, index) =>
          indices.includes(index)
            ? { ...item, fieldReviews: clear(item.fieldReviews) }
            : item,
        ),
      };
}

export function withConnectedSalesGroups(
  form: SalesSlipForm,
  indices: number[],
  groups: SalesOrchidGroupOption[],
): SalesSlipForm {
  const first = groups[0];
  if (!first || !indices.length) return form;
  if (first.varietyId == null || first.varietyId <= 0)
    throw new Error("품종이 연결된 난 묶음을 선택해주세요.");
  if (groups.some((group) => group.varietyId !== first.varietyId))
    throw new Error("한 품목에는 같은 품종의 난 묶음을 선택해주세요.");

  return confirmSalesReview(
    confirmSalesReview(
      {
        ...form,
        items: distributeSalesItems(
          form.items.map((item, index) =>
            indices.includes(index)
              ? {
                  ...item,
                  varietyId: String(first.varietyId),
                  itemName: first.varietyName,
                  genus: first.genus,
                  allocationMode: "AUTO" as const,
                  allocations: groups.map((group) => ({
                    orchidGroupId: String(group.id),
                    varietyId: String(group.varietyId),
                    varietyName: group.varietyName,
                    genus: group.genus,
                    locationLabel: `${group.houseNumber}동 ${group.physicalBedNumber}배드 ${group.bedZoneName}`,
                    availableQuantity: group.availableQuantity,
                    quantity: "0",
                  })),
                }
              : item,
          ),
        ),
      },
      "ITEM_NAME",
      indices,
    ),
    "GENUS",
    indices,
  );
}
