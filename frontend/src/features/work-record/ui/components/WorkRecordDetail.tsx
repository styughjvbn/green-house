"use client";

import { X } from "lucide-react";
import type { WorkRecord } from "@/entities/farm/types";
import {
  formatMaterialSummary,
  formatTarget,
  formatTargetType,
} from "../../lib/workRecordForm";

type WorkRecordDetailProps = {
  record: WorkRecord | null;
  onClose: () => void;
};

export function WorkRecordDetail({ onClose, record }: WorkRecordDetailProps) {
  if (!record) {
    return (
      <aside className="rounded-md border border-[#dfe5dc] bg-white p-5 text-sm text-[#5c6a60] shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <span>선택한 작업 이력이 없습니다.</span>
          <button
            className="h-8 w-8 rounded-md text-[#6a766e]"
            type="button"
            aria-label="닫기"
            onClick={onClose}
          >
            <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          </button>
        </div>
      </aside>
    );
  }

  return (
    <aside className="rounded-md border border-[#dfe5dc] bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-[#17251b]">작업 이력 상세</h2>
        <button
          className="h-8 w-8 rounded-md text-[#6a766e]"
          type="button"
          aria-label="닫기"
          onClick={onClose}
        >
          <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        </button>
      </div>

      <div className="mt-5">
        <WorkTypePill workType={record.workType} />
        <p className="mt-4 text-2xl font-bold text-[#17251b]">
          {record.workDate}
        </p>
      </div>

      <dl className="mt-6 space-y-5 text-sm">
        <DetailRow
          label="대상 유형"
          value={formatTargetType(record.targetType)}
        />
        <DetailRow label="세부 대상" value={formatTarget(record)} />
        <DetailRow label="약제 / 자재" value={record.materialName ?? "-"} />
        <DetailRow
          label="농도 / 희석배수"
          value={record.dilutionRatio ?? "-"}
        />
        <DetailRow label="수량" value={record.quantity ?? "-"} />
        <DetailRow label="작업자" value={record.worker ?? "-"} />
        {record.fromBedZoneId || record.toBedZoneId ? (
          <>
            <DetailRow
              label="이전 위치"
              value={
                record.fromBedZoneId ? `구역 #${record.fromBedZoneId}` : "-"
              }
            />
            <DetailRow
              label="새 위치"
              value={record.toBedZoneId ? `구역 #${record.toBedZoneId}` : "-"}
            />
          </>
        ) : null}
        <DetailRow label="메모" value={record.memo ?? "-"} multiline />
      </dl>

      <div className="mt-6 border-t border-[#edf0ec] pt-5">
        <DetailRow label="등록일" value={record.workDate} />
        <DetailRow label="수정일" value={record.workDate} />
      </div>

      <div className="mt-7 grid grid-cols-2 gap-3">
        <button
          className="rounded-md border border-[#94c49a] px-4 py-2.5 text-sm font-semibold text-[#159447]"
          type="button"
        >
          수정
        </button>
        <button
          className="rounded-md border border-[#efb3b3] px-4 py-2.5 text-sm font-semibold text-[#e52d2d]"
          type="button"
        >
          삭제
        </button>
      </div>

      <button
        className="mt-5 w-full rounded-md border border-[#dfe5dc] px-4 py-3 text-sm font-semibold text-[#344138]"
        type="button"
      >
        작업 이력 출력 (A5)
      </button>

      <p className="sr-only">{formatMaterialSummary(record)}</p>
    </aside>
  );
}

function WorkTypePill({ workType }: { workType: string }) {
  return (
    <span className="inline-flex rounded-md bg-[#dff3e2] px-3 py-1.5 text-sm font-bold text-[#159447]">
      {workType}
    </span>
  );
}

function DetailRow({
  label,
  multiline = false,
  value,
}: {
  label: string;
  multiline?: boolean;
  value: string;
}) {
  return (
    <div className="grid grid-cols-[90px_minmax(0,1fr)] gap-4">
      <dt className="text-[#6a766e]">{label}</dt>
      <dd
        className={`font-semibold text-[#344138] ${multiline ? "whitespace-pre-line" : ""}`}
      >
        {value}
      </dd>
    </div>
  );
}
