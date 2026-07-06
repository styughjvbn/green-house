import { FormEvent, useMemo, useState } from "react";
import type { BusinessPartner, SalesSlip } from "@/entities/farm/types";
import { createBusinessPartner, createSalesSlip } from "../api/salesApi";
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
  toCreateBusinessPartnerPayload,
  toCreateSalesSlipPayload,
} from "../lib/salesForm";
import type {
  BusinessPartnerFilterState,
  BusinessPartnerForm,
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
  const [selectedSlipId, setSelectedSlipId] = useState<number | null>(
    initialSalesSlips[0]?.id ?? null,
  );
  const [selectedPartnerId, setSelectedPartnerId] = useState<number | null>(
    initialBusinessPartners[0]?.id ?? null,
  );
  const [savingBusinessPartner, setSavingBusinessPartner] = useState(false);
  const [savingSlip, setSavingSlip] = useState(false);
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
    setFilters((current) => ({ ...current, [field]: value }));
  }

  function updatePartnerFilters<K extends keyof BusinessPartnerFilterState>(
    field: K,
    value: BusinessPartnerFilterState[K],
  ) {
    setPartnerFilters((current) => ({ ...current, [field]: value }));
  }

  function resetFilters() {
    setFilters(createInitialSalesFilters());
  }

  function resetPartnerFilters() {
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
      salesStatus: salesType === "AUCTION" ? "출하 완료" : "작성중",
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
      const salesSlip = await createSalesSlip(
        toCreateSalesSlipPayload(salesForm),
      );
      setSalesSlips((current) => [salesSlip, ...current]);
      setSelectedSlipId(salesSlip.id);
      setShowCreateSlip(false);
      setSalesForm((current) => resetSalesSlipFormAfterSave(current));
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

  function updateSalesSlip(salesSlip: SalesSlip) {
    setSalesSlips((current) =>
      current.map((item) => (item.id === salesSlip.id ? salesSlip : item)),
    );
  }

  return {
    partners,
    filteredBusinessPartners,
    salesSlips,
    filteredSalesSlips,
    selectedSalesSlip,
    selectedPartnerId,
    selectedBusinessPartner,
    partnerForm,
    activeTab,
    filters,
    partnerFilters,
    salesForm,
    showCreateSlip,
    savingBusinessPartner,
    savingSlip,
    errorMessage,
    totalAmount,
    addSalesItem,
    removeSalesItem,
    selectBusinessPartner,
    selectSalesSlip: setSelectedSlipId,
    selectSalesType,
    setActiveTab,
    setShowCreateSlip,
    resetFilters,
    resetPartnerFilters,
    updateBusinessPartnerForm,
    updateFilters,
    updatePartnerFilters,
    updateSalesForm,
    updateItem,
    updateSalesSlip,
    handleCreateBusinessPartner,
    handleCreateSalesSlip,
  };
}
