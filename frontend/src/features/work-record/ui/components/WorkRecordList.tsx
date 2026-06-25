"use client";

import type { WorkRecord } from "@/entities/farm/types";
import { formatTarget, formatTargetType } from "../../lib/workRecordForm";

type WorkRecordListProps = {
  records: WorkRecord[];
  selectedRecordId: number | null;
  onSelect: (recordId: number) => void;
};

export function WorkRecordList({
  records,
  selectedRecordId,
  onSelect,
}: WorkRecordListProps) {
  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] border-collapse text-left text-sm">
          <thead className="border-y border-[#dfe5dc] text-[#435047]">
            <tr>
              <th className="px-3 py-3 font-semibold">작업일</th>
              <th className="px-3 py-3 font-semibold">작업 유형</th>
              <th className="px-3 py-3 font-semibold">대상</th>
              <th className="px-3 py-3 font-semibold">세부 대상</th>
              <th className="px-3 py-3 font-semibold">약제명 / 자재명</th>
              <th className="px-3 py-3 font-semibold">농도 / 희석배수</th>
              <th className="px-3 py-3 font-semibold">작업자</th>
              <th className="px-3 py-3 font-semibold">메모</th>
              <th className="px-3 py-3 font-semibold">등록일</th>
              <th className="px-3 py-3 text-center font-semibold">더보기</th>
            </tr>
          </thead>
          <tbody>
            {records.map((record) => {
              const selected = selectedRecordId === record.id;

              return (
                <tr
                  key={record.id}
                  className={`cursor-pointer border-b border-[#edf0ec] transition hover:bg-[#eef7ec] ${
                    selected ? "bg-[#eaf7eb]" : "bg-white"
                  }`}
                  onClick={() => onSelect(record.id)}
                >
                  <td className="px-3 py-3 font-bold">{record.workDate}</td>
                  <td className="px-3 py-3">
                    <WorkTypeBadge workType={record.workType} />
                  </td>
                  <td className="px-3 py-3">
                    {formatTargetType(record.targetType)}
                  </td>
                  <td className="px-3 py-3">{formatTarget(record)}</td>
                  <td className="px-3 py-3">{record.materialName ?? "-"}</td>
                  <td className="px-3 py-3">
                    {[record.dilutionRatio, record.quantity]
                      .filter(Boolean)
                      .join(" / ") || "-"}
                  </td>
                  <td className="px-3 py-3">{record.worker ?? "-"}</td>
                  <td className="px-3 py-3">{record.memo ?? "-"}</td>
                  <td className="px-3 py-3 text-xs text-[#5c6a60]">
                    {record.workDate}
                  </td>
                  <td className="px-3 py-3 text-center">
                    <button
                      className="h-8 w-8 rounded-md border border-[#dfe5dc] text-[#5c6a60]"
                      type="button"
                    >
                      ...
                    </button>
                  </td>
                </tr>
              );
            })}
            {records.length === 0 ? (
              <tr>
                <td
                  className="px-3 py-12 text-center text-[#5c6a60]"
                  colSpan={10}
                >
                  조건에 맞는 작업 이력이 없습니다.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      <div className="mt-4 flex items-center justify-between text-sm">
        <span className="rounded-md border border-[#dfe5dc] px-4 py-2 text-[#344138]">
          전체 {records.length}건
        </span>
        <div className="flex items-center gap-2">
          <button
            className="h-9 w-9 rounded-md border border-[#dfe5dc]"
            type="button"
          >
            ‹
          </button>
          <button
            className="h-9 w-9 rounded-md bg-[#159447] font-bold text-white"
            type="button"
          >
            1
          </button>
          <button
            className="h-9 w-9 rounded-md border border-[#dfe5dc]"
            type="button"
          >
            2
          </button>
          <button
            className="h-9 w-9 rounded-md border border-[#dfe5dc]"
            type="button"
          >
            3
          </button>
          <button
            className="h-9 w-9 rounded-md border border-[#dfe5dc]"
            type="button"
          >
            ›
          </button>
        </div>
        <span className="rounded-md border border-[#dfe5dc] px-4 py-2 text-[#344138]">
          페이지당 10개
        </span>
      </div>
    </section>
  );
}

function WorkTypeBadge({ workType }: { workType: string }) {
  const tone = getWorkTypeTone(workType);
  const dotClass = {
    blue: "bg-[#246df2]",
    green: "bg-[#31b55f]",
    orange: "bg-[#f59e0b]",
    purple: "bg-[#a855f7]",
    red: "bg-[#ef4776]",
    teal: "bg-[#1399ad]",
    brown: "bg-[#a66a37]",
  }[tone];

  return (
    <span className="inline-flex items-center gap-2">
      <span className={`h-2.5 w-2.5 rounded-full ${dotClass}`} />
      {workType}
    </span>
  );
}

function getWorkTypeTone(workType: string) {
  if (workType === "위치 이동") return "blue";
  if (workType === "농약") return "green";
  if (workType === "비료") return "orange";
  if (workType === "분갈이") return "purple";
  if (workType.includes("꽃")) return "red";
  if (workType.includes("잎")) return "teal";
  return "brown";
}
