"use client";

import { ChevronDown, ChevronUp, X } from "lucide-react";
import { useState } from "react";
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
  const [additionalInfoOpen, setAdditionalInfoOpen] = useState(false);

  if (!record) {
    return (
      <aside className="min-h-0 overflow-y-auto rounded-md border border-[#dfe5dc] bg-white p-5 text-sm text-[#5c6a60] shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <span>선택한 작업 이력이 없습니다.</span>
          <CloseButton onClose={onClose} />
        </div>
      </aside>
    );
  }

  const template = getRecordTemplate(record, workTypes);
  const fields = getWorkTypeTemplateConfig(template).fields;
  const detailEntries = formatDetailEntries(record.details);

  return (
    <aside className="min-h-0 overflow-y-auto rounded-md border border-[#dfe5dc] bg-white p-5 shadow-sm">
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
        {detailEntries.length ? (
          <>
            <dt className="pt-2">
              <button
                className="flex w-full items-center justify-between rounded-md bg-[#f6f8f5] px-3 py-2 text-left text-sm font-bold text-[#17251b]"
                type="button"
                onClick={() => setAdditionalInfoOpen((open) => !open)}
              >
                <span>추가 정보</span>
                {additionalInfoOpen ? (
                  <ChevronUp
                    className="h-4 w-4 text-[#6a766e]"
                    strokeWidth={1.8}
                    aria-hidden="true"
                  />
                ) : (
                  <ChevronDown
                    className="h-4 w-4 text-[#6a766e]"
                    strokeWidth={1.8}
                    aria-hidden="true"
                  />
                )}
              </button>
            </dt>
            {additionalInfoOpen
              ? detailEntries.map((entry) => (
                  <DetailRow
                    key={entry.key}
                    label={entry.label}
                    value={entry.value}
                  />
                ))
              : null}
          </>
        ) : null}
      </dl>
    </aside>
  );
}

function CloseButton({ onClose }: { onClose: () => void }) {
  return (
    <button
      className="h-5 w-5 rounded-md text-[#6a766e]"
      type="button"
      aria-label="닫기"
      onClick={onClose}
    >
      <X className="h-5 w-5" strokeWidth={1.8} aria-hidden="true" />
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

function formatDetailEntries(details: Record<string, unknown> | null) {
  if (!details) {
    return [];
  }

  return Object.entries(details)
    .map(([key, value]) => {
      const formatted = formatDetailValue(key, value);
      return formatted
        ? {
            key,
            label: DETAIL_LABELS[key] ?? key,
            value: formatted,
          }
        : null;
    })
    .filter((entry): entry is { key: string; label: string; value: string } =>
      Boolean(entry),
    );
}

function formatDetailValue(key: string, value: unknown) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  if (key === "inboundType" && typeof value === "string") {
    return INBOUND_TYPE_LABELS[value] ?? value;
  }
  if (key === "status" && typeof value === "string") {
    return INBOUND_STATUS_LABELS[value] ?? value;
  }
  return String(value);
}

const DETAIL_LABELS: Record<string, string> = {
  actualQuantity: "실제 수량",
  ageYear: "년생",
  bedZoneId: "구역 ID",
  bottleCount: "병 수",
  estimatedQuantity: "예상 수량",
  genus: "속명",
  growthStage: "생육 단계",
  inboundRecordId: "입고 기록 ID",
  inboundType: "입고 유형",
  orchidGroupId: "난 묶음 ID",
  placementType: "배치 규격",
  potSize: "화분 크기",
  pottingDueDate: "포트 예정일",
  status: "상태",
  tempLocation: "임시 위치",
  trayCount: "판수",
  varietyId: "품종 ID",
  varietyName: "품종",
};

const INBOUND_TYPE_LABELS: Record<string, string> = {
  FLASK_SEEDLING: "유리병 모종",
  POTTED_SEEDLING: "포트 모종",
  PRODUCT_POT: "상품분",
  SAMPLE: "샘플",
  ETC: "기타",
};

const INBOUND_STATUS_LABELS: Record<string, string> = {
  TEMP_STORED: "임시보관",
  POTTING_PENDING: "포트작업대기",
  POTTED: "포트작업완료",
  PLACED: "배치완료",
  CANCELED: "취소",
};
