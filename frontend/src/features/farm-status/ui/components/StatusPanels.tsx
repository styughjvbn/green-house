"use client";

import type { FarmStatusOrchidGroupItem, FarmStatusOrchidGroupList, HouseStatusSummary } from "@/entities/farm/types";
import { selectionTitle } from "../../lib/farmStatusView";

export function SelectionSummaryPanel({
  selection,
  selectedHouse,
}: {
  selection: FarmStatusOrchidGroupList | null;
  selectedHouse: HouseStatusSummary | null;
}) {
  const items = selection?.items ?? [];
  const statusLabel = selectedHouse && (selectedHouse.warningCount > 0 || selectedHouse.repotDueCount > 0) ? "주의" : "정상";

  return (
    <aside className="rounded-xl border border-[#d9e2d5] bg-white shadow-sm">
      <div className="flex items-start justify-between gap-3 border-b border-[#edf1ea] p-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-md bg-[#256ff0] px-3 py-1.5 text-sm font-semibold text-white">
              {selection?.targetName ?? selectedHouse?.houseName ?? "선택 없음"}
            </span>
            <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusLabel === "정상" ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff3da] text-[#b76600]"}`}>
              {statusLabel}
            </span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-[#17251b]">{selectionTitle(selection, selectedHouse)}</h2>
        </div>
        <a className="rounded-md border border-[#d2dcd0] bg-[#f8faf7] px-3 py-2 text-xs font-semibold text-[#34503b]" href="/orchid-groups">
          관리에서 수정
        </a>
      </div>

      <div className="grid grid-cols-4 border-b border-[#edf1ea] text-center">
        <PanelMetric label="물리 배드" value="3개" />
        <PanelMetric label="논리 구역" value="6개" />
        <PanelMetric label="난 묶음" value={`${items.length}개`} />
        <PanelMetric label="최근 작업" value={selectedHouse?.latestWorkDate ?? "없음"} compact />
      </div>

      <div className="flex gap-2 border-b border-[#edf1ea] p-3">
        <DisabledTab active label="선택 범위" />
        <DisabledTab label="배드별 보기" />
        <DisabledTab label="구역별 보기" />
      </div>

      <div className="max-h-[345px] overflow-auto p-3">
        <OrchidMiniTable items={items} />
      </div>
    </aside>
  );
}

export function OrchidGroupTable({
  selection,
  selectedHouse,
}: {
  selection: FarmStatusOrchidGroupList | null;
  selectedHouse: HouseStatusSummary | null;
}) {
  const items = selection?.items ?? [];

  return (
    <section className="rounded-xl border border-[#d9e2d5] bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-[#17251b]">{selectionTitle(selection, selectedHouse)}</h2>
          <p className="mt-1 text-xs text-[#66736a]">현재 선택한 범위에 배치된 난 묶음입니다.</p>
        </div>
        <a className="rounded-md bg-[#1f8f48] px-3 py-2 text-xs font-semibold text-white" href="/orchid-groups">
          난 묶음 관리
        </a>
      </div>
      <div className="mt-3 overflow-x-auto">
        <OrchidMiniTable items={items} />
      </div>
    </section>
  );
}

export function RecentWorkSummary() {
  const rows = [
    ["최근 농약", "기록 없음"],
    ["최근 비료", "기록 없음"],
    ["최근 분갈이", "기록 없음"],
    ["잎 정리", "기록 없음"],
    ["잡초 정리", "기록 없음"],
    ["단화 정리", "기록 없음"],
  ];

  return (
    <section className="rounded-xl border border-[#d9e2d5] bg-white p-4 shadow-sm">
      <h2 className="text-lg font-semibold text-[#17251b]">최근 작업 요약</h2>
      <div className="mt-3 grid grid-cols-2 gap-2">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-md bg-[#f7f9f5] px-3 py-2 text-sm">
            <p className="font-medium text-[#425348]">{label}</p>
            <p className="mt-1 text-xs text-[#68766d]">{value}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function OrchidMiniTable({ items }: { items: FarmStatusOrchidGroupItem[] }) {
  if (items.length === 0) {
    return <p className="rounded-md bg-[#f7f9f5] p-4 text-center text-sm text-[#6a766d]">선택한 범위에 난 묶음이 없습니다.</p>;
  }

  return (
    <table className="w-full border-separate border-spacing-y-1.5 text-left text-xs">
      <thead className="text-[#657269]">
        <tr>
          <th className="px-2 font-semibold">배드/구역</th>
          <th className="px-2 font-semibold">품종명</th>
          <th className="px-2 text-right font-semibold">수량</th>
          <th className="px-2 font-semibold">상태</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.orchidGroupId} className="bg-[#f8faf7]">
            <td className="rounded-l-md px-2 py-2 text-[#4f6255]">
              {item.physicalBedName} {item.bedZoneName}
            </td>
            <td className="px-2 py-2 font-semibold text-[#1e2d23]">{item.varietyName}</td>
            <td className="px-2 py-2 text-right text-[#1e2d23]">{item.quantity}</td>
            <td className="rounded-r-md px-2 py-2">
              <StatusBadge status={item.status} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function PanelMetric({ label, value, compact = false }: { label: string; value: string; compact?: boolean }) {
  return (
    <div className="border-r border-[#edf1ea] px-2 py-3 last:border-r-0">
      <p className="text-[11px] font-semibold text-[#7a877e]">{label}</p>
      <p className={`mt-1 font-semibold text-[#17251b] ${compact ? "text-xs" : "text-base"}`}>{value}</p>
    </div>
  );
}

function DisabledTab({ active = false, label }: { active?: boolean; label: string }) {
  return (
    <button className={`rounded-md px-3 py-2 text-xs font-semibold ${active ? "bg-[#256ff0] text-white" : "bg-[#eef4ed] text-[#48604f]"}`} disabled type="button">
      {label}
    </button>
  );
}

function StatusBadge({ status }: { status: string }) {
  const isNormal = status === "정상";
  return <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${isNormal ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff0df] text-[#c15b10]"}`}>{status}</span>;
}
