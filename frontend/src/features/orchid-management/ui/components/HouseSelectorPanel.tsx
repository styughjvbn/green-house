"use client";

import { Palette, Pencil, Ruler } from "lucide-react";
import { useRouter } from "next/navigation";
import type { House, HouseStatusSummary } from "@/entities/farm/types";

export default function HouseSelectorPanel({
  house,
  houses,
  distinguishVarietyColors,
  placementEditMode,
  selectedHouseId,
  showScale,
  onTogglePlacementEditMode,
  onToggleVarietyColors,
  onToggleScale,
}: {
  house: House;
  houses: HouseStatusSummary[];
  distinguishVarietyColors: boolean;
  placementEditMode: boolean;
  selectedHouseId: number;
  showScale: boolean;
  onTogglePlacementEditMode: () => void;
  onToggleVarietyColors: () => void;
  onToggleScale: () => void;
}) {
  const router = useRouter();

  return (
    <section className="flex flex-wrap items-center gap-3 rounded-md border border-[#e2e7df] bg-white px-3 py-2 shadow-sm">
      <div className="flex h-8 overflow-hidden rounded-md border border-[#dfe5dc] bg-white">
        <span className="flex items-center border-r border-[#e6ebe3] bg-[#fbfcfa] px-3 text-xs font-semibold text-[#66746b]">
          현재 동 선택
        </span>
        <select
          aria-label="현재 동 선택"
          className="h-full min-w-28 bg-white px-3 text-sm font-semibold text-[#17251b] outline-none"
          value={selectedHouseId}
          onChange={(event) =>
            router.push(`/orchid-groups?houseId=${event.target.value}`)
          }
        >
          {houses.map((item) => (
            <option key={item.houseId} value={item.houseId}>
              {item.houseNumber}동
            </option>
          ))}
        </select>
      </div>

      <div className="min-w-3 flex-1" />
      <button
        aria-pressed={distinguishVarietyColors}
        className={`inline-flex h-8 touch-manipulation items-center gap-2 rounded-md border border-[#dfe5dc] px-4 text-sm font-semibold shadow-sm ${
          distinguishVarietyColors
            ? "bg-[#2f7f77] text-white"
            : "bg-white text-[#344138]"
        }`}
        onClick={onToggleVarietyColors}
        type="button"
      >
        <Palette className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        <span>
          난 묶음 색상 구별 {distinguishVarietyColors ? "끄기" : "켜기"}
        </span>
      </button>
      <button
        className={`inline-flex h-8 touch-manipulation items-center gap-2 rounded-md border border-[#dfe5dc] px-4 text-sm font-semibold shadow-sm ${
          showScale ? "bg-[#159447] text-white" : "bg-white text-[#344138]"
        }`}
        onClick={onToggleScale}
        type="button"
      >
        <Ruler className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        {showScale ? "눈금 끄기" : "눈금 켜기"}
      </button>
      <button
        className={`inline-flex h-8 touch-manipulation items-center gap-2 rounded-md border border-[#dfe5dc] px-4 text-sm font-semibold shadow-sm ${
          placementEditMode
            ? "bg-[#159447] text-white"
            : "bg-white text-[#344138]"
        }`}
        type="button"
        onClick={onTogglePlacementEditMode}
      >
        <Pencil className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        {placementEditMode ? "배치 수정 끄기" : "배치 수정 켜기"}
      </button>

      <div className="flex h-8 items-center gap-5 rounded-md border border-[#e2e7df] bg-white px-4 text-xs font-semibold text-[#435047] shadow-sm">
        <StatusDot color="#159447" label="정상" />
        <StatusDot color="#f59e0b" label="주의" />
        <StatusDot color="#e52d2d" label="이상" />
      </div>

      <span className="sr-only">{house.number}동 상세 보기</span>
    </section>
  );
}

function StatusDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span
        className="h-2.5 w-2.5 rounded-full"
        style={{ backgroundColor: color }}
      />
      {label}
    </span>
  );
}
