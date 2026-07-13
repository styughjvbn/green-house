"use client";

import { Info, Palette, Plus, Ruler } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FocusEvent, useEffect, useRef, useState } from "react";
import type { House, HouseStatusSummary } from "@/entities/farm/types";

export default function HouseSelectorPanel({
  house,
  houses,
  distinguishVarietyColors,
  createActive,
  selected,
  selectedHouseId,
  showScale,
  onToggleVarietyColors,
  onOpenCreate,
  onToggleScale,
  onSelectHouse,
}: {
  house: House;
  houses: HouseStatusSummary[];
  distinguishVarietyColors: boolean;
  createActive: boolean;
  selected: boolean;
  selectedHouseId: number;
  showScale: boolean;
  onToggleVarietyColors: () => void;
  onOpenCreate: () => void;
  onToggleScale: () => void;
  onSelectHouse: () => void;
}) {
  const router = useRouter();
  const [showLegend, setShowLegend] = useState(false);
  const legendTimerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (legendTimerRef.current != null) {
        window.clearTimeout(legendTimerRef.current);
      }
    };
  }, []);

  function showStatusLegend() {
    setShowLegend(true);
    if (legendTimerRef.current != null) {
      window.clearTimeout(legendTimerRef.current);
    }
    legendTimerRef.current = window.setTimeout(() => {
      setShowLegend(false);
      legendTimerRef.current = null;
    }, 1800);
  }

  function hideStatusLegend() {
    if (legendTimerRef.current != null) {
      window.clearTimeout(legendTimerRef.current);
      legendTimerRef.current = null;
    }
    setShowLegend(false);
  }

  function handleLegendBlur(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      hideStatusLegend();
    }
  }

  return (
    <section className="relative flex flex-nowrap items-center gap-2 overflow-visible rounded-md border border-[#e2e7df] bg-white px-3 py-2 shadow-sm">
      <div className="flex h-8 shrink-0 overflow-hidden rounded-md border border-[#dfe5dc] bg-white">
        <button
          className={`flex items-center border-r border-[#e6ebe3] px-3 text-xs font-semibold whitespace-nowrap hover:bg-[#f0f5ee] ${
            selected ? "bg-[#246df2] text-white" : "bg-[#fbfcfa] text-[#66746b]"
          }`}
          type="button"
          onClick={onSelectHouse}
        >
          현재 동 선택
        </button>
        <select
          aria-label="현재 동 선택"
          className="h-full min-w-14 bg-white px-3 text-sm font-semibold text-[#17251b] outline-none"
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

      <button
        className={`inline-flex h-8 shrink-0 touch-manipulation items-center gap-2 rounded-md border border-[#dfe5dc] px-3 text-sm font-semibold whitespace-nowrap shadow-sm ${
          createActive ? "bg-[#159447] text-white" : "bg-white text-[#344138]"
        }`}
        onClick={onOpenCreate}
        type="button"
      >
        <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />난 묶음
        추가
      </button>

      <div className="min-w-3 flex-1" />
      <button
        aria-pressed={distinguishVarietyColors}
        className={`inline-flex h-8 shrink-0 touch-manipulation items-center gap-2 rounded-md border border-[#dfe5dc] px-3 text-sm font-semibold whitespace-nowrap shadow-sm ${
          distinguishVarietyColors
            ? "bg-[#2f7f77] text-white"
            : "bg-white text-[#344138]"
        }`}
        onClick={onToggleVarietyColors}
        type="button"
      >
        <Palette className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        <span>
          색상 구별{" "}
          <span className="hidden xl:inline">
            {distinguishVarietyColors ? "끄기" : "켜기"}
          </span>
        </span>
      </button>
      <button
        className={`inline-flex h-8 shrink-0 touch-manipulation items-center gap-2 rounded-md border border-[#dfe5dc] px-3 text-sm font-semibold whitespace-nowrap shadow-sm ${
          showScale ? "bg-[#159447] text-white" : "bg-white text-[#344138]"
        }`}
        onClick={onToggleScale}
        type="button"
      >
        <Ruler className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        <span>
          눈금{" "}
          <span className="hidden xl:inline">
            {showScale ? "끄기" : "켜기"}
          </span>
        </span>
      </button>
      <div className="relative shrink-0" onBlur={handleLegendBlur}>
        <button
          aria-label="상태 범례 보기"
          aria-expanded={showLegend}
          className="flex h-8 w-8 items-center justify-center rounded-md border border-[#e2e7df] bg-white text-[#435047] shadow-sm hover:bg-[#f5f7f4]"
          type="button"
          onClick={showStatusLegend}
          onFocus={showStatusLegend}
        >
          <Info className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        </button>
        {showLegend ? (
          <div className="absolute top-10 right-0 z-30 flex h-8 items-center gap-2 rounded-md border border-[#e2e7df] bg-white px-3 text-xs font-semibold whitespace-nowrap text-[#435047] shadow-lg">
            <StatusDot color="#159447" label="정상" />
            <StatusDot color="#f59e0b" label="주의" />
            <StatusDot color="#e52d2d" label="이상" />
          </div>
        ) : null}
      </div>

      <span className="sr-only">{house.number}동 상세 보기</span>
    </section>
  );
}

function StatusDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span
        className="h-2.5 w-2.5 rounded-full"
        style={{ backgroundColor: color }}
      />
      {label}
    </span>
  );
}
