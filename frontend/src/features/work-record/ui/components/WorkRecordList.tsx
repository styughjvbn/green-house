"use client";

import { Plus } from "lucide-react";
import type { WorkRecord, WorkType } from "@/entities/farm/types";
import { formatWorkRecordContent } from "@/entities/farm/workTypes";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import { formatTarget, formatTargetType } from "../../lib/workRecordForm";

type WorkRecordListProps = {
  currentPage: number;
  pageSize: number;
  records: WorkRecord[];
  selectedRecordId: number | null;
  totalPages: number;
  totalRecords: number;
  workTypes: WorkType[];
  onCreate: () => void;
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
  onCreate,
  onPageChange,
  onPageSizeChange,
  onSelect,
}: WorkRecordListProps) {
  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-[#17251b]">작업 이력 목록</h2>
          <p className="mt-1 text-sm text-[#6a766e]">전체 {totalRecords}건</p>
        </div>
        <button
          className="inline-flex h-10 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white shadow-sm"
          type="button"
          onClick={onCreate}
        >
          <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          작업 이력 등록
        </button>
      </div>
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
          <PaginationControls
            nextLabel="다음"
            pageCount={totalPages}
            pageIndex={currentPage - 1}
            pageSize={pageSize}
            pageSizeOptions={[5, 10, 20, 50]}
            previousLabel="이전"
            onPageChange={(pageIndex) => onPageChange(pageIndex + 1)}
            onPageSizeChange={onPageSizeChange}
          />
        </div>
      </div>
    </section>
  );
}

function WorkTypeBadge({ workType }: { workType: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span className="h-2.5 w-2.5 rounded-full bg-[#31b55f]" />
      {workType}
    </span>
  );
}
