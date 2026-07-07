"use client";

import type { House, OrchidManagementViewMode } from "@/entities/farm/types";

export default function HouseDetailHeader({
  house,
  viewMode,
  onViewModeChange,
}: {
  house: House;
  viewMode: OrchidManagementViewMode;
  onViewModeChange: (viewMode: OrchidManagementViewMode) => void;
}) {
  const viewModes: Array<{ value: OrchidManagementViewMode; label: string }> = [
    { value: "REAL_DIRECTION", label: "실제 방향 보기" },
    { value: "ROTATED", label: "회전 보기" },
    { value: "BY_BED", label: "다이별 보기" },
  ];

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">선택한 동</p>
          <h1 className="mt-1 text-2xl font-semibold">
            {house.number}동 상세 보기
          </h1>
        </div>
        <div className="flex flex-wrap gap-2">
          {viewModes.map((mode) => (
            <button
              key={mode.value}
              className={`rounded-md px-3 py-1.5 text-sm font-semibold ${
                viewMode === mode.value
                  ? "bg-[#159447] text-white"
                  : "border border-[#d7ddd4] bg-white text-[#435047]"
              }`}
              onClick={() => onViewModeChange(mode.value)}
              type="button"
            >
              {mode.label}
            </button>
          ))}
        </div>
      </div>
      <div className="mt-3 flex flex-wrap gap-2 rounded-md border border-[#b9d0ff] bg-[#f3f7ff] px-3 py-2 text-sm font-semibold text-[#246df2]">
        <span>보기 모드에서 난 묶음 추가, 수정, 삭제가 가능합니다.</span>
        <span className="text-[#b4c2dc]">|</span>
        <span>배치 수정 시작 후 같은 동 안에서 드래그 이동할 수 있습니다.</span>
      </div>
    </section>
  );
}
