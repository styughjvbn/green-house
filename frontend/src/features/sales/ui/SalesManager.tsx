"use client";

import { useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { Download, Plus } from "lucide-react";
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

  return (
    <div className="space-y-4">
      {sales.activeTab === "SLIPS" ? (
        <>
          <div className="flex justify-end gap-3">
            <button
              className="inline-flex h-10 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white shadow-sm"
              type="button"
              onClick={() => sales.setShowCreateSlip((current) => !current)}
            >
              <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
              판매 전표 등록
            </button>
          </div>

          <SalesFilters
            partners={sales.partners}
            filters={sales.filters}
            onChange={sales.updateFilters}
            onReset={sales.resetFilters}
          />

          {sales.showCreateSlip ? (
            <SalesSlipCreateForm
              auctionShipments={sales.auctionShipments}
              partners={sales.partners}
              errorMessage={sales.errorMessage}
              form={sales.salesForm}
              saving={sales.savingSlip}
              loadingAuctionShipments={sales.loadingAuctionShipments}
              totalAmount={sales.totalAmount}
              onAddItem={sales.addSalesItem}
              onChange={sales.updateSalesForm}
              onRemoveItem={sales.removeSalesItem}
              onSubmit={sales.handleCreateSalesSlip}
              onSalesTypeChange={sales.selectSalesType}
              onAuctionShipmentChange={sales.selectAuctionShipment}
              onUpdateItem={sales.updateItem}
            />
          ) : null}

          <div className="grid gap-4 2xl:grid-cols-[minmax(0,0.88fr)_minmax(0,1.12fr)]">
            <SalesSlipList
              salesSlips={sales.filteredSalesSlips}
              selectedSalesSlipId={sales.selectedSalesSlip?.id ?? null}
              onSelect={sales.selectSalesSlip}
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
