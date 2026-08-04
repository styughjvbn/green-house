"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { ANALYTICS_ROUTE } from "@/shared/config/routes";
import { defaultAnalyticsDateRange } from "../lib/analyticsDateRange";
import { createAnalyticsViewModel } from "../lib/analyticsView";
import type { AnalyticsPageProps, AnalyticsTab } from "../model/types";
import { AnalyticsFilters as FilterBar } from "./components/AnalyticsFilters";
import { AnalyticsSummary } from "./components/AnalyticsSummary";
import { AnalyticsTabContent } from "./components/AnalyticsTabContent";

export function AnalyticsPage(props: AnalyticsPageProps) {
  const router = useRouter();
  const tab = props.activeTab ?? "sales";
  const [draftFilters, setDraftFilters] = useState(props.dateRange);
  const [isPending, startTransition] = useTransition();
  const view = useMemo(() => createAnalyticsViewModel(props), [props]);
  const reset = () => {
    const defaults = defaultAnalyticsDateRange();
    setDraftFilters(defaults);
    startTransition(() => {
      router.push(
        `${ANALYTICS_ROUTE.tab(tab)}?from=${defaults.dateFrom}&to=${defaults.dateTo}`,
      );
    });
  };

  function updateTab(nextTab: AnalyticsTab) {
    router.push(
      `${ANALYTICS_ROUTE.tab(nextTab)}?from=${props.dateRange.dateFrom}&to=${props.dateRange.dateTo}`,
    );
  }

  function applyFilters() {
    startTransition(() => {
      router.push(
        `${ANALYTICS_ROUTE.tab(tab)}?from=${draftFilters.dateFrom}&to=${draftFilters.dateTo}`,
      );
    });
  }

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col gap-3 overflow-hidden">
      <FilterBar
        values={draftFilters}
        onChange={(key, value) =>
          setDraftFilters((current) => ({ ...current, [key]: value }))
        }
        onApply={applyFilters}
        onReset={reset}
        pending={isPending}
      />
      <AnalyticsSummary
        sales={view.currentMonthSales}
        previousSales={view.previousMonthSales}
        shipped={view.shippedQuantity}
        previousShipped={view.previousMonthShippedQuantity}
        unpaid={view.unpaidAmount}
        saleable={view.saleableQuantity}
        warning={props.summary.warningCount}
        repotDue={props.summary.repotDueCount}
        onSelectTab={updateTab}
      />
      <div className="min-h-0 flex-1 overflow-y-auto pb-1">
        <AnalyticsTabContent tab={tab} props={props} view={view} />
      </div>
    </main>
  );
}
