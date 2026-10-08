"use client";

import { useSearchParams } from "next/navigation";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import { readSalesRouteState } from "../lib/salesRouteParams";
import { useSalesSlips } from "../model/useSalesSlips";
import { SalesFilters } from "./slips/SalesFilters";
import { SalesSlipCreateForm } from "./slips/SalesSlipCreateForm";
import { SalesSlipDetail } from "./slips/SalesSlipDetail";
import { SalesSlipList } from "./slips/SalesSlipList";

export function SalesSlipsPage({
  initialShowCreateSlip = false,
  v2 = false,
}: {
  initialShowCreateSlip?: boolean;
  v2?: boolean;
}) {
  const routeState = readSalesRouteState(useSearchParams());
  const sales = useSalesSlips({
    initialShowCreateSlip,
    routeState,
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
        {v2 ? (
          <section className="rounded-md border bg-white px-4 py-3">
            <h2 className="font-bold">전표</h2>
            <p className="mt-1 text-sm text-[#68756c]">
              일반 판매와 경매 출하를 함께 조회합니다. 새 전표에서 판매 유형을
              선택하세요.
            </p>
          </section>
        ) : null}
        <SalesFilters
          filters={sales.filters}
          onChange={sales.updateFilters}
          onReset={sales.resetFilters}
          onSearch={sales.searchSalesSlips}
        />

        {sales.showCreateSlip ? (
          <SalesSlipCreateForm
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
            v2={v2}
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
            v2={v2}
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
