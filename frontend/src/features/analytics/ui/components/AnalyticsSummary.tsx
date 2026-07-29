"use client";

import {
  AlertTriangle,
  Banknote,
  PackageOpen,
  Sprout,
  WalletCards,
  type LucideIcon,
} from "lucide-react";
import type { AnalyticsTab } from "../../model/types";

type Card = {
  label: string;
  value: string;
  note: string;
  tone: string;
  icon: LucideIcon;
  tab: AnalyticsTab;
};

export function AnalyticsSummary({
  sales,
  previousSales,
  shipped,
  previousShipped,
  unpaid,
  saleable,
  warning,
  repotDue,
  onSelectTab,
}: {
  sales: number;
  previousSales: number;
  shipped: number;
  previousShipped: number;
  unpaid: number;
  saleable: number;
  warning: number;
  repotDue: number;
  onSelectTab: (tab: AnalyticsTab) => void;
}) {
  const cards: Card[] = [
    {
      label: "조회 종료월 매출",
      value: formatWon(sales),
      note: comparisonNote(sales, previousSales),
      tone: "green",
      icon: Banknote,
      tab: "sales",
    },
    {
      label: "조회 종료월 출하 수량",
      value: `${shipped.toLocaleString()}분`,
      note: comparisonNote(shipped, previousShipped),
      tone: "blue",
      icon: PackageOpen,
      tab: "sales",
    },
    {
      label: "미입금 금액",
      value: formatWon(unpaid),
      note: "미입금 전표 확인",
      tone: "orange",
      icon: WalletCards,
      tab: "customer",
    },
    {
      label: "판매 가능 수량",
      value: `${saleable.toLocaleString()}분`,
      note: "정상 기준",
      tone: "purple",
      icon: Sprout,
      tab: "variety",
    },
    {
      label: "상태 이상 수",
      value: `${warning.toLocaleString()}건`,
      note: "현재 농장 기준",
      tone: "red",
      icon: AlertTriangle,
      tab: "work",
    },
    {
      label: "분갈이 예정 수",
      value: `${repotDue.toLocaleString()}분`,
      note: "30일 이내",
      tone: "mint",
      icon: Sprout,
      tab: "work",
    },
  ];

  return (
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
      {cards.map((card) => {
        const Icon = card.icon;
        return (
          <button
            className={`${toneClass(card.tone)} min-h-28 rounded-md border p-4 text-left shadow-sm transition hover:-translate-y-0.5`}
            key={card.label}
            type="button"
            onClick={() => onSelectTab(card.tab)}
          >
            <div className="flex items-center gap-3">
              <Icon className="h-6 w-6 shrink-0" strokeWidth={1.8} />
              <div>
                <p className="text-xs font-semibold text-[#4f5a53]">
                  {card.label}
                </p>
                <strong className="mt-1 block text-xl text-[#17231b]">
                  {card.value}
                </strong>
              </div>
            </div>
            <p className="mt-4 text-[11px] text-[#68756d]">{card.note}</p>
          </button>
        );
      })}
    </section>
  );
}

function comparisonNote(current: number, previous: number) {
  if (previous === 0) {
    return current === 0 ? "전월과 동일" : "전월 비교 기준 없음";
  }
  const rate = ((current - previous) / previous) * 100;
  if (rate === 0) return "전월과 동일";
  return `전월 대비 ${rate > 0 ? "▲" : "▼"} ${Math.abs(rate).toFixed(1)}%`;
}

export function formatWon(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) {
    return "-";
  }
  return `${value.toLocaleString()}원`;
}

function toneClass(tone: string) {
  return (
    {
      green: "border-[#d7e8da] bg-[#f8fcf8] text-[#159447]",
      blue: "border-[#d7e5ee] bg-[#f7fbfd] text-[#1680bd]",
      orange: "border-[#ebdfcb] bg-[#fdfaf5] text-[#b87513]",
      purple: "border-[#e5daeb] bg-[#fbf8fc] text-[#7c4ba2]",
      red: "border-[#efdadd] bg-[#fff9f9] text-[#d63c50]",
      mint: "border-[#d9e7e3] bg-[#f8fcfa] text-[#218c58]",
    }[tone] ?? "border-[#dce2dc] bg-white text-[#159447]"
  );
}
