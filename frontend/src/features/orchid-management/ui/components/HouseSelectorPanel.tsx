"use client";

import { Pencil } from "lucide-react";
import { useRouter } from "next/navigation";
import type {
  House,
  HouseStatusSummary,
} from "@/entities/farm/types";

export default function HouseSelectorPanel({
  house,
  houses,
  placementEditMode,
  selectedHouseId,
  onTogglePlacementEditMode,
}: {
  house: House;
  houses: HouseStatusSummary[];
  placementEditMode: boolean;
  selectedHouseId: number;
  onTogglePlacementEditMode: () => void;
}) {
  const router = useRouter();

  return (
    <section className="flex flex-wrap items-center gap-3 rounded-md border border-[#e2e7df] bg-white px-3 py-2 shadow-sm">
      <div className="flex h-9 overflow-hidden rounded-md border border-[#dfe5dc] bg-white">
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
        className={`inline-flex h-9 touch-manipulation items-center gap-2 rounded-md px-4 text-sm font-semibold shadow-sm border border-[#dfe5dc] ${
          placementEditMode
            ? "bg-[#159447] text-white"
            : "bg-white text-[#344138]"
        }`}
        type="button"
        onClick={onTogglePlacementEditMode}
      >
        <Pencil className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        배치 수정 모드
      </button>

      <div className="flex h-9 items-center gap-5 rounded-md border border-[#e2e7df] bg-white px-4 text-xs font-semibold text-[#435047] shadow-sm">
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
