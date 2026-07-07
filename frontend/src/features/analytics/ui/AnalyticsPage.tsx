"use client";

import { ChevronDown, Download } from "lucide-react";
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

const DEFAULT_FILTERS: AnalyticsFilters = {
  dateFrom: "2026-01-01",
  dateTo: "2026-12-31",
  house: "전체",
  bed: "전체",
  zone: "전체",
  variety: "전체",
  partner: "전체",
};

export function AnalyticsPage(props: AnalyticsPageProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tab = (searchParams.get("tab") as AnalyticsTab | null) ?? "SALES";
  const [draftFilters, setDraftFilters] = useState(DEFAULT_FILTERS);
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const varieties = useMemo(
    () => [
      ...new Set(
        props.salesSlips.flatMap((slip) =>
          slip.items.map((item) => item.itemName),
        ),
      ),
    ],
    [props.salesSlips],
  );
  const partners = useMemo(
    () => [...new Set(props.salesSlips.map((slip) => slip.partner.name))],
    [props.salesSlips],
  );
  const filteredProps = useMemo(
    () => ({
      ...props,
      salesSlips: props.salesSlips.filter(
        (slip) =>
          slip.saleDate >= filters.dateFrom &&
          slip.saleDate <= filters.dateTo &&
          (filters.partner === "전체" ||
            slip.partner.name === filters.partner) &&
          (filters.variety === "전체" ||
            slip.items.some((item) => item.itemName === filters.variety)),
      ),
      workRecords: props.workRecords.filter(
        (record) =>
          record.workDate >= filters.dateFrom &&
          record.workDate <= filters.dateTo,
      ),
    }),
    [filters, props],
  );
  const view = useMemo(
    () => createAnalyticsViewModel(filteredProps),
    [filteredProps],
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
