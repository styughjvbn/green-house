"use client";

import type { BusinessPartner, SalesSlipPage } from "@/entities/farm/types";
import { usePagedListUrlActions } from "@/shared/api/usePagedListUrlActions";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import {
  SALES_FILTER_KEYS,
  writeSalesFilterParams,
} from "../lib/salesUrlFilters";
import { useSalesSlips } from "../model/useSalesSlips";
import type { SalesFilterState } from "../model/types";
import { SalesFilters } from "./slips/SalesFilters";
import { SalesSlipCreateForm } from "./slips/SalesSlipCreateForm";
import { SalesSlipDetail } from "./slips/SalesSlipDetail";
import { SalesSlipList } from "./slips/SalesSlipList";

export function SalesSlipsPage({
  initialBusinessPartners,
  initialFilters,
  initialPage,
  initialShowCreateSlip = false,
}: {
  initialBusinessPartners: BusinessPartner[];
  initialFilters: SalesFilterState;
  initialPage: SalesSlipPage;
  initialShowCreateSlip?: boolean;
}) {
  const sales = useSalesSlips({
    initialBusinessPartners,
    initialPage,
    initialShowCreateSlip,
    initialFilters,
  });

  const listActions = usePagedListUrlActions({
    filters: sales.filters,
    filterKeys: SALES_FILTER_KEYS,
    writeFilterParams: writeSalesFilterParams,
    onSearch: sales.searchSalesSlips,
    onReset: sales.resetFilters,
    onPageChange: sales.setSalesSlipPage,
    onPageSizeChange: sales.setSalesSlipPageSize,
  });

  function handleToggleCreateSalesSlip() {
    const nextOpen = !sales.showCreateSlip;
    if (nextOpen) {
      sales.startCreateSalesSlip();
    } else {
      sales.cancelSalesSlipEditing();
    }
  }

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <SalesFilters
          partners={sales.partners}
          filters={sales.filters}
          onChange={sales.updateFilters}
          onReset={listActions.reset}
          onSearch={listActions.search}
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
            onSubmit={sales.handleCreateSalesSlip}
            onSalesTypeChange={sales.selectSalesType}
            onUpdateItem={sales.updateItem}
          />
        ) : null}

        <TabError message={sales.errorMessage} />

        <TabSplit>
          <SalesSlipList
            currentPage={sales.salesSlipCurrentPage}
            pageSize={sales.salesSlipPageSize}
            salesSlips={sales.salesSlips}
            loading={sales.loadingSalesSlipPage}
            selectedSalesSlipId={sales.selectedSalesSlipId}
            totalPages={sales.salesSlipTotalPages}
            totalSalesSlips={sales.salesSlipTotalElements}
            onSelect={sales.selectSalesSlip}
            onCreateSalesSlip={handleToggleCreateSalesSlip}
            onPageChange={listActions.changePage}
            onPageSizeChange={listActions.changePageSize}
          />
          <SalesSlipDetail
            loading={sales.loadingSalesSlipDetail}
            salesSlip={sales.selectedSalesSlip}
            updatingSalesStatus={sales.updatingSlipStatus}
            onCancelSalesSlip={sales.handleCancelSalesSlip}
            onEditSalesSlip={sales.startEditSalesSlip}
            onCompleteSalesSlip={sales.handleCompleteSalesSlip}
            onPaymentConfirmed={sales.updateSalesSlip}
          />
        </TabSplit>
      </TabLayout>
    </main>
  );
}
