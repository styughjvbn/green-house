"use client";

import { useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { createAnalyticsViewModel } from "../lib/analyticsView";
import type {
  AnalyticsFilters,
  AnalyticsPageProps,
  AnalyticsTab,
} from "../model/types";
import { AnalyticsFilters as FilterBar } from "./components/AnalyticsFilters";
import { AnalyticsSummary } from "./components/AnalyticsSummary";
import { AnalyticsTabContent } from "./components/AnalyticsTabContent";

const ALL_LABEL = "전체";

const DEFAULT_FILTERS: AnalyticsFilters = {
  dateFrom: "2026-01-01",
  dateTo: "2026-12-31",
  house: ALL_LABEL,
  bed: ALL_LABEL,
  zone: ALL_LABEL,
  variety: ALL_LABEL,
  partner: ALL_LABEL,
};

export function AnalyticsPage(props: AnalyticsPageProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tab =
    props.activeTab ??
    (searchParams.get("tab") as AnalyticsTab | null) ??
    "SALES";
  const [draftFilters, setDraftFilters] = useState(DEFAULT_FILTERS);
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const view = useMemo(() => createAnalyticsViewModel(props), [props]);
  const varieties = useMemo(
    () => view.varietySales.map((item) => item.label),
    [view.varietySales],
  );
  const partners = useMemo(
    () => view.partnerSales.map((item) => item.label),
    [view.partnerSales],
  );
  const filteredProps = useMemo(
    () => ({
      ...props,
      workRecords: props.workRecords.filter(
        (record) =>
          record.workDate >= filters.dateFrom &&
          record.workDate <= filters.dateTo,
      ),
    }),
    [filters, props],
  );

  const reset = () => {
    setDraftFilters(DEFAULT_FILTERS);
    setFilters(DEFAULT_FILTERS);
  };

  function updateTab(nextTab: AnalyticsTab) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", nextTab);
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <main className="min-w-0 space-y-4">
      <FilterBar
        values={draftFilters}
        varieties={varieties}
        partners={partners}
        onChange={(key, value) =>
          setDraftFilters((current) => ({ ...current, [key]: value }))
        }
        onApply={() => setFilters(draftFilters)}
        onReset={reset}
      />
      <AnalyticsSummary
        sales={view.currentMonthSales}
        shipped={view.shippedQuantity}
        unpaid={view.unpaidAmount}
        saleable={view.saleableQuantity}
        warning={props.summary.warningCount}
        repotDue={props.summary.repotDueCount}
        onSelectTab={updateTab}
      />
      <AnalyticsTabContent tab={tab} props={filteredProps} view={view} />
    </main>
  );
}
