"use client";

import { useSalesManager } from "../model/useSalesManager";
import type { SalesManagerProps } from "../model/types";
import { CustomerCreateForm } from "./components/CustomerCreateForm";
import { CustomerList } from "./components/CustomerList";
import { SalesFilters } from "./components/SalesFilters";
import { SalesSlipCreateForm } from "./components/SalesSlipCreateForm";
import { SalesSlipDetail } from "./components/SalesSlipDetail";
import { SalesSlipList } from "./components/SalesSlipList";
import { SalesToolbar } from "./components/SalesToolbar";
import { AuctionSettlementView } from "./AuctionSettlementView";
import { AuctionTrackingView } from "./AuctionTrackingView";

export function SalesManager({
  initialCustomers,
  initialSalesSlips,
  initialAuctionLots,
  initialAuctionSummary,
}: SalesManagerProps) {
  const sales = useSalesManager(initialCustomers, initialSalesSlips);

  return (
    <div className="space-y-4">
      <SalesToolbar
        activeTab={sales.activeTab}
        onCreateSlip={() => sales.setShowCreateSlip((current) => !current)}
        onTabChange={sales.setActiveTab}
      />

      {sales.activeTab === "SLIPS" ? (
        <>
          <SalesFilters
            customers={sales.customers}
            filters={sales.filters}
            onChange={sales.updateFilters}
            onReset={sales.resetFilters}
          />

          {sales.showCreateSlip ? (
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
          ) : null}

          <div className="grid gap-4 2xl:grid-cols-[minmax(0,0.88fr)_minmax(0,1.12fr)]">
            <SalesSlipList
              salesSlips={sales.filteredSalesSlips}
              selectedSalesSlipId={sales.selectedSalesSlip?.id ?? null}
              onSelect={sales.selectSalesSlip}
            />
            <SalesSlipDetail salesSlip={sales.selectedSalesSlip} />
          </div>
        </>
      ) : sales.activeTab === "AUCTION" ? (
        <AuctionTrackingView
          initialLots={initialAuctionLots}
          initialSummary={initialAuctionSummary}
        />
      ) : sales.activeTab === "SETTLEMENT" ? (
        <AuctionSettlementView lots={initialAuctionLots} />
      ) : (
        <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
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
        </div>
      )}
    </div>
  );
}
