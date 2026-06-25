"use client";

import Link from "next/link";
import type { HouseStatusSummary } from "@/entities/farm/types";

export default function HouseSelectorPanel({ houses, selectedHouseId }: { houses: HouseStatusSummary[]; selectedHouseId: number }) {
  return (
    <aside className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">전체 동 보기</p>
          <h2 className="mt-1 text-lg font-semibold">동 선택</h2>
        </div>
        <span className="rounded-full border border-[#d7ddd4] px-2 py-0.5 text-xs text-[#5c6a60]">15</span>
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2 2xl:grid-cols-2">
        {houses.map((house) => {
          const selected = house.houseId === selectedHouseId;
          const warning = house.warningCount > 0;
          return (
            <Link
              key={house.houseId}
              href={`/orchid-groups?houseId=${house.houseId}`}
              className={`min-h-20 rounded-md border p-2 text-center shadow-sm transition hover:border-[#159447] ${
                selected ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]" : "border-[#d7ddd4] bg-white"
              }`}
            >
              <p className="text-base font-semibold">{house.houseNumber}동</p>
              <div className="mx-auto mt-2 h-8 w-12 rounded-t-xl border border-[#b9c7b9] bg-[linear-gradient(90deg,#f5f7f4_0,#f5f7f4_18%,#dfe7de_19%,#f5f7f4_20%,#f5f7f4_48%,#dfe7de_49%,#f5f7f4_50%,#f5f7f4_78%,#dfe7de_79%,#f5f7f4_80%)]" />
              <p className={`mt-1 text-xs font-semibold ${warning ? "text-[#f59e0b]" : "text-[#159447]"}`}>{warning ? "주의" : "정상"}</p>
            </Link>
          );
        })}
      </div>
    </aside>
  );
}

