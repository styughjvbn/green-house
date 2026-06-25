"use client";

import { useSalesManager } from "../model/useSalesManager";
import type { SalesManagerProps } from "../model/types";
import { CustomerCreateForm } from "./components/CustomerCreateForm";
import { CustomerList } from "./components/CustomerList";
import { SalesSlipCreateForm } from "./components/SalesSlipCreateForm";
import { SalesSlipList } from "./components/SalesSlipList";

export function SalesManager({
  initialCustomers,
  initialSalesSlips,
}: SalesManagerProps) {
  const sales = useSalesManager(initialCustomers, initialSalesSlips);

  return (
    <div className="grid gap-5 xl:grid-cols-[360px_minmax(0,1fr)]">
      <section className="space-y-4">
        <CustomerCreateForm
          form={sales.customerForm}
          saving={sales.savingCustomer}
          onChange={sales.updateCustomerForm}
          onSubmit={sales.handleCreateCustomer}
        />
        <CustomerList
          customers={sales.customers}
          selectedCustomerId={sales.salesForm.customerId}
          onSelectCustomer={sales.selectCustomer}
        />
      </section>

      <section className="space-y-4">
        <SalesSlipCreateForm
          customers={sales.customers}
          errorMessage={sales.errorMessage}
          form={sales.salesForm}
          saving={sales.savingSlip}
          totalAmount={sales.totalAmount}
          onAddItem={sales.addSalesItem}
          onChange={sales.updateSalesForm}
          onRemoveItem={sales.removeSalesItem}
          onSubmit={sales.handleCreateSalesSlip}
          onUpdateItem={sales.updateItem}
        />
        <SalesSlipList salesSlips={sales.salesSlips} />
      </section>
    </div>
  );
}
