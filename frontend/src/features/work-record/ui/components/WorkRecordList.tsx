"use client";

import type { WorkRecord } from "@/types/farm";
import { formatTarget } from "../../lib/workRecordForm";

type WorkRecordListProps = {
  records: WorkRecord[];
};

export function WorkRecordList({ records }: WorkRecordListProps) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">작업 목록</p>
          <h2 className="mt-1 text-2xl font-semibold">최근 작업 이력</h2>
        </div>
        <span className="rounded-full bg-[#eef7ec] px-3 py-1 text-sm font-semibold text-[#246b38]">{records.length}건</span>
      </div>

      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[720px] border-separate border-spacing-y-2 text-left text-sm">
          <thead className="text-[#637063]">
            <tr>
              <th className="px-3 font-semibold">작업일</th>
              <th className="px-3 font-semibold">유형</th>
              <th className="px-3 font-semibold">대상</th>
              <th className="px-3 font-semibold">자재/수량</th>
              <th className="px-3 font-semibold">작업자</th>
              <th className="px-3 font-semibold">메모</th>
            </tr>
          </thead>
          <tbody>
            {records.map((record) => (
              <tr key={record.id} className="bg-[#f8faf7]">
                <td className="rounded-l-md px-3 py-3 font-medium">{record.workDate}</td>
                <td className="px-3 py-3">{record.workType}</td>
                <td className="px-3 py-3">{formatTarget(record)}</td>
                <td className="px-3 py-3">{[record.materialName, record.dilutionRatio, record.quantity].filter(Boolean).join(" / ") || "-"}</td>
                <td className="px-3 py-3">{record.worker ?? "-"}</td>
                <td className="rounded-r-md px-3 py-3">{record.memo ?? "-"}</td>
              </tr>
            ))}
            {records.length === 0 ? (
              <tr>
                <td className="rounded-md bg-[#f8faf7] px-3 py-8 text-center text-[#5c6a60]" colSpan={6}>
                  아직 등록된 작업 이력이 없습니다.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
