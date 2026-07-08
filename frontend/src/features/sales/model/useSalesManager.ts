import { FormEvent, useMemo, useState } from "react";
import type {
  BusinessPartner,
  SalesOrchidGroupOption,
  SalesSlip,
} from "@/entities/farm/types";
import {
  changeSalesSlipStatus,
  createBusinessPartner,
  createSalesSlip,
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
  filterSalesSlips,
  resetSalesSlipFormAfterSave,
  toSalesSlipForm,
  toCreateBusinessPartnerPayload,
  toCreateSalesSlipPayload,
} from "../lib/salesForm";
import type {
  BusinessPartnerFilterState,
  BusinessPartnerForm,
  SalesAllocationForm,
  SalesFilterState,
  SalesItemForm,
  SalesSlipForm,
  SalesTab,
} from "./types";

export function useSalesManager(
  initialBusinessPartners: BusinessPartner[],
  initialSalesSlips: SalesSlip[],
) {
  const [partners, setBusinessPartners] = useState<BusinessPartner[]>(
    initialBusinessPartners,
  );
  const [salesSlips, setSalesSlips] = useState<SalesSlip[]>(initialSalesSlips);
  const [partnerForm, setBusinessPartnerForm] = useState<BusinessPartnerForm>(
    createEmptyBusinessPartnerForm(),
  );
  const [salesForm, setSalesForm] = useState<SalesSlipForm>(() =>
    createInitialSalesForm(initialBusinessPartners),
  );
  const [activeTab, setActiveTab] = useState<SalesTab>("SLIPS");
  const [filters, setFilters] = useState<SalesFilterState>(() =>
    createInitialSalesFilters(),
  );
  const [partnerFilters, setPartnerFilters] =
    useState<BusinessPartnerFilterState>(() =>
      createInitialBusinessPartnerFilters(),
    );
  const [showCreateSlip, setShowCreateSlip] = useState(false);
  const [editingSlipId, setEditingSlipId] = useState<number | null>(null);
  const [selectedSlipId, setSelectedSlipId] = useState<number | null>(
    initialSalesSlips[0]?.id ?? null,
  );
  const [selectedPartnerId, setSelectedPartnerId] = useState<number | null>(
    initialBusinessPartners[0]?.id ?? null,
  );
  const [salesSlipPage, setSalesSlipPage] = useState(0);
  const [salesSlipPageSize, setSalesSlipPageSize] = useState(10);
  const [partnerPage, setPartnerPage] = useState(0);
  const [partnerPageSize, setPartnerPageSize] = useState(10);
  const [savingBusinessPartner, setSavingBusinessPartner] = useState(false);
  const [savingSlip, setSavingSlip] = useState(false);
  const [updatingSlipStatus, setUpdatingSlipStatus] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const totalAmount = useMemo(
    () => calculateSalesTotal(salesForm.items),
    [salesForm.items],
  );
  const filteredSalesSlips = useMemo(
    () => filterSalesSlips(salesSlips, filters),
    [salesSlips, filters],
  );
  const filteredBusinessPartners = useMemo(
    () => filterBusinessPartners(partners, partnerFilters),
    [partners, partnerFilters],
  );
  const salesSlipTotalPages = Math.max(
    1,
    Math.ceil(filteredSalesSlips.length / salesSlipPageSize),
  );
  const visibleSalesSlipPage = Math.min(salesSlipPage, salesSlipTotalPages - 1);
  const paginatedSalesSlips = useMemo(() => {
    const start = visibleSalesSlipPage * salesSlipPageSize;
    return filteredSalesSlips.slice(start, start + salesSlipPageSize);
  }, [filteredSalesSlips, salesSlipPageSize, visibleSalesSlipPage]);
  const partnerTotalPages = Math.max(
    1,
    Math.ceil(filteredBusinessPartners.length / partnerPageSize),
  );
  const visiblePartnerPage = Math.min(partnerPage, partnerTotalPages - 1);
  const paginatedBusinessPartners = useMemo(() => {
    const start = visiblePartnerPage * partnerPageSize;
    return filteredBusinessPartners.slice(start, start + partnerPageSize);
  }, [filteredBusinessPartners, partnerPageSize, visiblePartnerPage]);
  const selectedSalesSlip =
    filteredSalesSlips.find((salesSlip) => salesSlip.id === selectedSlipId) ??
    filteredSalesSlips[0] ??
    null;
  const selectedBusinessPartner =
    partners.find((partner) => partner.id === selectedPartnerId) ?? null;

  function updateBusinessPartnerForm<K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) {
    setBusinessPartnerForm((current) => ({ ...current, [field]: value }));
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
      items: current.items.map((item, itemIndex) =>
        itemIndex === index ? { ...item, [field]: value } : item,
      ),
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
      items: current.items.map((item, itemIndex) =>
        itemIndex === index
          ? {
              ...item,
              allocations: item.allocations.filter(
                (_, currentIndex) => currentIndex !== allocationIndex,
              ),
            }
          : item,
      ),
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
      setSalesSlips((current) =>
        editingSlipId == null
          ? [salesSlip, ...current]
          : current.map((item) =>
              item.id === salesSlip.id ? salesSlip : item,
            ),
      );
      setSelectedSlipId(salesSlip.id);
      setSalesSlipPage(0);
      setShowCreateSlip(false);
      setSalesForm((current) => resetSalesSlipFormAfterSave(current));
      setEditingSlipId(null);
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
    setShowCreateSlip((current) => {
      const next = !current;
      if (next) {
        setSalesForm(createInitialSalesForm(partners));
      }
      return next;
    });
  }

  function startEditSalesSlip(salesSlipId: number) {
    const salesSlip = salesSlips.find((item) => item.id === salesSlipId);
    if (!salesSlip) return;
    setEditingSlipId(salesSlipId);
    setSelectedSlipId(salesSlipId);
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
      const current = salesSlips.find((item) => item.id === salesSlipId);
      const nextStatus =
        current?.salesType === "AUCTION" ? "출하 완료" : "출고 완료";
      const updated = await changeSalesSlipStatus(salesSlipId, {
        salesStatus: nextStatus,
        memo: null,
      });
      updateSalesSlip(updated);
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
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "전표를 취소하지 못했습니다.",
      );
    } finally {
      setUpdatingSlipStatus(false);
    }
  }

  function updateSalesSlip(salesSlip: SalesSlip) {
    setSalesSlips((current) =>
      current.map((item) => (item.id === salesSlip.id ? salesSlip : item)),
    );
  }

  return {
    partners,
    filteredBusinessPartners,
    paginatedBusinessPartners,
    salesSlips,
    filteredSalesSlips,
    paginatedSalesSlips,
    salesSlipCurrentPage: visibleSalesSlipPage,
    salesSlipPageSize,
    salesSlipTotalPages,
    partnerCurrentPage: visiblePartnerPage,
    partnerPageSize,
    partnerTotalPages,
    selectedSalesSlip,
    selectedPartnerId,
    selectedBusinessPartner,
    partnerForm,
    activeTab,
    filters,
    partnerFilters,
    salesForm,
    showCreateSlip,
    editingSlipId,
    savingBusinessPartner,
    savingSlip,
    updatingSlipStatus,
    errorMessage,
    totalAmount,
    addAllocation,
    addSalesItem,
    removeAllocation,
    removeSalesItem,
    selectBusinessPartner,
    selectSalesSlip: setSelectedSlipId,
    selectSalesType,
    setActiveTab,
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
    updateFilters,
    updatePartnerFilters,
    updateSalesForm,
    updateItem,
    updateSalesSlip,
    handleCompleteSalesSlip,
    handleCancelSalesSlip,
    handleCreateBusinessPartner,
    handleCreateSalesSlip,
  };
}
