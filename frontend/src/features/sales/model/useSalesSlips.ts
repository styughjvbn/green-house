import { useMemo, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  SalesOrchidGroupOption,
  SalesSlip,
  SalesSlipListItem,
  SalesSlipPage,
} from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
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
import type { SalesRouteState } from "../lib/salesRouteParams";
import {
  createInitialSalesFilters,
  SALES_FILTER_KEYS,
  writeSalesFilterParams,
} from "../lib/salesUrlFilters";
import {
  businessPartnerLookupQueryOptions,
  salesSlipPageQueryOptions,
} from "./salesQueryOptions";
import { salesQueryKeys } from "./salesQueryKeys";
import type {
  SalesAllocationForm,
  SalesFilterState,
  SalesItemForm,
  SalesSlipForm,
} from "./types";

export function useSalesSlips({
  initialShowCreateSlip = false,
  routeState,
}: {
  initialShowCreateSlip?: boolean;
  routeState: SalesRouteState<SalesFilterState>;
}) {
  const queryClient = useQueryClient();
  const salesSlipQuery = useQuery(salesSlipPageQueryOptions(routeState));
  const partnersQuery = useQuery(businessPartnerLookupQueryOptions());
  const partners = partnersQuery.data ?? [];
  const listState = useUrlPagedListState({
    emptyFilters: createInitialSalesFilters,
    filterKeys: SALES_FILTER_KEYS,
    routeFilters: routeState.filters,
    writeFilterParams: writeSalesFilterParams,
  });
  const salesSlipPageData =
    salesSlipQuery.data ??
    createEmptyPage<SalesSlipListItem>(routeState.size, routeState.page);
  const [selectedSalesSlip, setSelectedSalesSlip] = useState<SalesSlip | null>(
    null,
  );
  const [salesForm, setSalesForm] = useState<SalesSlipForm>(() =>
    createInitialSalesForm(partners),
  );
  const [showCreateSlip, setShowCreateSlip] = useState(initialShowCreateSlip);
  const [editingSlipId, setEditingSlipId] = useState<number | null>(null);
  const [selectedSlipId, setSelectedSlipId] = useState<number | null>(null);
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
    selectedSlipId != null &&
    salesSlipPageData.content.some((item) => item.id === selectedSlipId)
      ? selectedSlipId
      : (salesSlipPageData.content[0]?.id ?? null);
  const salesSlipDetailQuery = useQuery({
    queryKey: salesQueryKeys.slips.detail(visibleSelectedSlipId ?? 0),
    queryFn: () => getSalesSlip(visibleSelectedSlipId as number),
    enabled: visibleSelectedSlipId != null,
  });
  const visibleSelectedSalesSlip =
    selectedSalesSlip?.id === visibleSelectedSlipId
      ? selectedSalesSlip
      : (salesSlipDetailQuery.data ?? null);

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
      setSelectedSlipId(salesSlip.id);
      setSelectedSalesSlip(salesSlip);
      listState.changePage(0);
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
    setSalesForm(createInitialSalesForm(partners));
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
      setSelectedSlipId(salesSlipId);
      setSelectedSalesSlip(salesSlip);
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
    setSalesForm(createInitialSalesForm(partners));
    setErrorMessage(null);
  }

  async function handleCompleteSalesSlip(salesSlipId: number) {
    setUpdatingSlipStatus(true);
    setErrorMessage(null);
    try {
      const current =
        selectedSalesSlip?.id === salesSlipId
          ? selectedSalesSlip
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
    setSelectedSalesSlip(salesSlip);
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
    setSelectedSlipId(salesSlipId);
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
