"use client";

import type { FormEvent } from "react";
import type { BusinessPartnerPage, SalesSlipPage } from "@/entities/farm/types";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import {
  deleteParams,
  SALES_FILTER_KEYS,
  writeSalesFilterParams,
} from "../lib/salesUrlFilters";
import { useSalesManager } from "../model/useSalesManager";
import type { SalesFilterState } from "../model/types";
import { SalesFilters } from "./slips/SalesFilters";
import { SalesSlipCreateForm } from "./slips/SalesSlipCreateForm";
import { SalesSlipDetail } from "./slips/SalesSlipDetail";
import { SalesSlipList } from "./slips/SalesSlipList";

export function SalesSlipsPage({
  initialBusinessPartnerPage,
  initialFilters,
  initialPage,
  initialShowCreateSlip = false,
}: {
  initialBusinessPartnerPage: BusinessPartnerPage;
  initialFilters: SalesFilterState;
  initialPage: SalesSlipPage;
  initialShowCreateSlip?: boolean;
}) {
  const writeUrlParams = useUrlSearchParamsWriter();
  const sales = useSalesManager({
    initialBusinessPartnerPage,
    initialSalesPage: initialPage,
    initialShowCreateSlip,
    initialSalesFilters: initialFilters,
  });

  const updateFilter = <K extends keyof SalesFilterState>(
    field: K,
    value: SalesFilterState[K],
  ) => {
    sales.updateFilters(field, value);
  };

  const searchSalesSlips = () => {
    sales.searchSalesSlips(sales.filters);
    writeUrlParams((params) => {
      writeSalesFilterParams(params, sales.filters);
      params.set("page", "0");
    });
  };

  async function handleCreateSalesSlip(event: FormEvent<HTMLFormElement>) {
    await sales.handleCreateSalesSlip(event);
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

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <SalesFilters
          partners={sales.partners}
          filters={sales.filters}
          onChange={updateFilter}
          onReset={() => {
            sales.resetFilters();
            writeUrlParams((params) => {
              deleteParams(params, SALES_FILTER_KEYS);
              params.set("page", "0");
            });
          }}
          onSearch={searchSalesSlips}
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

        <TabSplit>
          <SalesSlipList
            currentPage={sales.salesSlipCurrentPage}
            pageSize={sales.salesSlipPageSize}
            salesSlips={sales.paginatedSalesSlips}
            selectedSalesSlipId={sales.selectedSalesSlipId}
            totalPages={sales.salesSlipTotalPages}
            totalSalesSlips={sales.salesSlipTotalElements}
            onSelect={sales.selectSalesSlip}
            onCreateSalesSlip={handleToggleCreateSalesSlip}
            onPageChange={(pageIndex) => {
              sales.setSalesSlipPage(pageIndex);
              writeUrlParams((params) => {
                params.set("page", String(pageIndex));
              });
            }}
            onPageSizeChange={(pageSize) => {
              sales.setSalesSlipPageSize(pageSize);
              writeUrlParams((params) => {
                params.set("size", String(pageSize));
                params.set("page", "0");
              });
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
        </TabSplit>
      </TabLayout>
    </main>
  );
}
