"use client";

import type { HouseStatusSummary } from "@/entities/farm/types";
import type { SelectedTarget } from "../../model/types";
import { hasHouseWarning } from "../../lib/farmStatusView";

type FarmOverviewLayerProps = {
  houses: HouseStatusSummary[];
  selectedTarget: SelectedTarget | null;
  onSelectHouse: (house: HouseStatusSummary) => void;
};

export default function FarmOverviewLayer({ houses, selectedTarget, onSelectHouse }: FarmOverviewLayerProps) {
  return (
    <div className="grid min-h-[370px] grid-cols-5 items-end gap-2 lg:grid-cols-[repeat(15,minmax(0,1fr))]">
      {houses.map((house) => (
        <HouseSummaryBlock
          key={house.houseId}
          house={house}
          selected={selectedTarget?.type === "HOUSE" && selectedTarget.id === house.houseId}
          onSelect={() => onSelectHouse(house)}
        />
      ))}
    </div>
  );
}

function HouseSummaryBlock({
  house,
  selected,
  onSelect,
}: {
  house: HouseStatusSummary;
  selected: boolean;
  onSelect: () => void;
}) {
  const hasWarning = hasHouseWarning(house);

  return (
    <button
      className={`group relative flex h-40 min-w-0 flex-col items-center rounded-b-lg rounded-t-md border bg-[#f7f8f5] px-1 pb-2 pt-6 text-left shadow-[0_7px_10px_rgba(37,72,39,0.26)] transition hover:translate-y-[-2px] lg:h-56 ${
        selected ? "border-[#1d6ff2] bg-[#dcecff] ring-3 ring-[#2f80ff]" : "border-[#cfd8cc]"
      }`}
      onClick={onSelect}
      type="button"
    >
      <div
        className={`absolute -top-3 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold text-white shadow ${
          selected ? "bg-[#246df2]" : "bg-[#155c30]"
        }`}
      >
        {house.houseNumber}동
        <span className={`h-2 w-2 rounded-full ${hasWarning ? "bg-[#ffcc33]" : "bg-[#56d071]"}`} />
      </div>
      <div className="h-full w-full rounded-b-md rounded-t-sm border border-[#d9ded8] bg-[repeating-linear-gradient(90deg,#f4f5f2_0,#f4f5f2_8px,#e6e9e4_9px,#e6e9e4_10px),repeating-linear-gradient(0deg,rgba(180,190,180,0.28)_0,rgba(180,190,180,0.28)_15px,rgba(255,255,255,0)_16px,rgba(255,255,255,0)_32px)] opacity-95" />
      <span className={`absolute bottom-3 h-3.5 w-3.5 rounded-full ${hasWarning ? "bg-[#f59e0b]" : "bg-[#20a64d]"}`} />
      <span className="mt-1 text-[11px] font-semibold text-[#314037]">{house.orchidGroupCount}묶음</span>
    </button>
  );
}

