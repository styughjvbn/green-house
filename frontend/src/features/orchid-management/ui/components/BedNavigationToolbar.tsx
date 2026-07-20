"use client";

import { ChevronLeft, ChevronRight, Palette, Plus, Ruler } from "lucide-react";
import type {
  HouseStatusSummary,
  VisibleBedCount,
} from "@/entities/farm/types";
import VisibleBedCountSelector from "./VisibleBedCountSelector";

export default function BedNavigationToolbar({
  houses,
  startHouseId,
  visibleBedCount,
  hasPrevious,
  hasNext,
  distinguishVarietyColors,
  createActive,
  showScale,
  onPrevious,
  onNext,
  onGoToHouse,
  onVisibleBedCountChange,
  onToggleVarietyColors,
  onOpenCreate,
  onToggleScale,
}: {
  houses: HouseStatusSummary[];
  startHouseId: number | null;
  visibleBedCount: VisibleBedCount;
  hasPrevious: boolean;
  hasNext: boolean;
  distinguishVarietyColors: boolean;
  createActive: boolean;
  showScale: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onGoToHouse: (houseId: number) => void;
  onVisibleBedCountChange: (count: VisibleBedCount) => void;
  onToggleVarietyColors: () => void;
  onOpenCreate: () => void;
  onToggleScale: () => void;
}) {
  return (
    <section className="flex flex-wrap items-center gap-2 rounded-md border border-[#e2e7df] bg-white px-3 py-2 shadow-sm">
      <button
        aria-label="이전 다이"
        className="flex h-8 w-8 items-center justify-center rounded-md border border-[#dfe5dc] disabled:cursor-not-allowed disabled:opacity-40"
        disabled={!hasPrevious}
        type="button"
        onClick={onPrevious}
      >
        <ChevronLeft className="h-4 w-4" />
      </button>
      <select
        aria-label="동으로 빠른 이동"
        className="h-8 rounded-md border border-[#dfe5dc] bg-white px-3 text-sm font-semibold text-[#17251b]"
        value={startHouseId ?? ""}
        onChange={(event) => onGoToHouse(Number(event.target.value))}
      >
        {houses.map((house) => (
          <option key={house.houseId} value={house.houseId}>
            {house.houseNumber}동으로 이동
          </option>
        ))}
      </select>
      <button
        aria-label="다음 다이"
        className="flex h-8 w-8 items-center justify-center rounded-md border border-[#dfe5dc] disabled:cursor-not-allowed disabled:opacity-40"
        disabled={!hasNext}
        type="button"
        onClick={onNext}
      >
        <ChevronRight className="h-4 w-4" />
      </button>
      <VisibleBedCountSelector
        value={visibleBedCount}
        onChange={onVisibleBedCountChange}
      />

      <button
        className={`inline-flex h-8 items-center gap-2 rounded-md border border-[#dfe5dc] px-3 text-sm font-semibold ${
          createActive ? "bg-[#159447] text-white" : "bg-white text-[#344138]"
        }`}
        onClick={onOpenCreate}
        type="button"
      >
        <Plus className="h-4 w-4" />난 묶음 추가
      </button>
      <div className="min-w-2 flex-1" />
      <button
        aria-pressed={distinguishVarietyColors}
        className={`inline-flex h-8 items-center gap-2 rounded-md border border-[#dfe5dc] px-3 text-sm font-semibold ${
          distinguishVarietyColors
            ? "bg-[#2f7f77] text-white"
            : "bg-white text-[#344138]"
        }`}
        onClick={onToggleVarietyColors}
        type="button"
      >
        <Palette className="h-4 w-4" />
        색상 구별
      </button>
      <button
        aria-pressed={showScale}
        className={`inline-flex h-8 items-center gap-2 rounded-md border border-[#dfe5dc] px-3 text-sm font-semibold ${
          showScale ? "bg-[#159447] text-white" : "bg-white text-[#344138]"
        }`}
        onClick={onToggleScale}
        type="button"
      >
        <Ruler className="h-4 w-4" />
        눈금
      </button>
    </section>
  );
}
