import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  SalesOrchidGroupOption,
  SalesSlip,
  SalesSlipListItem,
  SalesSlipPage,
} from "@/entities/farm/types";
import { createUuid } from "@/shared/lib/id";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  changeSalesSlipStatus,
  createSalesSlip,
  getSalesSlip,
  updateSalesSlip as requestUpdateSalesSlip,
} from "../api/salesApi";
import {
  addSelectedGroupsToSalesItems,
  calculateSalesTotal,
  distributeSalesItems,
  salesAllocationIssue,
  selectedSalesGroups,
  createEmptySalesItem,
  createInitialSalesForm,
  resetSalesSlipFormAfterSave,
  toCreateSalesSlipPayload,
  toSalesSlipForm,
  withSalesRecordingMode,
} from "../lib/salesForm";
import { createPendingCreationRequestKey } from "@/shared/lib/pendingCreationRequestKey";
import type { SalesSlipsRouteState } from "../lib/salesRouteParams";
import {
  createInitialSalesFilters,
  SALES_FILTER_KEYS,
  writeSalesFilterParams,
} from "../lib/salesUrlFilters";
import {
  salesSlipDetailQueryOptions,
  salesSlipPageQueryOptions,
} from "./salesQueryOptions";
import { salesQueryKeys } from "./salesQueryKeys";
import type {
  SalesAllocationForm,
  SalesItemForm,
  SalesSlipForm,
  SalesFilterState,
} from "./types";

export function useSalesSlips({
  initialShowCreateSlip = false,
  routeState,
}: {
  initialShowCreateSlip?: boolean;
  routeState: SalesSlipsRouteState;
}) {
  const queryClient = useQueryClient();
  const writeUrlParams = useUrlSearchParamsWriter();
  const { businessDate } = useRuntimeContext();
  const salesSlipQuery = useQuery(salesSlipPageQueryOptions(routeState));
  const listState = useUrlPagedListState({
    emptyFilters: createInitialSalesFilters,
    filterKeys: SALES_FILTER_KEYS,
    resetParamKeys: ["slipId", "paymentPage"],
    routeFilters: routeState.filters,
    writeFilterParams: writeSalesFilterParams,
  });
  const salesSlipPageData =
    salesSlipQuery.data ??
    createEmptyPage<SalesSlipListItem>(routeState.size, routeState.page);
  const [salesForm, setSalesForm] = useState<SalesSlipForm>(() =>
    createInitialSalesForm(businessDate),
  );
  const [showCreateSlip, setShowCreateSlip] = useState(initialShowCreateSlip);
  const [editingSlipId, setEditingSlipId] = useState<number | null>(null);
  const [savingSlip, setSavingSlip] = useState(false);
  const [updatingSlipStatus, setUpdatingSlipStatus] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [creationRequestKey] = useState(() =>
    createPendingCreationRequestKey(
      "greenhouse:sales-create-request:v1",
      createUuid,
      () => window.sessionStorage,
    ),
  );

  const totalAmount = useMemo(
    () => calculateSalesTotal(salesForm.items),
    [salesForm.items],
  );
  const salesSlipTotalPages = Math.max(1, salesSlipPageData.totalPages);
  const visibleSalesSlipPage = Math.min(
    routeState.page,
    salesSlipTotalPages - 1,
  );
  const visibleSelectedSlipId =
    routeState.selectedSlipId != null
      ? routeState.selectedSlipId
      : (salesSlipPageData.content[0]?.id ?? null);
  const salesSlipDetailQuery = useQuery({
    ...salesSlipDetailQueryOptions(visibleSelectedSlipId ?? 0),
    enabled: visibleSelectedSlipId != null,
  });
  const visibleSelectedSalesSlip = salesSlipDetailQuery.data ?? null;
  const writeSelectedSlipId = useCallback(
    (salesSlipId: number, historyMode: "replace" | "push") => {
      writeUrlParams((params) => {
        params.set("slipId", String(salesSlipId));
        params.delete("paymentPage");
      }, historyMode);
    },
    [writeUrlParams],
  );

  useEffect(() => {
    if (
      routeState.selectedSlipId == null &&
      salesSlipPageData.content[0]?.id != null
    ) {
      writeSelectedSlipId(salesSlipPageData.content[0].id, "replace");
    }
  }, [
    routeState.selectedSlipId,
    salesSlipPageData.content,
    writeSelectedSlipId,
  ]);

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
    if (salesForm.salesType === salesType) return;
    setSalesForm((current) => ({
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
        salesType === "AUCTION"
          ? (current.items.length > 0
              ? current.items
              : [createEmptySalesItem()]
            ).map((item) => ({ ...item, unitPrice: "0" }))
          : current.items.length > 0
            ? current.items
            : [createEmptySalesItem()],
    }));
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

  async function handleCreateSalesSlip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingSlip(true);
    setErrorMessage(null);

    try {
      const allocationError = salesAllocationIssue(
        salesForm.items,
        salesForm.recordOnly,
      );
      if (allocationError) throw new Error(allocationError);
      const payload = toCreateSalesSlipPayload(salesForm);
      let salesSlip: SalesSlip;
      if (editingSlipId == null) {
        const key = creationRequestKey.get();
        salesSlip = await createSalesSlip(payload, key);
        creationRequestKey.complete(key);
      } else {
        salesSlip = await requestUpdateSalesSlip(editingSlipId, payload);
      }
      updateSalesSlip(salesSlip);
      writeUrlParams((params) => {
        params.set("slipId", String(salesSlip.id));
        params.delete("paymentPage");
        params.set("page", "0");
      });
      setShowCreateSlip(false);
      setSalesForm((current) => resetSalesSlipFormAfterSave(current));
      setEditingSlipId(null);
      await Promise.all([
        invalidateSalesSlips(),
        ...(salesSlip.salesType === "AUCTION"
          ? [
              queryClient.invalidateQueries({
                queryKey: salesQueryKeys.auction.all,
              }),
            ]
          : []),
        queryClient.invalidateQueries({
          queryKey: salesQueryKeys.partners.all,
        }),
        queryClient.invalidateQueries({
          queryKey: salesQueryKeys.payments.balance(salesSlip.partner.id),
        }),
        queryClient.invalidateQueries({
          queryKey: ["sales", "allocationWorkspace", salesSlip.partner.id],
        }),
        queryClient.invalidateQueries({
          queryKey: salesQueryKeys.payments
            .historyPage("SALES_SLIP", salesSlip.id, 0, 10)
            .slice(0, 4),
        }),
      ]);
      return true;
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
      return false;
    } finally {
      setSavingSlip(false);
    }
  }

  function startCreateSalesSlip() {
    setEditingSlipId(null);
    setErrorMessage(null);
    setSalesForm(createInitialSalesForm(businessDate));
    setShowCreateSlip(true);
  }

  async function startEditSalesSlip(salesSlipId: number) {
    setErrorMessage(null);
    try {
      const salesSlip =
        visibleSelectedSalesSlip?.id === salesSlipId
          ? visibleSelectedSalesSlip
          : await getSalesSlip(salesSlipId);
      setEditingSlipId(salesSlipId);
      writeSelectedSlipId(salesSlipId, "replace");
      queryClient.setQueryData(
        salesQueryKeys.slips.detail(salesSlip.id),
        salesSlip,
      );
      setSalesForm(toSalesSlipForm(salesSlip));
      setShowCreateSlip(true);
    } catch (error) {
      setErrorMessage(toMessage(error));
    }
  }

  function cancelSalesSlipEditing() {
    setEditingSlipId(null);
    setShowCreateSlip(false);
    setSalesForm(createInitialSalesForm(businessDate));
    setErrorMessage(null);
  }

  async function handleCompleteSalesSlip(salesSlipId: number) {
    setUpdatingSlipStatus(true);
    setErrorMessage(null);
    try {
      const current =
        visibleSelectedSalesSlip?.id === salesSlipId
          ? visibleSelectedSalesSlip
          : salesSlipPageData.content.find((item) => item.id === salesSlipId);
      const nextStatus =
        current?.salesType === "AUCTION" ? "출하 완료" : "출고 완료";
      const updated = await changeSalesSlipStatus(salesSlipId, {
        salesStatus: nextStatus,
        memo: null,
      });
      updateSalesSlip(updated);
      await invalidateSalesSlips();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "상태를 변경하지 못했습니다.",
      );
    } finally {
      setUpdatingSlipStatus(false);
    }
  }

  async function handleCancelSalesSlip(salesSlipId: number) {
    setUpdatingSlipStatus(true);
    setErrorMessage(null);
    try {
      const updated = await changeSalesSlipStatus(salesSlipId, {
        salesStatus: "취소",
        memo: null,
      });
      updateSalesSlip(updated);
      await invalidateSalesSlips();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "전표를 취소하지 못했습니다.",
      );
    } finally {
      setUpdatingSlipStatus(false);
    }
  }

  function updateSalesSlip(salesSlip: SalesSlip) {
    queryClient.setQueryData(
      salesQueryKeys.slips.detail(salesSlip.id),
      salesSlip,
    );
    queryClient.setQueriesData<SalesSlipPage>(
      { queryKey: salesQueryKeys.slips.pages },
      (current) =>
        current == null
          ? current
          : {
              ...current,
              content: current.content.map((item) =>
                item.id === salesSlip.id ? salesSlip : item,
              ),
            },
    );
  }

  function selectSalesSlip(salesSlipId: number) {
    writeSelectedSlipId(salesSlipId, "push");
  }

  return {
    salesSlips: salesSlipPageData.content,
    salesSlipCurrentPage: visibleSalesSlipPage,
    salesSlipPageSize: routeState.size,
    salesSlipTotalPages,
    salesSlipTotalElements: salesSlipPageData.totalElements,
    selectedSalesSlipId: visibleSelectedSlipId,
    selectedSalesSlip: visibleSelectedSalesSlip,
    loadingSalesSlipDetail: salesSlipDetailQuery.isFetching,
    loadingSalesSlipPage: salesSlipQuery.isFetching,
    filters: listState.filters,
    setSalesTypeFilter: (salesType: SalesFilterState["salesType"]) =>
      listState.replaceFilters({
        ...listState.filters,
        salesType,
        partnerId: "",
      }),
    salesForm,
    showCreateSlip,
    editingSlipId,
    savingSlip,
    updatingSlipStatus,
    errorMessage:
      errorMessage ??
      (salesSlipQuery.error == null
        ? salesSlipDetailQuery.error == null
          ? null
          : toMessage(salesSlipDetailQuery.error)
        : toMessage(salesSlipQuery.error)),
    totalAmount,
    selectSalesGroups,
    removeSalesGroup,
    useAutomaticAllocation,
    selectedGroups: selectedSalesGroups(salesForm.items),
    allocationIssue: salesAllocationIssue(
      salesForm.items,
      salesForm.recordOnly,
    ),
    setRecordingMode: (recordOnly: boolean) =>
      setSalesForm((current) => withSalesRecordingMode(current, recordOnly)),
    addSalesItem,
    removeSalesItem,
    selectSalesSlip,
    selectSalesType,
    setShowCreateSlip,
    startCreateSalesSlip,
    startEditSalesSlip,
    cancelSalesSlipEditing,
    resetFilters: listState.reset,
    removeFilters: listState.removeFilters,
    resetPopupFilters: () =>
      listState.removeFilters(
        SALES_FILTER_KEYS.filter((key) => key !== "keyword"),
      ),
    hasEmptySalesSlipResult:
      salesSlipQuery.isSuccess &&
      !salesSlipQuery.isFetching &&
      salesSlipPageData.totalElements === 0,
    setSalesSlipPage: listState.changePage,
    setSalesSlipPageSize: listState.changePageSize,
    searchSalesSlips: listState.search,
    updateAllocation,
    updateFilters: listState.updateFilter,
    updateSalesForm,
    updateItem,
    updateSalesSlip,
    handleCompleteSalesSlip,
    handleCancelSalesSlip,
    handleCreateSalesSlip,
  };

  async function invalidateSalesSlips() {
    await queryClient.invalidateQueries({
      queryKey: salesQueryKeys.slips.all,
    });
  }
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}

function isSameSalesItemVariety(
  item: Pick<SalesItemForm, "genus" | "itemName">,
  target: Pick<SalesAllocationForm, "genus" | "varietyName">,
) {
  const itemName = item.itemName.trim();
  const genus = item.genus.trim();
  return (
    (!itemName || itemName === target.varietyName) &&
    (!genus || genus === target.genus)
  );
}
