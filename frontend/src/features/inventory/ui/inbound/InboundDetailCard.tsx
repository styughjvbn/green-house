"use client";

import { Scissors } from "lucide-react";
import {
  DetailActionButton,
  DetailCard,
  DetailHeader,
} from "@/shared/ui/DetailCard";
import { StatusBadge } from "@/shared/ui/StatusBadge";
import type {
  InboundRecord,
  InboundRecordUpdatePayload,
} from "../../model/types";
import {
  createInboundEditForm,
  INBOUND_STATUS_LABELS,
  INBOUND_TYPE_LABELS,
  toOptionalNumber,
} from "../../lib/inboundUi";
import { DetailRow, Field, inputClass } from "../common/InventoryPrimitives";

export function InboundDetailCard({
  record,
  editing,
  editForm,
  onToggleEditing,
  onEditFormChange,
  onSubmitUpdate,
  onOpenPotting,
  onOpenCancel,
  onDelete,
}: {
  record: InboundRecord;
  editing: boolean;
  editForm: InboundRecordUpdatePayload;
  onToggleEditing: (
    nextEditing: boolean,
    form: InboundRecordUpdatePayload,
  ) => void;
  onEditFormChange: (
    updater: (
      current: InboundRecordUpdatePayload,
    ) => InboundRecordUpdatePayload,
  ) => void;
  onSubmitUpdate: () => Promise<void>;
  onOpenPotting: () => void;
  onOpenCancel: () => void;
  onDelete: () => void;
}) {
  return (
    <DetailCard>
      <DetailHeader
        eyebrow="입고 상세"
        eyebrowAside={
          <StatusBadge tone={inboundStatusTone(record.status)} size="compact">
            {INBOUND_STATUS_LABELS[record.status]}
          </StatusBadge>
        }
        title={record.varietyName}
        actions={
          <>
            {record.status !== "CANCELED" ? (
              <DetailActionButton
                onClick={() =>
                  onToggleEditing(!editing, createInboundEditForm(record))
                }
              >
                수정
              </DetailActionButton>
            ) : null}
            {record.inboundType === "FLASK_SEEDLING" &&
            record.createdOrchidGroups.length === 0 &&
            record.status !== "CANCELED" ? (
              <DetailActionButton icon={Scissors} onClick={onOpenPotting}>
                포트 작업 실행
              </DetailActionButton>
            ) : null}
            {record.createdOrchidGroups.length === 0 &&
            record.status !== "CANCELED" ? (
              <DetailActionButton tone="danger" onClick={onOpenCancel}>
                입고 취소
              </DetailActionButton>
            ) : null}
            {record.status === "CANCELED" ? (
              <DetailActionButton tone="danger" onClick={onDelete}>
                삭제
              </DetailActionButton>
            ) : null}
          </>
        }
      />
      {editing ? (
        <InboundEditForm
          editForm={editForm}
          record={record}
          onChange={onEditFormChange}
          onSubmit={onSubmitUpdate}
        />
      ) : (
        <InboundDetailView record={record} />
      )}
    </DetailCard>
  );
}

function InboundEditForm({
  record,
  editForm,
  onChange,
  onSubmit,
}: {
  record: InboundRecord;
  editForm: InboundRecordUpdatePayload;
  onChange: (
    updater: (
      current: InboundRecordUpdatePayload,
    ) => InboundRecordUpdatePayload,
  ) => void;
  onSubmit: () => Promise<void>;
}) {
  return (
    <form
      className="grid gap-3 p-4 md:grid-cols-2"
      onSubmit={(event) => {
        event.preventDefault();
        void onSubmit();
      }}
    >
      <DetailRow
        label="입고 유형"
        value={INBOUND_TYPE_LABELS[record.inboundType]}
      />
      <DetailRow
        label="품종명"
        value={`${record.genus} / ${record.varietyName}`}
      />
      <Field label="입고일">
        <input
          className={inputClass}
          required
          type="date"
          value={editForm.inboundDate}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              inboundDate: event.target.value,
            }))
          }
        />
      </Field>
      <Field label="예상 수량">
        <input
          className={inputClass}
          type="number"
          value={editForm.estimatedQuantity ?? ""}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              estimatedQuantity: toOptionalNumber(event.target.value),
            }))
          }
        />
      </Field>
      <Field label="임시 위치">
        <input
          className={inputClass}
          value={editForm.tempLocation ?? ""}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              tempLocation: event.target.value,
            }))
          }
        />
      </Field>
      <Field label="포트 예정일">
        <input
          className={inputClass}
          type="date"
          value={editForm.pottingDueDate ?? ""}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              pottingDueDate: event.target.value || undefined,
            }))
          }
        />
      </Field>
      <Field label="작업자">
        <input
          className={inputClass}
          value={editForm.worker ?? ""}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              worker: event.target.value,
            }))
          }
        />
      </Field>
      <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
        <span>메모</span>
        <textarea
          className="min-h-20 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
          value={editForm.memo ?? ""}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              memo: event.target.value,
            }))
          }
        />
      </label>
      <div className="flex justify-end md:col-span-2">
        <button
          className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
          type="submit"
        >
          저장
        </button>
      </div>
    </form>
  );
}

function InboundDetailView({ record }: { record: InboundRecord }) {
  return (
    <dl className="space-y-1 p-4">
      <DetailRow label="입고일" value={record.inboundDate} />
      <DetailRow
        label="입고 유형"
        value={INBOUND_TYPE_LABELS[record.inboundType]}
      />
      <DetailRow label="속" value={record.genus} />
      <DetailRow label="품종명" value={record.varietyName} />
      <DetailRow
        label="상태"
        value={
          <StatusBadge tone={inboundStatusTone(record.status)}>
            {INBOUND_STATUS_LABELS[record.status]}
          </StatusBadge>
        }
      />
      <DetailRow label="예상 수량" value={record.estimatedQuantity} />
      <DetailRow label="임시 위치" value={record.tempLocation} />
      <DetailRow label="포트 예정일" value={record.pottingDueDate} />
      {record.inboundType === "FLASK_SEEDLING" ? (
        <DetailRow label="포트 작업일" value={record.pottingDate} />
      ) : null}
      <DetailRow label="작업자" value={record.worker} />
      <DetailRow label="메모" value={record.memo} />
      {record.createdOrchidGroups.length ? (
        <div className="mt-4 border-t border-[#e2e8e3] pt-4">
          <dt className="mb-2 text-xs font-semibold text-[#6a766e]">
            배치된 난 묶음
          </dt>
          <dd className="space-y-2">
            {record.createdOrchidGroups.map((group) => (
              <div
                className="rounded-md border border-[#d7ddd8] bg-[#f8faf8] p-3 text-sm"
                key={group.id}
              >
                <div className="flex items-center justify-between gap-3">
                  <strong>난 묶음 #{group.id}</strong>
                  <StatusBadge tone="blue" size="compact">
                    {group.status}
                  </StatusBadge>
                </div>
                <p className="mt-1 text-[#526057]">
                  {group.quantity}분 · {group.potSize ?? "화분 미지정"} ·{" "}
                  {group.ageYear == null
                    ? "년생 미지정"
                    : `${group.ageYear}년생`}
                </p>
                <p className="mt-1 text-xs text-[#6a766e]">
                  {group.location}
                  {group.placementType ? ` · ${group.placementType}` : ""}
                </p>
              </div>
            ))}
          </dd>
        </div>
      ) : null}
    </dl>
  );
}

function inboundStatusTone(status: InboundRecord["status"]) {
  if (status === "CANCELED") return "gray";
  return "blue";
}
