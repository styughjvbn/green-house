import { FormEvent, useMemo, useState } from "react";
import type { Customer, SalesSlip } from "@/entities/farm/types";
import { createCustomer, createSalesSlip } from "../api/salesApi";
import {
  calculateSalesTotal,
  createEmptyCustomerForm,
  createEmptySalesItem,
  createInitialSalesForm,
  resetSalesSlipFormAfterSave,
  toCreateCustomerPayload,
  toCreateSalesSlipPayload,
} from "../lib/salesForm";
import type { CustomerForm, SalesItemForm, SalesSlipForm } from "./types";

export function useSalesManager(initialCustomers: Customer[], initialSalesSlips: SalesSlip[]) {
  const [customers, setCustomers] = useState<Customer[]>(initialCustomers);
  const [salesSlips, setSalesSlips] = useState<SalesSlip[]>(initialSalesSlips);
  const [customerForm, setCustomerForm] = useState<CustomerForm>(createEmptyCustomerForm());
  const [salesForm, setSalesForm] = useState<SalesSlipForm>(() => createInitialSalesForm(initialCustomers));
  const [savingCustomer, setSavingCustomer] = useState(false);
  const [savingSlip, setSavingSlip] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const totalAmount = useMemo(() => calculateSalesTotal(salesForm.items), [salesForm.items]);

  function updateCustomerForm<K extends keyof CustomerForm>(field: K, value: CustomerForm[K]) {
    setCustomerForm((current) => ({ ...current, [field]: value }));
  }

  function updateSalesForm<K extends keyof SalesSlipForm>(field: K, value: SalesSlipForm[K]) {
    setSalesForm((current) => ({ ...current, [field]: value }));
  }

  function updateItem(index: number, field: keyof SalesItemForm, value: string) {
    setSalesForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) =>
        itemIndex === index ? { ...item, [field]: value } : item,
      ),
    }));
  }

  function addSalesItem() {
    setSalesForm((current) => ({ ...current, items: [...current.items, createEmptySalesItem()] }));
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
      const customer = await createCustomer(toCreateCustomerPayload(customerForm));
      setCustomers((current) => [...current, customer]);
      setSalesForm((current) => ({ ...current, customerId: String(customer.id) }));
      setCustomerForm(createEmptyCustomerForm());
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSavingCustomer(false);
    }
  }

  async function handleCreateSalesSlip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingSlip(true);
    setErrorMessage(null);

    try {
      const salesSlip = await createSalesSlip(toCreateSalesSlipPayload(salesForm));
      setSalesSlips((current) => [salesSlip, ...current]);
      setSalesForm((current) => resetSalesSlipFormAfterSave(current));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSavingSlip(false);
    }
  }

  return {
    customers,
    salesSlips,
    customerForm,
    salesForm,
    savingCustomer,
    savingSlip,
    errorMessage,
    totalAmount,
    addSalesItem,
    removeSalesItem,
    selectCustomer,
    updateCustomerForm,
    updateSalesForm,
    updateItem,
    handleCreateCustomer,
    handleCreateSalesSlip,
  };
}

