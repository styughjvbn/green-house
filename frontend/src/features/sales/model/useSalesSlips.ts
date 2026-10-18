import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  SalesSlip,
  SalesSlipListItem,
  SalesSlipPage,
} from "@/entities/farm/types";
import { createSalesSlipDraftActions } from "./salesSlipDraftActions";
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
  calculateSalesTotal,
  salesAllocationIssue,
  selectedSalesGroups,
  createInitialSalesForm,
  resetSalesSlipFormAfterSave,
  toCreateSalesSlipPayload,
  toSalesSlipForm,
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
  SalesSlipForm,
  SalesFilterState,
  SalesItemForm,
  SalesAllocationForm,
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

  const {
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
    setRecordingMode,
  } = createSalesSlipDraftActions(setSalesForm, editingSlipId);

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
    resetSalesAllocation,
    selectedGroups: selectedSalesGroups(salesForm.items),
    allocationIssue: salesAllocationIssue(
      salesForm.items,
      salesForm.recordOnly,
    ),
    setRecordingMode,
    onPhotoSlipSaved: async (slip: SalesSlip) => {
      updateSalesSlip(slip);
      await Promise.all([
        invalidateSalesSlips(),
        queryClient.invalidateQueries({ queryKey: salesQueryKeys.auction.all }),
        queryClient.invalidateQueries({
          queryKey: salesQueryKeys.partners.all,
        }),
        queryClient.invalidateQueries({ queryKey: ["farm-structure"] }),
        queryClient.invalidateQueries({
          queryKey: ["sales", "orchid-group-options"],
        }),
      ]);
    },
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
