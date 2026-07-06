import { FormEvent, useMemo, useState } from "react";
import type {
  AuctionShipmentOption,
  BusinessPartner,
  SalesSlip,
} from "@/entities/farm/types";
import {
  createBusinessPartner,
  createSalesSlip,
  getAuctionShipmentOptions,
} from "../api/salesApi";
import {
  calculateSalesTotal,
  createEmptyBusinessPartnerForm,
  createEmptySalesItem,
  createInitialSalesForm,
  createInitialSalesFilters,
  filterSalesSlips,
  resetSalesSlipFormAfterSave,
  toCreateBusinessPartnerPayload,
  toCreateSalesSlipPayload,
} from "../lib/salesForm";
import type {
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
  const [showCreateSlip, setShowCreateSlip] = useState(false);
  const [selectedSlipId, setSelectedSlipId] = useState<number | null>(
    initialSalesSlips[0]?.id ?? null,
  );
  const [selectedPartnerId, setSelectedPartnerId] = useState<number | null>(
    initialBusinessPartners[0]?.id ?? null,
  );
  const [savingBusinessPartner, setSavingBusinessPartner] = useState(false);
  const [savingSlip, setSavingSlip] = useState(false);
  const [auctionShipments, setAuctionShipments] = useState<
    AuctionShipmentOption[]
  >([]);
  const [loadingAuctionShipments, setLoadingAuctionShipments] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const totalAmount = useMemo(
    () =>
      salesForm.salesType === "AUCTION"
        ? 0
        : calculateSalesTotal(salesForm.items),
    [salesForm.items, salesForm.salesType],
  );
  const filteredSalesSlips = useMemo(
    () => filterSalesSlips(salesSlips, filters),
    [salesSlips, filters],
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

  function resetFilters() {
    setFilters(createInitialSalesFilters());
  }

  async function selectSalesType(salesType: SalesSlipForm["salesType"]) {
    updateSalesForm("salesType", salesType);
    if (salesType !== "AUCTION" || auctionShipments.length > 0) return;
    setLoadingAuctionShipments(true);
    setErrorMessage(null);
    try {
      const options = await getAuctionShipmentOptions();
      setAuctionShipments(options);
      const first = options[0];
      if (first) {
        setSalesForm((current) => ({
          ...current,
          auctionShipmentId: String(first.id),
          saleDate: first.shipmentDate,
        }));
      }
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "출하 기록을 조회하지 못했습니다.",
      );
    } finally {
      setLoadingAuctionShipments(false);
    }
  }

  function selectAuctionShipment(shipmentId: string) {
    const shipment = auctionShipments.find(
      (option) => String(option.id) === shipmentId,
    );
    setSalesForm((current) => ({
      ...current,
      auctionShipmentId: shipmentId,
      saleDate: shipment?.shipmentDate ?? current.saleDate,
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
    const partner = partners.find((item) => item.id === partnerId);
    if (partner?.partnerType !== "AUCTION_HOUSE") {
      updateSalesForm("partnerId", String(partnerId));
    }
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
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
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
      if (salesSlip.auctionShipmentId) {
        setAuctionShipments((current) =>
          current.filter(
            (shipment) => shipment.id !== salesSlip.auctionShipmentId,
          ),
        );
      }
      setSelectedSlipId(salesSlip.id);
      setShowCreateSlip(false);
      setSalesForm((current) => resetSalesSlipFormAfterSave(current));
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
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
    salesSlips,
    filteredSalesSlips,
    selectedSalesSlip,
    selectedPartnerId,
    selectedBusinessPartner,
    partnerForm,
    activeTab,
    filters,
    salesForm,
    showCreateSlip,
    savingBusinessPartner,
    savingSlip,
    auctionShipments,
    loadingAuctionShipments,
    errorMessage,
    totalAmount,
    addSalesItem,
    removeSalesItem,
    selectBusinessPartner,
    selectSalesSlip: setSelectedSlipId,
    selectSalesType,
    selectAuctionShipment,
    setActiveTab,
    setShowCreateSlip,
    resetFilters,
    updateBusinessPartnerForm,
    updateFilters,
    updateSalesForm,
    updateItem,
    updateSalesSlip,
    handleCreateBusinessPartner,
    handleCreateSalesSlip,
  };
}
