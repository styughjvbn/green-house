"use client";
import { useRef, useState, type ReactNode } from "react";
import { FilterResetButton } from "@/shared/ui/FilterControls";
import { hasFilterConditions } from "@/shared/ui/listFilterBadges";
import { useSearchParams } from "next/navigation";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import { readSalesRouteState } from "../lib/salesRouteParams";
import { useSalesSlips } from "../model/useSalesSlips";
import { SalesFilters } from "./slips/SalesFilters";
import { SalesSlipRegistrationMethodDialog } from "./slips/SalesSlipRegistrationMethodDialog";
import { SalesSlipPhotoRegistrationDialog } from "./slips/SalesSlipPhotoRegistrationDialog";
import { SalesSlipCreateForm } from "./slips/SalesSlipCreateForm";
import { SalesSlipDetail } from "./slips/SalesSlipDetail";
import { SalesSlipList } from "./slips/SalesSlipList";
export function SalesSlipsPage({
  initialShowCreateSlip = false,
}: {
  initialShowCreateSlip?: boolean;
}) {
  const routeState = readSalesRouteState(useSearchParams());
  const sales = useSalesSlips({
    routeState,
  });
  const [registrationMethodOpen, setRegistrationMethodOpen] = useState(
    initialShowCreateSlip,
  );
  const [photoRegistrationOpen, setPhotoRegistrationOpen] = useState(false);
  const registrationOpener = useRef<HTMLElement | null>(null);
  function restoreRegistrationFocus() {
    requestAnimationFrame(() => registrationOpener.current?.focus());
  }
  function handleToggleCreateSalesSlip() {
    registrationOpener.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    setRegistrationMethodOpen(true);
  }
  const toolbar = (columnSettings: ReactNode) => (
    <SalesFilters
      loading={sales.loadingSalesSlipPage}
      filters={sales.filters}
      appliedFilters={routeState.filters}
      columnSettings={columnSettings}
      onRemove={sales.removeFilters}
      onResetFilters={sales.resetPopupFilters}
      onChange={sales.updateFilters}
      onSalesTypeChange={sales.setSalesTypeFilter}
      onReset={sales.resetFilters}
      onSearch={sales.searchSalesSlips}
    />
  );

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        {registrationMethodOpen ? (
          <SalesSlipRegistrationMethodDialog
            onClose={() => {
              setRegistrationMethodOpen(false);
              restoreRegistrationFocus();
            }}
            onSelect={(method) => {
              setRegistrationMethodOpen(false);
              if (method === "photo") setPhotoRegistrationOpen(true);
              else sales.startCreateSalesSlip();
            }}
          />
        ) : null}
        {photoRegistrationOpen ? (
          <SalesSlipPhotoRegistrationDialog
            onClose={() => {
              setPhotoRegistrationOpen(false);
              restoreRegistrationFocus();
            }}
            onSaved={sales.onPhotoSlipSaved}
          />
        ) : null}
        {sales.showCreateSlip ? (
          <SalesSlipCreateForm
            errorMessage={sales.errorMessage}
            form={sales.salesForm}
            mode={sales.editingSlipId == null ? "create" : "edit"}
            saving={sales.savingSlip}
            totalAmount={sales.totalAmount}
            onSelectGroups={sales.selectSalesGroups}
            onRemoveGroup={sales.removeSalesGroup}
            onAutomaticAllocation={sales.useAutomaticAllocation}
            onResetAllocation={sales.resetSalesAllocation}
            selectedGroups={sales.selectedGroups}
            allocationIssue={sales.allocationIssue}
            onAddItem={sales.addSalesItem}
            onAllocationChange={sales.updateAllocation}
            onCancel={() => {
              sales.cancelSalesSlipEditing();
              if (sales.editingSlipId == null) restoreRegistrationFocus();
            }}
            onChange={sales.updateSalesForm}
            onRemoveItem={sales.removeSalesItem}
            onSubmit={sales.handleCreateSalesSlip}
            onSalesTypeChange={sales.selectSalesType}
            onRecordingModeChange={sales.setRecordingMode}
            onUpdateItem={sales.updateItem}
          />
        ) : null}

        <TabError message={sales.errorMessage} />

        <TabSplit>
          <SalesSlipList
            hasAppliedFilters={hasFilterConditions(routeState.filters)}
            toolbar={toolbar}
            emptyAction={
              sales.hasEmptySalesSlipResult && !sales.errorMessage ? (
                <FilterResetButton
                  className="lg:mt-0"
                  label="전체 초기화"
                  onClick={sales.resetFilters}
                />
              ) : undefined
            }
            currentPage={sales.salesSlipCurrentPage}
            pageSize={sales.salesSlipPageSize}
            salesSlips={sales.salesSlips}
            loading={sales.loadingSalesSlipPage}
            selectedSalesSlipId={sales.selectedSalesSlipId}
            totalPages={sales.salesSlipTotalPages}
            totalSalesSlips={sales.salesSlipTotalElements}
            onSelect={sales.selectSalesSlip}
            onCreateSalesSlip={handleToggleCreateSalesSlip}
            onPageChange={sales.setSalesSlipPage}
            onPageSizeChange={sales.setSalesSlipPageSize}
          />
          <SalesSlipDetail
            loading={sales.loadingSalesSlipDetail}
            salesSlip={sales.selectedSalesSlip}
            updatingSalesStatus={sales.updatingSlipStatus}
            onCancelSalesSlip={sales.handleCancelSalesSlip}
            onEditSalesSlip={sales.startEditSalesSlip}
            onCompleteSalesSlip={sales.handleCompleteSalesSlip}
          />
        </TabSplit>
      </TabLayout>
    </main>
  );
}
