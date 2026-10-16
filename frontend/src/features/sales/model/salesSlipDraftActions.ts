import type { Dispatch, SetStateAction } from "react";
import type { SalesOrchidGroupOption } from "@/entities/farm/types";
import type {
  SalesAllocationForm,
  SalesItemForm,
  SalesSlipForm,
} from "./types";
import {
  addSelectedGroupsToSalesItems,
  calculateSalesTotal,
  createEmptySalesItem,
  distributeSalesItems,
  withSalesRecordingMode,
} from "../lib/salesForm";

export function createSalesSlipDraftActions(
  setSalesForm: Dispatch<SetStateAction<SalesSlipForm>>,
  editingSlipId: number | null = null,
) {
  function updateSalesForm<K extends keyof SalesSlipForm>(
    field: K,
    value: SalesSlipForm[K],
  ) {
    setSalesForm((current) => ({
      ...current,
      [field]: value,
      ...(field === "receiptEnabled" && value === true && !current.receiptAmount
        ? { receiptAmount: String(calculateSalesTotal(current.items)) }
        : {}),
    }));
  }

  function selectSalesType(salesType: SalesSlipForm["salesType"]) {
    setSalesForm((current) =>
      current.salesType === salesType
        ? current
        : {
            ...current,
            salesType,
            partnerId: "",
            paymentStatus: salesType === "AUCTION" ? "정산 대기" : "미입금",
            salesStatus: current.recordOnly
              ? salesType === "AUCTION"
                ? "출하 완료"
                : "출고 완료"
              : "작성중",
            paymentMethod: salesType === "AUCTION" ? "경매 정산" : "",
            receiptEnabled: false,
            receiptAmount: "",
            items:
              current.items.length > 0
                ? current.items
                : [createEmptySalesItem()],
          },
    );
  }

  function updateItem(
    index: number,
    field: keyof SalesItemForm,
    value: string,
  ) {
    setSalesForm((current) => {
      const items = current.items.map((item, itemIndex) =>
        itemIndex === index
          ? {
              ...item,
              [field]: value,
              allocationMode:
                field === "quantity" ? ("AUTO" as const) : item.allocationMode,
            }
          : item,
      );
      return {
        ...current,
        items:
          field === "quantity" && !current.recordOnly
            ? distributeSalesItems(items)
            : items,
      };
    });
  }

  function addSalesItem(index: number) {
    setSalesForm((current) => {
      if (editingSlipId != null) return current;
      const source = current.items[index];
      if (current.recordOnly && index === -1)
        return {
          ...current,
          items: [...current.items, createEmptySalesItem()],
        };
      if (!source?.itemName) return current;
      const item = {
        ...createEmptySalesItem(),
        itemName: source.itemName,
        genus: source.genus,
        ...(source.fieldReviews
          ? {
              fieldReviews: {
                ...(source.fieldReviews.ITEM_NAME
                  ? { ITEM_NAME: source.fieldReviews.ITEM_NAME }
                  : {}),
                ...(source.fieldReviews.GENUS
                  ? { GENUS: source.fieldReviews.GENUS }
                  : {}),
              },
            }
          : {}),
      };
      const items = [...current.items];
      items.splice(index + 1, 0, item);
      return {
        ...current,
        items: current.recordOnly ? items : distributeSalesItems(items),
      };
    });
  }

  function removeSalesItem(index: number) {
    setSalesForm((current) => ({
      ...current,
      items: distributeSalesItems(
        current.items.filter((_, itemIndex) => itemIndex !== index),
      ),
    }));
  }

  function selectSalesGroups(groups: SalesOrchidGroupOption[]) {
    setSalesForm((current) => ({
      ...current,
      items: addSelectedGroupsToSalesItems(current.items, groups),
    }));
  }

  function removeSalesGroup(id: string) {
    setSalesForm((current) => {
      const stripped = current.items.map((item) => ({
        ...item,
        allocations: item.allocations.filter(
          (allocation) => allocation.orchidGroupId !== id,
        ),
      }));
      const kept =
        editingSlipId == null
          ? stripped.filter(
              (item) =>
                item.allocations.length > 0 || (!item.itemName && !item.genus),
            )
          : stripped;
      return {
        ...current,
        items: distributeSalesItems(
          kept.length ? kept : [createEmptySalesItem()],
        ),
      };
    });
  }

  function useAutomaticAllocation(index: number) {
    setSalesForm((current) => ({
      ...current,
      items: distributeSalesItems(
        current.items.map((item, itemIndex) =>
          itemIndex === index
            ? { ...item, allocationMode: "AUTO" as const }
            : item,
        ),
      ),
    }));
  }

  function resetSalesAllocation(index: number) {
    setSalesForm((current) => ({
      ...current,
      items: distributeSalesItems(
        current.items.map((item, itemIndex) =>
          itemIndex === index
            ? {
                ...item,
                allocationMode: "MANUAL" as const,
                allocations: item.allocations.map((allocation) => ({
                  ...allocation,
                  quantity: "0",
                })),
              }
            : item,
        ),
      ),
    }));
  }

  function updateAllocation(
    index: number,
    allocationIndex: number,
    field: keyof SalesAllocationForm,
    value: string,
  ) {
    if (field !== "quantity") return;
    setSalesForm((current) => ({
      ...current,
      items: distributeSalesItems(
        current.items.map((item, itemIndex) =>
          itemIndex === index
            ? {
                ...item,
                allocationMode: "MANUAL" as const,
                allocations: item.allocations.map((allocation, currentIndex) =>
                  currentIndex === allocationIndex
                    ? { ...allocation, quantity: value }
                    : allocation,
                ),
              }
            : item,
        ),
      ),
    }));
  }

  return {
    updateSalesForm,
    selectSalesType,
    updateItem,
    addSalesItem,
    removeSalesItem,
    selectSalesGroups,
    removeSalesGroup,
    useAutomaticAllocation,
    resetSalesAllocation,
    updateAllocation,
    setRecordingMode: (recordOnly: boolean) =>
      setSalesForm((current) => withSalesRecordingMode(current, recordOnly)),
  };
}
