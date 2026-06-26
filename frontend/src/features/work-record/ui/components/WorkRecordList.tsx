"use client";

import type { WorkRecord, WorkType } from "@/entities/farm/types";
import { formatWorkRecordContent } from "@/entities/farm/workTypes";
import { formatTarget, formatTargetType } from "../../lib/workRecordForm";

type WorkRecordListProps = {
  currentPage: number;
  pageSize: number;
  records: WorkRecord[];
  selectedRecordId: number | null;
  totalPages: number;
  totalRecords: number;
  workTypes: WorkType[];
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
  onSelect: (recordId: number) => void;
};

export function WorkRecordList({
  currentPage,
  pageSize,
  records,
  selectedRecordId,
  totalPages,
  totalRecords,
  workTypes,
  onPageChange,
  onPageSizeChange,
  onSelect,
}: WorkRecordListProps) {
  const visiblePages = getVisiblePages(currentPage, totalPages);

  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] border-collapse text-left text-sm">
          <thead className="border-y border-[#dfe5dc] text-[#435047]">
            <tr>
              <th className="px-3 py-3 font-semibold">작업일</th>
              <th className="px-3 py-3 font-semibold">작업 유형</th>
              <th className="px-3 py-3 font-semibold">대상</th>
              <th className="px-3 py-3 font-semibold">작업 내용</th>
              <th className="px-3 py-3 font-semibold">작업자</th>
              <th className="px-3 py-3 text-center font-semibold">보기</th>
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
                    <p>{formatTargetType(record.targetType)}</p>
                    <p className="text-xs text-[#6a766e]">
                      {formatTarget(record)}
                    </p>
                  </td>
                  <td className="px-3 py-3">
                    {formatWorkRecordContent(record, workTypes)}
                  </td>
                  <td className="px-3 py-3">{record.worker ?? "-"}</td>
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
                  colSpan={6}
                >
                  조건에 맞는 작업 이력이 없습니다.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm">
        <span className="rounded-md border border-[#dfe5dc] px-4 py-2 text-[#344138]">
          전체 {totalRecords}건
        </span>
        <div className="flex items-center gap-2">
          <button
            className="h-9 w-9 rounded-md border border-[#dfe5dc] disabled:opacity-40"
            type="button"
            disabled={currentPage === 1}
            onClick={() => onPageChange(currentPage - 1)}
          >
            이전
          </button>
          {visiblePages.map((page) => (
            <button
              key={page}
              className={`h-9 w-9 rounded-md border font-bold ${
                page === currentPage
                  ? "border-[#159447] bg-[#159447] text-white"
                  : "border-[#dfe5dc] text-[#344138]"
              }`}
              type="button"
              onClick={() => onPageChange(page)}
            >
              {page}
            </button>
          ))}
          <button
            className="h-9 w-9 rounded-md border border-[#dfe5dc] disabled:opacity-40"
            type="button"
            disabled={currentPage === totalPages}
            onClick={() => onPageChange(currentPage + 1)}
          >
            다음
          </button>
        </div>
        <label className="inline-flex items-center gap-2 rounded-md border border-[#dfe5dc] px-3 py-2 text-[#344138]">
          <span>페이지당</span>
          <select
            className="bg-white font-bold outline-none"
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value))}
          >
            {[5, 10, 20, 50].map((size) => (
              <option key={size} value={size}>
                {size}개
              </option>
            ))}
          </select>
        </label>
      </div>
    </section>
  );
}

function getVisiblePages(currentPage: number, totalPages: number) {
  const maxVisibleCount = 5;
  const halfCount = Math.floor(maxVisibleCount / 2);
  const startPage = Math.max(
    1,
    Math.min(currentPage - halfCount, totalPages - maxVisibleCount + 1),
  );
  const visibleCount = Math.min(maxVisibleCount, totalPages);

  return Array.from({ length: visibleCount }, (_, index) => startPage + index);
}

function WorkTypeBadge({ workType }: { workType: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span className="h-2.5 w-2.5 rounded-full bg-[#31b55f]" />
      {workType}
    </span>
  );
}
