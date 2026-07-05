"use client";

import { Download, Plus } from "lucide-react";
import type { SalesTab } from "../../model/types";

export function SalesToolbar({
  activeTab,
  onCreateSlip,
  onTabChange,
}: {
  activeTab: SalesTab;
  onCreateSlip: () => void;
  onTabChange: (tab: SalesTab) => void;
}) {
  return (
    <section className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex h-10 max-w-full items-end gap-5 overflow-x-auto border-b border-[#dfe5dc]">
        <TabButton
          active={activeTab === "SLIPS"}
          label="판매 전표"
          onClick={() => onTabChange("SLIPS")}
        />
        <TabButton
          active={activeTab === "AUCTION"}
          label="출하·경매 추적"
          onClick={() => onTabChange("AUCTION")}
        />
        <TabButton
          active={activeTab === "SETTLEMENT"}
          label="경매장 정산"
          onClick={() => onTabChange("SETTLEMENT")}
        />
        <TabButton
          active={activeTab === "CUSTOMERS"}
          label="거래처 관리"
          onClick={() => onTabChange("CUSTOMERS")}
        />
      </div>

      {activeTab === "SLIPS" ? (
        <div className="flex gap-3">
          <button
            className="inline-flex h-10 items-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-4 text-sm font-semibold text-[#344138] shadow-sm"
            type="button"
          >
            <Download
              className="h-4 w-4"
              strokeWidth={1.8}
              aria-hidden="true"
            />
            엑셀 다운로드
          </button>
          <button
            className="inline-flex h-10 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white shadow-sm"
            type="button"
            onClick={onCreateSlip}
          >
            <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />새
            판매 전표 등록
          </button>
        </div>
      ) : null}
    </section>
  );
}

function TabButton({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={`h-10 shrink-0 border-b-2 px-1 text-sm font-semibold whitespace-nowrap ${
        active
          ? "border-[#159447] text-[#159447]"
          : "border-transparent text-[#5c6a60]"
      }`}
      type="button"
      onClick={onClick}
    >
      {label}
    </button>
  );
}
