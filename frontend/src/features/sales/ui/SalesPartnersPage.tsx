"use client";

import type { SubmitEvent } from "react";
import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import { readBusinessPartnerRouteState } from "../lib/salesRouteParams";
import { useBusinessPartners } from "../model/useBusinessPartners";
import { BusinessPartnerCreateForm } from "./partners/BusinessPartnerCreateForm";
import {
  BusinessPartnerEditSection,
  type BusinessPartnerDetailMode,
} from "./partners/BusinessPartnerEditSection";
import { BusinessPartnerFilters } from "./partners/BusinessPartnerFilters";
import { BusinessPartnerList } from "./partners/BusinessPartnerList";
import { PartnerSettlementSettingsSection } from "./partners/PartnerSettlementSettingsSection";

export function SalesPartnersPage() {
  const routeState = readBusinessPartnerRouteState(useSearchParams());
  const partners = useBusinessPartners({ routeState });
  const [showCreatePartner, setShowCreatePartner] = useState(false);
  const [partnerDetailMode, setPartnerDetailMode] =
    useState<BusinessPartnerDetailMode>("read");

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
          onChange={partners.updateFilter}
          onReset={partners.resetFilters}
          onSearch={partners.search}
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
            onPageChange={partners.setPage}
            onPageSizeChange={partners.setPageSize}
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
