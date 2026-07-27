"use client";

import type { FormEvent } from "react";
import { useState } from "react";
import type { BusinessPartnerPage } from "@/entities/farm/types";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import {
  BUSINESS_PARTNER_FILTER_KEYS,
  deleteParams,
  writeBusinessPartnerFilterParams,
} from "../lib/salesUrlFilters";
import { useSalesManager } from "../model/useSalesManager";
import type { BusinessPartnerFilterState } from "../model/types";
import { BusinessPartnerCreateForm } from "./partners/BusinessPartnerCreateForm";
import {
  BusinessPartnerEditSection,
  type BusinessPartnerDetailMode,
} from "./partners/BusinessPartnerEditSection";
import { BusinessPartnerFilters } from "./partners/BusinessPartnerFilters";
import { BusinessPartnerList } from "./partners/BusinessPartnerList";
import { PartnerSettlementSettingsSection } from "./partners/PartnerSettlementSettingsSection";

export function SalesPartnersPage({
  initialFilters,
  initialPage,
}: {
  initialPage: BusinessPartnerPage;
  initialFilters: BusinessPartnerFilterState;
}) {
  const writeUrlParams = useUrlSearchParamsWriter();
  const sales = useSalesManager({
    initialBusinessPartnerPage: initialPage,
    initialPartnerFilters: initialFilters,
  });
  const [showCreatePartner, setShowCreatePartner] = useState(false);
  const [partnerDetailMode, setPartnerDetailMode] =
    useState<BusinessPartnerDetailMode>("read");

  const updateFilter = <K extends keyof BusinessPartnerFilterState>(
    field: K,
    value: BusinessPartnerFilterState[K],
  ) => {
    const nextFilters = { ...sales.partnerFilters, [field]: value };
    sales.updatePartnerFilters(field, value);
    writeUrlParams((params) => {
      writeBusinessPartnerFilterParams(params, nextFilters);
      params.set("page", "0");
    });
  };

  async function handleCreateBusinessPartner(
    event: FormEvent<HTMLFormElement>,
  ) {
    const created = await sales.handleCreateBusinessPartner(event);
    if (created) {
      setShowCreatePartner(false);
    }
  }

  async function handleUpdateBusinessPartner(
    event: FormEvent<HTMLFormElement>,
  ) {
    return await sales.handleUpdateBusinessPartner(event);
  }

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <BusinessPartnerFilters
          filters={sales.partnerFilters}
          onChange={updateFilter}
          onReset={() => {
            sales.resetPartnerFilters();
            writeUrlParams((params) => {
              deleteParams(params, BUSINESS_PARTNER_FILTER_KEYS);
              params.set("page", "0");
            });
          }}
        />
        <TabSplit columns="lg:grid-cols-[520px_minmax(0,1fr)]">
          <BusinessPartnerList
            currentPage={sales.partnerCurrentPage}
            pageSize={sales.partnerPageSize}
            partners={sales.paginatedBusinessPartners}
            selectedBusinessPartnerId={sales.selectedPartnerId}
            totalPages={sales.partnerTotalPages}
            totalPartners={sales.filteredBusinessPartners.length}
            onSelectBusinessPartner={(partnerId) => {
              setShowCreatePartner(false);
              setPartnerDetailMode("read");
              sales.selectBusinessPartner(partnerId);
            }}
            onCreateBusinessPartner={() => setShowCreatePartner(true)}
            onPageChange={(pageIndex) => {
              sales.setPartnerPage(pageIndex);
              writeUrlParams((params) => {
                params.set("page", String(pageIndex));
              });
            }}
            onPageSizeChange={(pageSize) => {
              sales.setPartnerPageSize(pageSize);
              writeUrlParams((params) => {
                params.set("size", String(pageSize));
                params.set("page", "0");
              });
            }}
          />
          <div>
            <BusinessPartnerEditSection
              key={sales.selectedBusinessPartner?.id ?? "empty"}
              partner={sales.selectedBusinessPartner}
              form={sales.partnerEditForm}
              saving={sales.savingBusinessPartnerEdit}
              mode={partnerDetailMode}
              errorMessage={sales.errorMessage}
              onChange={sales.updateBusinessPartnerEditForm}
              onModeChange={setPartnerDetailMode}
              onSubmit={handleUpdateBusinessPartner}
            >
              <PartnerSettlementSettingsSection
                key={sales.selectedBusinessPartner?.id ?? "empty"}
                partner={sales.selectedBusinessPartner}
                embedded
                mode={partnerDetailMode === "settlement" ? "edit" : "read"}
                onSaved={() => setPartnerDetailMode("read")}
              />
            </BusinessPartnerEditSection>
          </div>
        </TabSplit>
        {showCreatePartner ? (
          <BusinessPartnerCreateForm
            form={sales.partnerForm}
            saving={sales.savingBusinessPartner}
            onChange={sales.updateBusinessPartnerForm}
            onClose={() => setShowCreatePartner(false)}
            onSubmit={handleCreateBusinessPartner}
          />
        ) : null}
      </TabLayout>
    </main>
  );
}
