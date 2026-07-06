"use client";

import type { FormEvent } from "react";
import { useEffect } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useSalesManager } from "../model/useSalesManager";
import type { SalesManagerProps } from "../model/types";
import { BusinessPartnerCreateForm } from "./components/BusinessPartnerCreateForm";
import { BusinessPartnerList } from "./components/BusinessPartnerList";
import { PartnerSettlementSettingsSection } from "./components/PartnerSettlementSettingsSection";
import { SalesFilters } from "./components/SalesFilters";
import { SalesSlipCreateForm } from "./components/SalesSlipCreateForm";
import { SalesSlipDetail } from "./components/SalesSlipDetail";
import { SalesSlipList } from "./components/SalesSlipList";
import { AuctionSettlementView } from "./AuctionSettlementView";
import { AuctionTrackingView } from "./AuctionTrackingView";

export function SalesManager({
  initialBusinessPartners,
  initialSalesSlips,
  initialAuctionPage,
  initialAuctionSummary,
  initialAuctionSettlements,
}: SalesManagerProps) {
  const sales = useSalesManager(initialBusinessPartners, initialSalesSlips);
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const activeTab = searchParams.get("tab") ?? "SLIPS";
  const createSlip = searchParams.get("createSlip") === "1";

  useEffect(() => {
    if (
      activeTab === "SLIPS" ||
      activeTab === "AUCTION" ||
      activeTab === "SETTLEMENT" ||
      activeTab === "PARTNERS"
    ) {
      sales.setActiveTab(activeTab);
    }
  }, [activeTab, sales]);

  useEffect(() => {
    sales.setShowCreateSlip(createSlip);
  }, [createSlip, sales]);

  function updateCreateSlip(nextOpen: boolean) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", "SLIPS");
    if (nextOpen) {
      params.set("createSlip", "1");
    } else {
      params.delete("createSlip");
    }
    router.replace(`${pathname}?${params.toString()}`);
  }

  async function handleCreateSalesSlip(event: FormEvent<HTMLFormElement>) {
    const created = await sales.handleCreateSalesSlip(event);
    if (created) {
      updateCreateSlip(false);
    }
  }

  return (
    <div className="space-y-4">
      {sales.activeTab === "SLIPS" ? (
        <>
          <SalesFilters
            partners={sales.partners}
            filters={sales.filters}
            onChange={sales.updateFilters}
            onReset={sales.resetFilters}
          />

          {sales.showCreateSlip ? (
            <SalesSlipCreateForm
              partners={sales.partners}
              errorMessage={sales.errorMessage}
              form={sales.salesForm}
              saving={sales.savingSlip}
              totalAmount={sales.totalAmount}
              onAddItem={sales.addSalesItem}
              onChange={sales.updateSalesForm}
              onRemoveItem={sales.removeSalesItem}
              onSubmit={handleCreateSalesSlip}
              onSalesTypeChange={sales.selectSalesType}
              onUpdateItem={sales.updateItem}
            />
          ) : null}

          <div className="grid gap-4 2xl:grid-cols-[minmax(0,0.88fr)_minmax(0,1.12fr)]">
            <SalesSlipList
              salesSlips={sales.filteredSalesSlips}
              selectedSalesSlipId={sales.selectedSalesSlip?.id ?? null}
              onSelect={sales.selectSalesSlip}
              onCreateSalesSlip={() => updateCreateSlip(!sales.showCreateSlip)}
            />
            <SalesSlipDetail
              salesSlip={sales.selectedSalesSlip}
              onPaymentConfirmed={sales.updateSalesSlip}
            />
          </div>
        </>
      ) : sales.activeTab === "AUCTION" ? (
        <AuctionTrackingView
          initialPage={initialAuctionPage}
          initialSummary={initialAuctionSummary}
        />
      ) : sales.activeTab === "SETTLEMENT" ? (
        <AuctionSettlementView initialSettlements={initialAuctionSettlements} />
      ) : (
        <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
          <BusinessPartnerCreateForm
            form={sales.partnerForm}
            saving={sales.savingBusinessPartner}
            onChange={sales.updateBusinessPartnerForm}
            onSubmit={sales.handleCreateBusinessPartner}
          />
          <div className="space-y-4">
            <BusinessPartnerList
              partners={sales.partners}
              selectedBusinessPartnerId={sales.selectedPartnerId}
              onSelectBusinessPartner={sales.selectBusinessPartner}
            />
            <PartnerSettlementSettingsSection
              key={sales.selectedBusinessPartner?.id ?? "empty"}
              partner={sales.selectedBusinessPartner}
            />
          </div>
        </div>
      )}
    </div>
  );
}
