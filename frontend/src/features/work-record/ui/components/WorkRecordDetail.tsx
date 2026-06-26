"use client";

import { X } from "lucide-react";
import type { WorkRecord, WorkType } from "@/entities/farm/types";
import {
  getRecordTemplate,
  getWorkRecordFieldLabel,
  getWorkTypeTemplateConfig,
  getWorkTypeTemplateLabel,
} from "@/entities/farm/workTypes";
import { formatTarget, formatTargetType } from "../../lib/workRecordForm";

type WorkRecordDetailProps = {
  record: WorkRecord | null;
  workTypes: WorkType[];
  onClose: () => void;
};

export function WorkRecordDetail({
  onClose,
  record,
  workTypes,
}: WorkRecordDetailProps) {
  if (!record) {
    return (
      <aside className="rounded-md border border-[#dfe5dc] bg-white p-5 text-sm text-[#5c6a60] shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <span>선택한 작업 이력이 없습니다.</span>
          <CloseButton onClose={onClose} />
        </div>
      </aside>
    );
  }

  const template = getRecordTemplate(record, workTypes);
  const fields = getWorkTypeTemplateConfig(template).fields;

  return (
    <aside className="rounded-md border border-[#dfe5dc] bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-[#17251b]">작업 이력 상세</h2>
        <CloseButton onClose={onClose} />
      </div>

      <div className="mt-5">
        <span className="inline-flex rounded-md bg-[#dff3e2] px-3 py-1.5 text-sm font-bold text-[#159447]">
          {record.workType}
        </span>
        <p className="mt-2 text-xs font-semibold text-[#6a766e]">
          {getWorkTypeTemplateLabel(template)}
        </p>
        <p className="mt-4 text-2xl font-bold text-[#17251b]">
          {record.workDate}
        </p>
      </div>

      <dl className="mt-6 space-y-5 text-sm">
        <DetailRow
          label="대상 유형"
          value={formatTargetType(record.targetType)}
        />
        <DetailRow label="대상" value={formatTarget(record)} />

        {fields.includes("materialName") ? (
          <DetailRow
            label={getWorkRecordFieldLabel(template, "materialName")}
            value={record.materialName ?? "-"}
          />
        ) : null}
        {fields.includes("dilutionRatio") ? (
          <DetailRow
            label={getWorkRecordFieldLabel(template, "dilutionRatio")}
            value={record.dilutionRatio ?? "-"}
          />
        ) : null}
        {fields.includes("quantity") ? (
          <DetailRow
            label={getWorkRecordFieldLabel(template, "quantity")}
            value={record.quantity ?? "-"}
          />
        ) : null}
        {fields.includes("worker") ? (
          <DetailRow
            label={getWorkRecordFieldLabel(template, "worker")}
            value={record.worker ?? "-"}
          />
        ) : null}
        {template === "MOVEMENT" ? (
          <>
            <DetailRow
              label="이전 위치"
              value={
                record.fromBedZoneId ? `구역 #${record.fromBedZoneId}` : "-"
              }
            />
            <DetailRow
              label="이동 위치"
              value={record.toBedZoneId ? `구역 #${record.toBedZoneId}` : "-"}
            />
          </>
        ) : null}
        {fields.includes("memo") ? (
          <DetailRow
            label={getWorkRecordFieldLabel(template, "memo")}
            value={record.memo ?? "-"}
            multiline
          />
        ) : null}
      </dl>

      <button
        className="mt-7 w-full rounded-md border border-[#dfe5dc] px-4 py-3 text-sm font-semibold text-[#344138]"
        type="button"
      >
        작업 이력 출력 (A5)
      </button>
    </aside>
  );
}

function CloseButton({ onClose }: { onClose: () => void }) {
  return (
    <button
      className="h-8 w-8 rounded-md text-[#6a766e]"
      type="button"
      aria-label="닫기"
      onClick={onClose}
    >
      <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
    </button>
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
