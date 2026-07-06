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

const ANALYTICS_TABS: { id: AnalyticsTab; label: string }[] = [
  { id: "SALES", label: "매출/출하" },
  { id: "VARIETY", label: "품종 분석" },
  { id: "CUSTOMER", label: "거래처 분석" },
  { id: "SPACE", label: "농장 공간" },
  { id: "WORK", label: "작업/상태" },
];

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
  const exportCsv = () => {
    const rows = [
      "전표번호,거래처,판매일,금액,입금상태",
      ...filteredProps.salesSlips.map((slip) =>
        [
          slip.slipNumber,
          slip.partner.name,
          slip.saleDate,
          slip.totalAmount,
          slip.paymentStatus,
        ].join(","),
      ),
    ];
    const blob = new Blob([`\uFEFF${rows.join("\n")}`], {
      type: "text/csv;charset=utf-8",
    });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "농장-분석.csv";
    link.click();
    URL.revokeObjectURL(link.href);
  };

  function updateTab(nextTab: AnalyticsTab) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", nextTab);
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <main className="min-w-0 space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-[#d8ded8]">
        <nav className="flex max-w-full overflow-x-auto">
          {ANALYTICS_TABS.map((item) => (
            <button
              className={`shrink-0 border-b-2 px-5 py-3 text-sm font-semibold ${
                tab === item.id
                  ? "border-[#159447] text-[#16843d]"
                  : "border-transparent text-[#667169]"
              }`}
              key={item.id}
              type="button"
              onClick={() => updateTab(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <button
          className="mb-2 flex items-center gap-2 rounded-md bg-[#159447] px-4 py-2 text-xs font-semibold text-white"
          type="button"
          onClick={exportCsv}
        >
          <Download className="h-4 w-4" />
          내보내기
          <ChevronDown className="h-3.5 w-3.5" />
        </button>
      </div>
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
