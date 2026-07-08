"use client";

import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type {
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import { useSalesManager } from "../model/useSalesManager";
import type { SalesManagerProps } from "../model/types";
import { AuctionSettlementView } from "./AuctionSettlementView";
import { AuctionTrackingView } from "./AuctionTrackingView";
import { BusinessPartnerCreateForm } from "./components/BusinessPartnerCreateForm";
import { BusinessPartnerFilters } from "./components/BusinessPartnerFilters";
import { BusinessPartnerList } from "./components/BusinessPartnerList";
import { PartnerSettlementSettingsSection } from "./components/PartnerSettlementSettingsSection";
import { SalesFilters } from "./components/SalesFilters";
import { SalesSlipCreateForm } from "./components/SalesSlipCreateForm";
import { SalesSlipDetail } from "./components/SalesSlipDetail";
import { SalesSlipList } from "./components/SalesSlipList";

export function SalesManager({
  activeTab,
  initialBusinessPartners,
  initialSalesSlips,
  initialAuctionPage,
  initialAuctionSummary,
  initialAuctionSettlements,
}: SalesManagerProps) {
  const sales = useSalesManager(
    initialBusinessPartners ?? [],
    initialSalesSlips ?? [],
  );
  const [showCreatePartner, setShowCreatePartner] = useState(false);
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const createSlip = searchParams.get("createSlip") === "1";

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

  async function handleCreateBusinessPartner(
    event: FormEvent<HTMLFormElement>,
  ) {
    const created = await sales.handleCreateBusinessPartner(event);
    if (created) {
      setShowCreatePartner(false);
    }
  }

  function handleToggleCreateSalesSlip() {
    if (!sales.showCreateSlip) {
      sales.startCreateSalesSlip();
    } else {
      sales.cancelSalesSlipEditing();
    }
    updateCreateSlip(!sales.showCreateSlip);
  }

  function handleEditSalesSlip(salesSlipId: number) {
    sales.startEditSalesSlip(salesSlipId);
    updateCreateSlip(true);
  }

  const auctionPage = initialAuctionPage ?? createEmptyAuctionPage();
  const auctionSummary = initialAuctionSummary ?? createEmptyAuctionSummary();
  const auctionSettlements = initialAuctionSettlements ?? [];

  return (
    <div className="space-y-4">
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
                updateCreateSlip(false);
              }}
              onChange={sales.updateSalesForm}
              onRemoveItem={sales.removeSalesItem}
              onSubmit={handleCreateSalesSlip}
              onSalesTypeChange={sales.selectSalesType}
              onUpdateItem={sales.updateItem}
            />
          ) : null}

          <div className="grid gap-4 2xl:grid-cols-[minmax(0,0.88fr)_minmax(0,1.12fr)]">
            <SalesSlipList
              currentPage={sales.salesSlipCurrentPage}
              pageSize={sales.salesSlipPageSize}
              salesSlips={sales.paginatedSalesSlips}
              selectedSalesSlipId={sales.selectedSalesSlip?.id ?? null}
              totalPages={sales.salesSlipTotalPages}
              totalSalesSlips={sales.filteredSalesSlips.length}
              onSelect={sales.selectSalesSlip}
              onCreateSalesSlip={handleToggleCreateSalesSlip}
              onPageChange={sales.setSalesSlipPage}
              onPageSizeChange={(pageSize) => {
                sales.setSalesSlipPageSize(pageSize);
                sales.setSalesSlipPage(0);
              }}
            />
            <SalesSlipDetail
              salesSlip={sales.selectedSalesSlip}
              updatingSalesStatus={sales.updatingSlipStatus}
              onCancelSalesSlip={sales.handleCancelSalesSlip}
              onEditSalesSlip={handleEditSalesSlip}
              onCompleteSalesSlip={sales.handleCompleteSalesSlip}
              onPaymentConfirmed={sales.updateSalesSlip}
            />
          </div>
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
          <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
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
          </div>
        </>
      )}
    </div>
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
