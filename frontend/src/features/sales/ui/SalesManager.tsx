"use client";

import { useSalesManager } from "../model/useSalesManager";
import type { SalesManagerProps } from "../model/types";
import { BusinessPartnerCreateForm } from "./components/BusinessPartnerCreateForm";
import { BusinessPartnerList } from "./components/BusinessPartnerList";
import { SalesFilters } from "./components/SalesFilters";
import { SalesSlipCreateForm } from "./components/SalesSlipCreateForm";
import { SalesSlipDetail } from "./components/SalesSlipDetail";
import { SalesSlipList } from "./components/SalesSlipList";
import { SalesToolbar } from "./components/SalesToolbar";
import { AuctionSettlementView } from "./AuctionSettlementView";
import { AuctionTrackingView } from "./AuctionTrackingView";

export function SalesManager({
  initialBusinessPartners,
  initialSalesSlips,
  initialAuctionPage,
  initialAuctionSummary,
}: SalesManagerProps) {
  const sales = useSalesManager(initialBusinessPartners, initialSalesSlips);

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
            <SalesSlipDetail salesSlip={sales.selectedSalesSlip} />
          </div>
        </>
      ) : sales.activeTab === "AUCTION" ? (
        <AuctionTrackingView
          initialPage={initialAuctionPage}
          initialSummary={initialAuctionSummary}
        />
      ) : sales.activeTab === "SETTLEMENT" ? (
        <AuctionSettlementView lots={initialAuctionPage.content} />
      ) : (
        <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
          <BusinessPartnerCreateForm
            form={sales.partnerForm}
            saving={sales.savingBusinessPartner}
            onChange={sales.updateBusinessPartnerForm}
            onSubmit={sales.handleCreateBusinessPartner}
          />
          <BusinessPartnerList
            partners={sales.partners}
            selectedBusinessPartnerId={sales.salesForm.partnerId}
            onSelectBusinessPartner={sales.selectBusinessPartner}
          />
        </div>
      )}
    </div>
  );
}
