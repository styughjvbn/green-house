import { FormEvent, useMemo, useState } from "react";
import type { Customer, SalesSlip } from "@/entities/farm/types";
import { createCustomer, createSalesSlip } from "../api/salesApi";
import {
  calculateSalesTotal,
  createEmptyCustomerForm,
  createEmptySalesItem,
  createInitialSalesForm,
  createInitialSalesFilters,
  filterSalesSlips,
  resetSalesSlipFormAfterSave,
  toCreateCustomerPayload,
  toCreateSalesSlipPayload,
} from "../lib/salesForm";
import type {
  CustomerForm,
  SalesFilterState,
  SalesItemForm,
  SalesSlipForm,
  SalesTab,
} from "./types";

export function useSalesManager(
  initialCustomers: Customer[],
  initialSalesSlips: SalesSlip[],
) {
  const [customers, setCustomers] = useState<Customer[]>(initialCustomers);
  const [salesSlips, setSalesSlips] = useState<SalesSlip[]>(initialSalesSlips);
  const [customerForm, setCustomerForm] = useState<CustomerForm>(
    createEmptyCustomerForm(),
  );
  const [salesForm, setSalesForm] = useState<SalesSlipForm>(() =>
    createInitialSalesForm(initialCustomers),
  );
  const [activeTab, setActiveTab] = useState<SalesTab>("SLIPS");
  const [filters, setFilters] = useState<SalesFilterState>(() =>
    createInitialSalesFilters(),
  );
  const [showCreateSlip, setShowCreateSlip] = useState(false);
  const [selectedSlipId, setSelectedSlipId] = useState<number | null>(
    initialSalesSlips[0]?.id ?? null,
  );
  const [savingCustomer, setSavingCustomer] = useState(false);
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
  const selectedSalesSlip =
    salesSlips.find((salesSlip) => salesSlip.id === selectedSlipId) ??
    filteredSalesSlips[0] ??
    salesSlips[0] ??
    null;

  function updateCustomerForm<K extends keyof CustomerForm>(
    field: K,
    value: CustomerForm[K],
  ) {
    setCustomerForm((current) => ({ ...current, [field]: value }));
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

  function selectCustomer(customerId: number) {
    updateSalesForm("customerId", String(customerId));
  }

  async function handleCreateCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingCustomer(true);
    setErrorMessage(null);

    try {
      const customer = await createCustomer(
        toCreateCustomerPayload(customerForm),
      );
      setCustomers((current) => [...current, customer]);
      setSalesForm((current) => ({
        ...current,
        customerId: String(customer.id),
      }));
      setCustomerForm(createEmptyCustomerForm());
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setSavingCustomer(false);
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
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setSavingSlip(false);
    }
  }

  return {
    customers,
    salesSlips,
    filteredSalesSlips,
    selectedSalesSlip,
    customerForm,
    activeTab,
    filters,
    salesForm,
    showCreateSlip,
    savingCustomer,
    savingSlip,
    errorMessage,
    totalAmount,
    addSalesItem,
    removeSalesItem,
    selectCustomer,
    selectSalesSlip: setSelectedSlipId,
    setActiveTab,
    setShowCreateSlip,
    resetFilters,
    updateCustomerForm,
    updateFilters,
    updateSalesForm,
    updateItem,
    handleCreateCustomer,
    handleCreateSalesSlip,
  };
}
