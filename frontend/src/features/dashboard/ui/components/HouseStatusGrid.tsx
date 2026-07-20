import Link from "next/link";
import type { HouseStatusSummary } from "@/entities/farm/types";
import {
  dotClass,
  getHouseTone,
  houseToneClass,
} from "../../lib/dashboardView";
import { DashboardLegend, DashboardPanel } from "./DashboardPanel";

export function HouseStatusGrid({ houses }: { houses: HouseStatusSummary[] }) {
  return (
    <DashboardPanel
      title="15동 상태 한눈에 보기"
      action={
        <div className="flex gap-4 text-xs font-semibold">
          <DashboardLegend tone="green" label="정상" />
          <DashboardLegend tone="orange" label="주의" />
          <DashboardLegend tone="red" label="이상" />
        </div>
      }
    >
      <div className="grid grid-cols-5 gap-3">
        {houses.map((house) => {
          const tone = getHouseTone(house.warningCount);

          return (
            <Link
              key={house.houseId}
              className={`relative flex h-16 items-center justify-center rounded-t-2xl border text-lg font-bold ${houseToneClass(tone)}`}
              href={`/orchid-groups?startBedId=${house.physicalBeds[0]?.id ?? ""}&bedCount=3`}
            >
              <span
                className={`absolute top-2 h-2 w-2 rounded-full ${dotClass(tone)}`}
              />
              {house.houseNumber}동
            </Link>
          );
        })}
      </div>
      <Link
        className="mt-4 flex h-10 items-center justify-center rounded-md border border-[#dfe5dc] text-sm font-semibold text-[#344138]"
        href="/farm-status"
      >
        농장 현황 전체 보기
      </Link>
    </DashboardPanel>
  );
}
