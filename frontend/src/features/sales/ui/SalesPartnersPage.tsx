"use client";

import type { SubmitEvent } from "react";
import { useState } from "react";
import type { BusinessPartnerPage } from "@/entities/farm/types";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import {
  BUSINESS_PARTNER_FILTER_KEYS,
  deleteParams,
  writeBusinessPartnerFilterParams,
} from "../lib/salesUrlFilters";
import { useBusinessPartners } from "../model/useBusinessPartners";
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
  const partners = useBusinessPartners({
    initialPage,
    initialFilters,
  });
  const [showCreatePartner, setShowCreatePartner] = useState(false);
  const [partnerDetailMode, setPartnerDetailMode] =
    useState<BusinessPartnerDetailMode>("read");

  const updateFilter = <K extends keyof BusinessPartnerFilterState>(
    field: K,
    value: BusinessPartnerFilterState[K],
  ) => {
    partners.updateFilter(field, value);
  };

  const searchBusinessPartners = () => {
    partners.search(partners.filters);
    writeUrlParams((params) => {
      writeBusinessPartnerFilterParams(params, partners.filters);
      params.set("page", "0");
    });
  };

  async function handleCreateBusinessPartner(
    event: SubmitEvent<HTMLFormElement>,
  ) {
    const created = await partners.handleCreate(event);
    if (created) {
      setShowCreatePartner(false);
    }
  }

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <BusinessPartnerFilters
          filters={partners.filters}
          onChange={updateFilter}
          onReset={() => {
            partners.resetFilters();
            writeUrlParams((params) => {
              deleteParams(params, BUSINESS_PARTNER_FILTER_KEYS);
              params.set("page", "0");
            });
          }}
          onSearch={searchBusinessPartners}
        />
        <TabError message={partners.errorMessage} />
        <TabSplit columns="lg:grid-cols-[520px_minmax(0,1fr)]">
          <BusinessPartnerList
            currentPage={partners.currentPage}
            loading={partners.loading}
            pageSize={partners.pageSize}
            partners={partners.partners}
            selectedBusinessPartnerId={partners.selectedPartnerId}
            totalPages={partners.totalPages}
            totalPartners={partners.totalElements}
            onSelectBusinessPartner={(partnerId) => {
              setShowCreatePartner(false);
              setPartnerDetailMode("read");
              partners.selectPartner(partnerId);
            }}
            onCreateBusinessPartner={() => setShowCreatePartner(true)}
            onPageChange={(pageIndex) => {
              partners.setPage(pageIndex);
              writeUrlParams((params) => {
                params.set("page", String(pageIndex));
              });
            }}
            onPageSizeChange={(pageSize) => {
              partners.setPageSize(pageSize);
              writeUrlParams((params) => {
                params.set("size", String(pageSize));
                params.set("page", "0");
              });
            }}
          />
          <div>
            <BusinessPartnerEditSection
              key={partners.selectedPartner?.id ?? "empty"}
              partner={partners.selectedPartner}
              form={partners.editForm}
              saving={partners.savingEdit}
              mode={partnerDetailMode}
              errorMessage={partners.errorMessage}
              onChange={partners.updateEditForm}
              onModeChange={setPartnerDetailMode}
              onSubmit={partners.handleUpdate}
            >
              <PartnerSettlementSettingsSection
                key={partners.selectedPartner?.id ?? "empty"}
                partner={partners.selectedPartner}
                embedded
                mode={partnerDetailMode === "settlement" ? "edit" : "read"}
                onSaved={() => setPartnerDetailMode("read")}
              />
            </BusinessPartnerEditSection>
          </div>
        </TabSplit>
        {showCreatePartner ? (
          <BusinessPartnerCreateForm
            form={partners.createForm}
            saving={partners.savingCreate}
            onChange={partners.updateCreateForm}
            onClose={() => setShowCreatePartner(false)}
            onSubmit={handleCreateBusinessPartner}
          />
        ) : null}
      </TabLayout>
    </main>
  );
}
