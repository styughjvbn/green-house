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
  createEmptySalesItem,
  createInitialSalesForm,
  resetSalesSlipFormAfterSave,
  toCreateSalesSlipPayload,
  toSalesSlipForm,
} from "../lib/salesForm";
import type { SalesSlipsRouteState } from "../lib/salesRouteParams";
import {
  createInitialSalesFilters,
  SALES_FILTER_KEYS,
  writeSalesFilterParams,
} from "../lib/salesUrlFilters";
import {
  businessPartnerLookupQueryOptions,
  salesSlipDetailQueryOptions,
  salesSlipPageQueryOptions,
} from "./salesQueryOptions";
import { salesQueryKeys } from "./salesQueryKeys";
import type {
  SalesAllocationForm,
  SalesItemForm,
  SalesSlipForm,
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
  const partnersQuery = useQuery(businessPartnerLookupQueryOptions());
  const partners = partnersQuery.data ?? [];
  const listState = useUrlPagedListState({
    emptyFilters: createInitialSalesFilters,
    filterKeys: SALES_FILTER_KEYS,
    resetParamKeys: ["slipId"],
    routeFilters: routeState.filters,
    writeFilterParams: writeSalesFilterParams,
  });
  const salesSlipPageData =
    salesSlipQuery.data ??
    createEmptyPage<SalesSlipListItem>(routeState.size, routeState.page);
  const [salesForm, setSalesForm] = useState<SalesSlipForm>(() =>
    createInitialSalesForm(partners, businessDate),
  );
  const [showCreateSlip, setShowCreateSlip] = useState(initialShowCreateSlip);
  const [editingSlipId, setEditingSlipId] = useState<number | null>(null);
  const [savingSlip, setSavingSlip] = useState(false);
  const [updatingSlipStatus, setUpdatingSlipStatus] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

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
      writeUrlParams(
        (params) => params.set("slipId", String(salesSlipId)),
        historyMode,
      );
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
    setSalesForm((current) => ({ ...current, [field]: value }));
  }

  function selectSalesType(salesType: SalesSlipForm["salesType"]) {
    const auctionPartner = partners.find(
      (partner) => partner.partnerType === "AUCTION_HOUSE",
    );
    const directPartner = partners.find(
      (partner) => partner.partnerType !== "AUCTION_HOUSE",
    );
    setSalesForm((current) => ({
      ...current,
      salesType,
      partnerId:
        salesType === "AUCTION"
          ? auctionPartner
            ? String(auctionPartner.id)
            : ""
          : directPartner
            ? String(directPartner.id)
            : current.partnerId,
      paymentStatus: salesType === "AUCTION" ? "정산 대기" : "미입금",
      salesStatus: "작성중",
      paymentMethod: salesType === "AUCTION" ? "경매 정산" : "",
      items:
        current.items.length > 0 ? current.items : [createEmptySalesItem()],
    }));
  }

  function updateItem(
    index: number,
    field: keyof SalesItemForm,
    value: string,
  ) {
    setSalesForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) => {
        if (itemIndex !== index) return item;

        const nextItem = { ...item, [field]: value };

        return {
          ...nextItem,
          allocations:
            field === "itemName" || field === "genus"
              ? nextItem.allocations.filter((allocation) =>
                  isSameSalesItemVariety(nextItem, allocation),
                )
              : nextItem.allocations,
        };
      }),
    }));
  }

  function addSalesItem() {
    setSalesForm((current) => ({
      ...current,
      items: [...current.items, createEmptySalesItem()],
    }));
  }

  function removeSalesItem(index: number) {
    setSalesForm((current) => ({
      ...current,
      items: current.items.filter((_, itemIndex) => itemIndex !== index),
    }));
  }

  function addAllocation(index: number, orchidGroup: SalesOrchidGroupOption) {
    setSalesForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) => {
        if (itemIndex !== index) return item;
        if (
          !isSameSalesItemVariety(item, {
            genus: orchidGroup.genus,
            varietyName: orchidGroup.varietyName,
          })
        ) {
          return item;
        }

        const existingIndex = item.allocations.findIndex(
          (allocation) => Number(allocation.orchidGroupId) === orchidGroup.id,
        );
        const nextAllocation = {
          orchidGroupId: String(orchidGroup.id),
          varietyName: orchidGroup.varietyName,
          genus: orchidGroup.genus,
          locationLabel: `${orchidGroup.houseNumber}동 ${orchidGroup.physicalBedNumber}배드 ${orchidGroup.bedZoneName}`,
          availableQuantity: orchidGroup.availableQuantity,
          quantity: "1",
        };

        return {
          ...item,
          itemName: item.itemName || orchidGroup.varietyName,
          genus: item.genus || orchidGroup.genus,
          allocations:
            existingIndex >= 0
              ? item.allocations.map((allocation, allocationIndex) =>
                  allocationIndex === existingIndex
                    ? {
                        ...allocation,
                        availableQuantity: orchidGroup.availableQuantity,
                      }
                    : allocation,
                )
              : [...item.allocations, nextAllocation],
        };
      }),
    }));
  }

  function updateAllocation(
    index: number,
    allocationIndex: number,
    field: keyof SalesAllocationForm,
    value: string,
  ) {
    setSalesForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) =>
        itemIndex === index
          ? {
              ...item,
              allocations: item.allocations.map((allocation, currentIndex) =>
                currentIndex === allocationIndex
                  ? { ...allocation, [field]: value }
                  : allocation,
              ),
            }
          : item,
      ),
    }));
  }

  function removeAllocation(index: number, allocationIndex: number) {
    setSalesForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) => {
        if (itemIndex !== index) return item;

        const allocations = item.allocations.filter(
          (_, currentIndex) => currentIndex !== allocationIndex,
        );

        return {
          ...item,
          itemName: allocations.length === 0 ? "" : item.itemName,
          genus: allocations.length === 0 ? "" : item.genus,
          allocations,
        };
      }),
    }));
  }

  async function handleCreateSalesSlip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingSlip(true);
    setErrorMessage(null);

    try {
      const payload = toCreateSalesSlipPayload(salesForm);
      const salesSlip =
        editingSlipId == null
          ? await createSalesSlip(payload)
          : await requestUpdateSalesSlip(editingSlipId, payload);
      updateSalesSlip(salesSlip);
      writeUrlParams((params) => {
        params.set("slipId", String(salesSlip.id));
        params.set("page", "0");
      });
      setShowCreateSlip(false);
      setSalesForm((current) => resetSalesSlipFormAfterSave(current));
      setEditingSlipId(null);
      await invalidateSalesSlips();
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
    setSalesForm(createInitialSalesForm(partners, businessDate));
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
    setSalesForm(createInitialSalesForm(partners, businessDate));
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
    partners,
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
    salesForm,
    showCreateSlip,
    editingSlipId,
    savingSlip,
    updatingSlipStatus,
    errorMessage:
      errorMessage ??
      (salesSlipQuery.error == null
        ? partnersQuery.error == null
          ? salesSlipDetailQuery.error == null
            ? null
            : toMessage(salesSlipDetailQuery.error)
          : toMessage(partnersQuery.error)
        : toMessage(salesSlipQuery.error)),
    totalAmount,
    addAllocation,
    addSalesItem,
    removeAllocation,
    removeSalesItem,
    selectSalesSlip,
    selectSalesType,
    setShowCreateSlip,
    startCreateSalesSlip,
    startEditSalesSlip,
    cancelSalesSlipEditing,
    resetFilters: listState.reset,
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
