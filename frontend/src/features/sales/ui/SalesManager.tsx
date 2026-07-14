"use client";

import type { FormEvent } from "react";
import { useState } from "react";
import type {
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import { useSalesManager } from "../model/useSalesManager";
import type { SalesManagerProps } from "../model/types";
import { AuctionSettlementView } from "./auction/AuctionSettlementView";
import { AuctionTrackingView } from "./auction/AuctionTrackingView";
import { SalesTabLayout, SalesTabSplit } from "./common/SalesTabLayout";
import { BusinessPartnerCreateForm } from "./partners/BusinessPartnerCreateForm";
import { BusinessPartnerFilters } from "./partners/BusinessPartnerFilters";
import { BusinessPartnerList } from "./partners/BusinessPartnerList";
import { PartnerSettlementSettingsSection } from "./partners/PartnerSettlementSettingsSection";
import { SalesFilters } from "./slips/SalesFilters";
import { SalesSlipCreateForm } from "./slips/SalesSlipCreateForm";
import { SalesSlipDetail } from "./slips/SalesSlipDetail";
import { SalesSlipList } from "./slips/SalesSlipList";

export function SalesManager({
  activeTab,
  initialShowCreateSlip = false,
  initialBusinessPartners,
  initialSalesSlipPage,
  initialAuctionPage,
  initialAuctionSummary,
  initialAuctionSettlements,
}: SalesManagerProps) {
  const sales = useSalesManager(
    initialBusinessPartners ?? [],
    initialSalesSlipPage,
    initialShowCreateSlip,
  );
  const [showCreatePartner, setShowCreatePartner] = useState(false);

  async function handleCreateSalesSlip(event: FormEvent<HTMLFormElement>) {
    await sales.handleCreateSalesSlip(event);
  }

  async function handleCreateBusinessPartner(
    event: FormEvent<HTMLFormElement>,
  ) {
    const created = await sales.handleCreateBusinessPartner(event);
    if (created) {
      setShowCreatePartner(false);
    }
  }

  function handleToggleCreateSalesSlip() {
    const nextOpen = !sales.showCreateSlip;
    if (nextOpen) {
      sales.startCreateSalesSlip();
    } else {
      sales.cancelSalesSlipEditing();
    }
  }

  function handleEditSalesSlip(salesSlipId: number) {
    void sales.startEditSalesSlip(salesSlipId);
  }

  const auctionPage = initialAuctionPage ?? createEmptyAuctionPage();
  const auctionSummary = initialAuctionSummary ?? createEmptyAuctionSummary();
  const auctionSettlements = initialAuctionSettlements ?? [];

  return (
    <SalesTabLayout>
      {activeTab === "SLIPS" ? (
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
              mode={sales.editingSlipId == null ? "create" : "edit"}
              saving={sales.savingSlip}
              totalAmount={sales.totalAmount}
              onAddAllocation={sales.addAllocation}
              onAddItem={sales.addSalesItem}
              onAllocationChange={sales.updateAllocation}
              onAllocationRemove={sales.removeAllocation}
              onCancel={() => {
                sales.cancelSalesSlipEditing();
              }}
              onChange={sales.updateSalesForm}
              onRemoveItem={sales.removeSalesItem}
              onSubmit={handleCreateSalesSlip}
              onSalesTypeChange={sales.selectSalesType}
              onUpdateItem={sales.updateItem}
            />
          ) : null}

          <SalesTabSplit>
            <SalesSlipList
              currentPage={sales.salesSlipCurrentPage}
              pageSize={sales.salesSlipPageSize}
              salesSlips={sales.paginatedSalesSlips}
              selectedSalesSlipId={sales.selectedSalesSlip?.id ?? null}
              totalPages={sales.salesSlipTotalPages}
              totalSalesSlips={sales.salesSlipTotalElements}
              onSelect={sales.selectSalesSlip}
              onCreateSalesSlip={handleToggleCreateSalesSlip}
              onPageChange={sales.setSalesSlipPage}
              onPageSizeChange={(pageSize) => {
                sales.setSalesSlipPageSize(pageSize);
                sales.setSalesSlipPage(0);
              }}
            />
            <SalesSlipDetail
              loading={sales.loadingSalesSlipDetail}
              salesSlip={sales.selectedSalesSlip}
              updatingSalesStatus={sales.updatingSlipStatus}
              onCancelSalesSlip={sales.handleCancelSalesSlip}
              onEditSalesSlip={handleEditSalesSlip}
              onCompleteSalesSlip={sales.handleCompleteSalesSlip}
              onPaymentConfirmed={sales.updateSalesSlip}
            />
          </SalesTabSplit>
        </>
      ) : activeTab === "AUCTION" ? (
        <AuctionTrackingView
          initialPage={auctionPage}
          initialSummary={auctionSummary}
        />
      ) : activeTab === "SETTLEMENT" ? (
        <AuctionSettlementView initialSettlements={auctionSettlements} />
      ) : (
        <>
          <BusinessPartnerFilters
            filters={sales.partnerFilters}
            onChange={sales.updatePartnerFilters}
            onReset={sales.resetPartnerFilters}
          />
          <SalesTabSplit columns="lg:grid-cols-[420px_minmax(0,1fr)]">
            <BusinessPartnerList
              currentPage={sales.partnerCurrentPage}
              pageSize={sales.partnerPageSize}
              partners={sales.paginatedBusinessPartners}
              selectedBusinessPartnerId={
                showCreatePartner ? null : sales.selectedPartnerId
              }
              totalPages={sales.partnerTotalPages}
              totalPartners={sales.filteredBusinessPartners.length}
              onSelectBusinessPartner={(partnerId) => {
                setShowCreatePartner(false);
                sales.selectBusinessPartner(partnerId);
              }}
              onCreateBusinessPartner={() => setShowCreatePartner(true)}
              onPageChange={sales.setPartnerPage}
              onPageSizeChange={(pageSize) => {
                sales.setPartnerPageSize(pageSize);
                sales.setPartnerPage(0);
              }}
            />
            <div className="space-y-4">
              {showCreatePartner ? (
                <BusinessPartnerCreateForm
                  form={sales.partnerForm}
                  saving={sales.savingBusinessPartner}
                  onChange={sales.updateBusinessPartnerForm}
                  onSubmit={handleCreateBusinessPartner}
                />
              ) : null}
              <PartnerSettlementSettingsSection
                key={sales.selectedBusinessPartner?.id ?? "empty"}
                partner={
                  showCreatePartner ? null : sales.selectedBusinessPartner
                }
              />
            </div>
          </SalesTabSplit>
        </>
      )}
    </SalesTabLayout>
  );
}

function createEmptyAuctionPage(): AuctionLotPage {
  return {
    content: [],
    totalElements: 0,
    totalPages: 1,
    size: 20,
    page: 0,
  };
}

function createEmptyAuctionSummary(): AuctionTrackingSummary {
  return {
    lotCount: 0,
    shippedQuantity: 0,
    soldQuantity: 0,
    waitingQuantity: 0,
    returnedQuantity: 0,
    reviewRequiredCount: 0,
    totalAmount: 0,
  };
}
