import Link from "next/link";
import type { HouseStatusSummary } from "@/entities/farm/types";
import { DashboardEmptyText, DashboardPanel } from "./DashboardPanel";

export function IssueTable({ houses }: { houses: HouseStatusSummary[] }) {
  const rows = houses.slice(0, 5);

  return (
    <DashboardPanel
      title="상태 이상 / 주의 목록"
      action={<Link className="rounded-md border border-[#dfe5dc] px-3 py-1.5 text-xs font-semibold" href="/farm-status">전체 보기</Link>}
    >
      <table className="w-full text-sm">
        <thead className="border-y border-[#edf0ec] text-left text-[#6a766e]">
          <tr>
            <th className="py-2 font-semibold">위치</th>
            <th className="py-2 font-semibold">내용</th>
            <th className="py-2 font-semibold">상태</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((house) => (
            <tr key={house.houseId} className="border-b border-[#edf0ec]">
              <td className="py-2 font-semibold text-[#246df2]">{house.houseNumber}동</td>
              <td className="py-2">확인 필요 {house.warningCount}건</td>
              <td className="py-2">
                <span className="rounded-md bg-[#fff1d6] px-2 py-1 text-xs font-bold text-[#d88400]">주의</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 ? <DashboardEmptyText text="상태 이상 또는 주의 항목이 없습니다." /> : null}
    </DashboardPanel>
  );
}
