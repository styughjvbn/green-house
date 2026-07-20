import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import type {
  BusinessPartner,
  SalesOrchidGroupOption,
  SalesSlip,
} from "@/entities/farm/types";
import {
  changeSalesSlipStatus,
  createBusinessPartner,
  createSalesSlip,
  getSalesSlip,
  getSalesSlipPage,
  updateBusinessPartner,
  updateSalesSlip as requestUpdateSalesSlip,
} from "../api/salesApi";
import {
  calculateSalesTotal,
  createEmptyBusinessPartnerForm,
  createEmptySalesItem,
  createInitialBusinessPartnerFilters,
  createInitialSalesFilters,
  createInitialSalesForm,
  filterBusinessPartners,
  resetSalesSlipFormAfterSave,
  toBusinessPartnerForm,
  toCreateBusinessPartnerPayload,
  toCreateSalesSlipPayload,
  toSalesSlipForm,
} from "../lib/salesForm";
import type {
  BusinessPartnerFilterState,
  BusinessPartnerForm,
  SalesAllocationForm,
  SalesFilterState,
  SalesItemForm,
  SalesSlipForm,
  SalesSlipPage,
} from "./types";

export function useSalesManager(
  initialBusinessPartners: BusinessPartner[],
  initialSalesSlipPage: SalesSlipPage | undefined,
  initialShowCreateSlip = false,
) {
  const [partners, setBusinessPartners] = useState<BusinessPartner[]>(
    initialBusinessPartners,
  );
  const [salesSlipPageData, setSalesSlipPageData] = useState<SalesSlipPage>(
    initialSalesSlipPage ?? createEmptySalesSlipPage(),
  );
  const [selectedSalesSlip, setSelectedSalesSlip] = useState<SalesSlip | null>(
    null,
  );
  const [partnerForm, setBusinessPartnerForm] = useState<BusinessPartnerForm>(
    createEmptyBusinessPartnerForm(),
  );
  const [partnerEditDraft, setPartnerEditDraft] = useState<{
    partnerId: number | null;
    form: BusinessPartnerForm;
  }>(() => ({
    partnerId: initialBusinessPartners[0]?.id ?? null,
    form: initialBusinessPartners[0]
      ? toBusinessPartnerForm(initialBusinessPartners[0])
      : createEmptyBusinessPartnerForm(),
  }));
  const [salesForm, setSalesForm] = useState<SalesSlipForm>(() =>
    createInitialSalesForm(initialBusinessPartners),
  );
  const [filters, setFilters] = useState<SalesFilterState>(() =>
    createInitialSalesFilters(),
  );
  const [partnerFilters, setPartnerFilters] =
    useState<BusinessPartnerFilterState>(() =>
      createInitialBusinessPartnerFilters(),
    );
  const [showCreateSlip, setShowCreateSlip] = useState(initialShowCreateSlip);
  const [editingSlipId, setEditingSlipId] = useState<number | null>(null);
  const [selectedSlipId, setSelectedSlipId] = useState<number | null>(
    initialSalesSlipPage?.content[0]?.id ?? null,
  );
  const [selectedPartnerId, setSelectedPartnerId] = useState<number | null>(
    initialBusinessPartners[0]?.id ?? null,
  );
  const [salesSlipPage, setSalesSlipPage] = useState(
    initialSalesSlipPage?.page ?? 0,
  );
  const [salesSlipPageSize, setSalesSlipPageSize] = useState(
    initialSalesSlipPage?.size ?? 10,
  );
  const [partnerPage, setPartnerPage] = useState(0);
  const [partnerPageSize, setPartnerPageSize] = useState(10);
  const [savingBusinessPartner, setSavingBusinessPartner] = useState(false);
  const [savingBusinessPartnerEdit, setSavingBusinessPartnerEdit] =
    useState(false);
  const [savingSlip, setSavingSlip] = useState(false);
  const [updatingSlipStatus, setUpdatingSlipStatus] = useState(false);
  const [loadingSalesSlipPage, setLoadingSalesSlipPage] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const totalAmount = useMemo(
    () => calculateSalesTotal(salesForm.items),
    [salesForm.items],
  );
  const filteredBusinessPartners = useMemo(
    () => filterBusinessPartners(partners, partnerFilters),
    [partners, partnerFilters],
  );
  const salesSlipTotalPages = Math.max(1, salesSlipPageData.totalPages);
  const visibleSalesSlipPage = Math.min(salesSlipPage, salesSlipTotalPages - 1);
  const partnerTotalPages = Math.max(
    1,
    Math.ceil(filteredBusinessPartners.length / partnerPageSize),
  );
  const visiblePartnerPage = Math.min(partnerPage, partnerTotalPages - 1);
  const paginatedBusinessPartners = useMemo(() => {
    const start = visiblePartnerPage * partnerPageSize;
    return filteredBusinessPartners.slice(start, start + partnerPageSize);
  }, [filteredBusinessPartners, partnerPageSize, visiblePartnerPage]);
  const selectedBusinessPartner =
    partners.find((partner) => partner.id === selectedPartnerId) ?? null;
  const partnerEditForm =
    selectedBusinessPartner == null
      ? createEmptyBusinessPartnerForm()
      : partnerEditDraft.partnerId === selectedBusinessPartner.id
        ? partnerEditDraft.form
        : toBusinessPartnerForm(selectedBusinessPartner);

  const loadSalesSlipPage = useCallback(
    async (pageOverride = salesSlipPage) => {
      setLoadingSalesSlipPage(true);
      try {
        const nextPage = await getSalesSlipPage(
          filters,
          pageOverride,
          salesSlipPageSize,
        );
        setSalesSlipPageData(nextPage);
        setSelectedSlipId((current) => {
          if (
            current != null &&
            nextPage.content.some((item) => item.id === current)
          ) {
            return current;
          }
          if (nextPage.content.length === 0) {
            setSelectedSalesSlip(null);
          }
          return nextPage.content[0]?.id ?? null;
        });
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "판매 전표 목록을 불러오지 못했습니다.",
        );
      } finally {
        setLoadingSalesSlipPage(false);
      }
    },
    [filters, salesSlipPage, salesSlipPageSize],
  );

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadSalesSlipPage();
    }, 250);
    return () => window.clearTimeout(timeoutId);
  }, [loadSalesSlipPage]);

  useEffect(() => {
    if (selectedSlipId == null) return;

    let canceled = false;
    getSalesSlip(selectedSlipId)
      .then((salesSlip) => {
        if (!canceled) setSelectedSalesSlip(salesSlip);
      })
      .catch((error) => {
        if (!canceled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "전표 상세를 불러오지 못했습니다.",
          );
          setSelectedSalesSlip(null);
        }
      });

    return () => {
      canceled = true;
    };
  }, [selectedSlipId]);

  function updateBusinessPartnerForm<K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) {
    setBusinessPartnerForm((current) => ({ ...current, [field]: value }));
  }

  function updateBusinessPartnerEditForm<K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) {
    if (!selectedBusinessPartner) return;
    setPartnerEditDraft((current) => ({
      partnerId: selectedBusinessPartner.id,
      form: {
        ...(current.partnerId === selectedBusinessPartner.id
          ? current.form
          : toBusinessPartnerForm(selectedBusinessPartner)),
        [field]: value,
      },
    }));
  }

  function updateSalesForm<K extends keyof SalesSlipForm>(
    field: K,
    value: SalesSlipForm[K],
  ) {
    setSalesForm((current) => ({ ...current, [field]: value }));
  }

  function updateFilters<K extends keyof SalesFilterState>(
    field: K,
    value: SalesFilterState[K],
  ) {
    setSalesSlipPage(0);
    setFilters((current) => ({ ...current, [field]: value }));
  }

  function updatePartnerFilters<K extends keyof BusinessPartnerFilterState>(
    field: K,
    value: BusinessPartnerFilterState[K],
  ) {
    setPartnerPage(0);
    setPartnerFilters((current) => ({ ...current, [field]: value }));
  }

  function resetFilters() {
    setSalesSlipPage(0);
    setFilters(createInitialSalesFilters());
  }

  function resetPartnerFilters() {
    setPartnerPage(0);
    setPartnerFilters(createInitialBusinessPartnerFilters());
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

  function selectBusinessPartner(partnerId: number) {
    setSelectedPartnerId(partnerId);
    updateSalesForm("partnerId", String(partnerId));
  }

  async function handleCreateBusinessPartner(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();
    setSavingBusinessPartner(true);
    setErrorMessage(null);

    try {
      const partner = await createBusinessPartner(
        toCreateBusinessPartnerPayload(partnerForm),
      );
      setBusinessPartners((current) => [...current, partner]);
      setSelectedPartnerId(partner.id);
      setPartnerPage(0);
      if (partner.partnerType !== "AUCTION_HOUSE") {
        setSalesForm((current) => ({
          ...current,
          partnerId: String(partner.id),
        }));
      }
      setBusinessPartnerForm(createEmptyBusinessPartnerForm());
      return true;
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
      return false;
    } finally {
      setSavingBusinessPartner(false);
    }
  }

  async function handleUpdateBusinessPartner(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();
    if (!selectedBusinessPartner) return false;

    setSavingBusinessPartnerEdit(true);
    setErrorMessage(null);
    try {
      const updated = await updateBusinessPartner(
        selectedBusinessPartner.id,
        toCreateBusinessPartnerPayload(partnerEditForm),
      );
      setBusinessPartners((current) =>
        current.map((partner) =>
          partner.id === updated.id ? updated : partner,
        ),
      );
      setPartnerEditDraft({
        partnerId: updated.id,
        form: toBusinessPartnerForm(updated),
      });
      return true;
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "거래처를 수정하지 못했습니다.",
      );
      return false;
    } finally {
      setSavingBusinessPartnerEdit(false);
    }
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
      setSalesSlipPage(0);
      setShowCreateSlip(false);
      setSalesForm((current) => resetSalesSlipFormAfterSave(current));
      setEditingSlipId(null);
      await loadSalesSlipPage(0);
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
    const salesSlip =
      selectedSalesSlip?.id === salesSlipId
        ? selectedSalesSlip
        : await getSalesSlip(salesSlipId);
    setEditingSlipId(salesSlipId);
    setSelectedSlipId(salesSlipId);
    setSelectedSalesSlip(salesSlip);
    setSalesForm(toSalesSlipForm(salesSlip));
    setShowCreateSlip(true);
    setErrorMessage(null);
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
      await loadSalesSlipPage();
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
      await loadSalesSlipPage();
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
    setSalesSlipPageData((current) => ({
      ...current,
      content: current.content.map((item) =>
        item.id === salesSlip.id ? salesSlip : item,
      ),
    }));
  }

  function selectSalesSlip(salesSlipId: number) {
    setSelectedSlipId(salesSlipId);
  }

  return {
    partners,
    filteredBusinessPartners,
    paginatedBusinessPartners,
    salesSlips: salesSlipPageData.content,
    filteredSalesSlips: salesSlipPageData.content,
    paginatedSalesSlips: salesSlipPageData.content,
    salesSlipCurrentPage: visibleSalesSlipPage,
    salesSlipPageSize,
    salesSlipTotalPages,
    salesSlipTotalElements: salesSlipPageData.totalElements,
    partnerCurrentPage: visiblePartnerPage,
    partnerPageSize,
    partnerTotalPages,
    selectedSalesSlip,
    loadingSalesSlipDetail: false,
    loadingSalesSlipPage,
    selectedPartnerId,
    selectedBusinessPartner,
    partnerForm,
    partnerEditForm,
    filters,
    partnerFilters,
    salesForm,
    showCreateSlip,
    editingSlipId,
    savingBusinessPartner,
    savingBusinessPartnerEdit,
    savingSlip,
    updatingSlipStatus,
    errorMessage,
    totalAmount,
    addAllocation,
    addSalesItem,
    removeAllocation,
    removeSalesItem,
    selectBusinessPartner,
    selectSalesSlip,
    selectSalesType,
    setShowCreateSlip,
    startCreateSalesSlip,
    startEditSalesSlip,
    cancelSalesSlipEditing,
    resetFilters,
    resetPartnerFilters,
    setSalesSlipPage,
    setSalesSlipPageSize,
    setPartnerPage,
    setPartnerPageSize,
    updateAllocation,
    updateBusinessPartnerForm,
    updateBusinessPartnerEditForm,
    updateFilters,
    updatePartnerFilters,
    updateSalesForm,
    updateItem,
    updateSalesSlip,
    handleCompleteSalesSlip,
    handleCancelSalesSlip,
    handleCreateBusinessPartner,
    handleUpdateBusinessPartner,
    handleCreateSalesSlip,
  };
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

function createEmptySalesSlipPage(): SalesSlipPage {
  return {
    content: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 1,
  };
}
