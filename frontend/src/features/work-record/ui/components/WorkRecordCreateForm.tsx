"use client";

import { X } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import type { BedZone, OrchidGroup, WorkType } from "@/entities/farm/types";
import {
  findWorkType,
  getManualWorkTypes,
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import type { WorkRecordFormState } from "../../model/types";
import { SelectField, TextField } from "./FormFields";
import { WorkTargetSelectionDialog } from "./WorkTargetSelectionDialog";

type WorkRecordCreateFormProps = {
  bedZones: BedZone[];
  errorMessage: string | null;
  form: WorkRecordFormState;
  orchidGroups: OrchidGroup[];
  selectedOrchidGroupIds: Set<number>;
  saving: boolean;
  workTypes: WorkType[];
  onClose: () => void;
  onOpenPlannedWork: () => void;
  onOpenDedicatedWork: (
    workTypeCode: "MOVEMENT" | "REPOT" | "DIVIDE" | "MERGE" | "POTTING",
  ) => void;
  onChange: <K extends keyof WorkRecordFormState>(
    field: K,
    value: WorkRecordFormState[K],
  ) => void;
  onTargetChange: (selectedIds: Set<number>) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function WorkRecordCreateForm({
  bedZones,
  errorMessage,
  form,
  orchidGroups,
  selectedOrchidGroupIds,
  saving,
  workTypes,
  onClose,
  onChange,
  onOpenPlannedWork,
  onOpenDedicatedWork,
  onTargetChange,
  onSubmit,
}: WorkRecordCreateFormProps) {
  const [targetSelectorOpen, setTargetSelectorOpen] = useState(false);
  const manualWorkTypes = getManualWorkTypes(workTypes);
  const selectedWorkType = findWorkType(workTypes, Number(form.workTypeId));
  const template = selectedWorkType?.template ?? null;
  const selectedGroups = useMemo(
    () => orchidGroups.filter((group) => selectedOrchidGroupIds.has(group.id)),
    [orchidGroups, selectedOrchidGroupIds],
  );
  const selectedQuantity = selectedGroups.reduce(
    (sum, group) => sum + group.quantity,
    0,
  );
  const selectedZoneCount = new Set(
    selectedGroups.map((group) => group.bedZoneId),
  ).size;

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-2xl flex-col rounded-md bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="작업 등록"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[#edf0ec] p-5">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">작업 등록</p>
            <h2 className="mt-1 text-xl font-semibold">작업 등록</h2>
            <p className="mt-1 text-sm text-[#5c6a60]">
              완료된 작업을 기록하거나 기간을 정해 작업을 계획합니다.
            </p>
          </div>
          <button
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          </button>
        </div>

        <form
          className="min-h-0 space-y-3 overflow-y-auto p-5"
          onSubmit={onSubmit}
        >
          <div className="grid grid-cols-2 gap-2 rounded-md bg-[#edf3ed] p-1">
            <button
              className="rounded bg-white px-3 py-2 text-sm font-semibold text-[#10783a] shadow-sm"
              type="button"
              aria-current="page"
            >
              완료 작업 기록
            </button>
            <button
              className="rounded px-3 py-2 text-sm font-semibold text-[#526057] hover:bg-white/70"
              type="button"
              onClick={onOpenPlannedWork}
            >
              기간 작업 계획
            </button>
          </div>

          <section className="rounded-md border border-[#cfe0d2] bg-[#f7faf6] p-4">
            <p className="text-sm font-semibold text-[#26352b]">전용 작업</p>
            <p className="mt-1 text-xs text-[#6a766e]">
              대상별 목적지나 결과 난 묶음을 입력해야 하는 작업입니다.
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              {(
                [
                  ["MOVEMENT", "자리 이동", "목적 구역·칸 입력"],
                  ["REPOT", "분갈이", "투입·손실·결과 입력"],
                  ["DIVIDE", "분주", "하나의 원본을 여러 묶음으로 분리"],
                  ["MERGE", "합식", "여러 원본을 하나의 묶음으로 결합"],
                  ["POTTING", "포트 작업", "입고 기록·배치 입력"],
                ] as const
              ).map(([code, label, description]) => {
                const available = workTypes.some(
                  (workType) => workType.active && workType.code === code,
                );
                return (
                  <button
                    key={code}
                    className="rounded-md border border-[#cfd8cc] bg-white px-3 py-3 text-left hover:border-[#159447] hover:bg-[#f1faf3] disabled:cursor-not-allowed disabled:opacity-40"
                    disabled={!available}
                    type="button"
                    onClick={() => onOpenDedicatedWork(code)}
                  >
                    <span className="block text-sm font-semibold text-[#26352b]">
                      {label}
                    </span>
                    <span className="mt-1 block text-xs text-[#6a766e]">
                      {description}
                    </span>
                  </button>
                );
              })}
            </div>
          </section>

          <div className="flex items-center gap-3 py-1">
            <span className="h-px flex-1 bg-[#e2e7e1]" />
            <span className="text-xs font-semibold text-[#6a766e]">
              일반 작업 바로 기록
            </span>
            <span className="h-px flex-1 bg-[#e2e7e1]" />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <SelectField
              label="작업 유형"
              value={form.workTypeId}
              onChange={(value) => onChange("workTypeId", value)}
            >
              {manualWorkTypes.map((workType) => (
                <option key={workType.id} value={workType.id}>
                  {workType.name}
                </option>
              ))}
            </SelectField>
            <TextField
              label="작업일"
              required
              type="date"
              value={form.workDate}
              onChange={(value) => onChange("workDate", value)}
            />
          </div>

          <section className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-[#cfe0d2] bg-[#f7faf6] p-4">
            <div>
              <p className="text-sm font-semibold text-[#26352b]">작업 대상</p>
              <p className="mt-1 text-sm text-[#5c6a60]">
                {selectedGroups.length > 0
                  ? `${selectedGroups.length}묶음 · ${selectedQuantity}분 · ${selectedZoneCount}개 구역`
                  : "동, 다이, 구역 또는 개별 난 묶음을 선택하세요."}
              </p>
            </div>
            <button
              className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a] hover:bg-[#f1faf3]"
              type="button"
              onClick={() => setTargetSelectorOpen(true)}
            >
              {selectedGroups.length > 0 ? "대상 변경" : "작업 대상 선택"}
            </button>
          </section>

          {isVisibleWorkRecordField(template, "materialName") ||
          isVisibleWorkRecordField(template, "dilutionRatio") ? (
            <div className="grid grid-cols-2 gap-3">
              {isVisibleWorkRecordField(template, "materialName") ? (
                <TextField
                  label={getWorkRecordFieldLabel(template, "materialName")}
                  value={form.materialName}
                  onChange={(value) => onChange("materialName", value)}
                />
              ) : null}
              {isVisibleWorkRecordField(template, "dilutionRatio") ? (
                <TextField
                  label={getWorkRecordFieldLabel(template, "dilutionRatio")}
                  value={form.dilutionRatio}
                  onChange={(value) => onChange("dilutionRatio", value)}
                />
              ) : null}
            </div>
          ) : null}

          <div className="grid grid-cols-2 gap-3">
            {isVisibleWorkRecordField(template, "quantity") ? (
              <TextField
                label={getWorkRecordFieldLabel(template, "quantity")}
                value={form.quantity}
                onChange={(value) => onChange("quantity", value)}
              />
            ) : null}
            {isVisibleWorkRecordField(template, "worker") ? (
              <TextField
                label={getWorkRecordFieldLabel(template, "worker")}
                value={form.worker}
                onChange={(value) => onChange("worker", value)}
              />
            ) : null}
          </div>

          {isVisibleWorkRecordField(template, "memo") ? (
            <label className="block">
              <span className="text-sm font-semibold text-[#435047]">
                {getWorkRecordFieldLabel(template, "memo")}
              </span>
              <textarea
                className="mt-1 min-h-20 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
                value={form.memo}
                onChange={(event) => onChange("memo", event.target.value)}
              />
            </label>
          ) : null}

          {errorMessage ? (
            <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
              {errorMessage}
            </p>
          ) : null}

          <div className="flex justify-end gap-2 border-t border-[#edf0ec] pt-4">
            <button
              className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
              type="button"
              onClick={onClose}
            >
              취소
            </button>
            <button
              className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={
                saving || !form.workTypeId || selectedOrchidGroupIds.size === 0
              }
              type="submit"
            >
              {saving ? "저장 중" : "작업 이력 저장"}
            </button>
          </div>
        </form>
      </section>
      {targetSelectorOpen ? (
        <WorkTargetSelectionDialog
          bedZones={bedZones}
          groups={orchidGroups}
          initialSelectedIds={selectedOrchidGroupIds}
          onClose={() => setTargetSelectorOpen(false)}
          onConfirm={(selectedIds) => {
            onTargetChange(selectedIds);
            setTargetSelectorOpen(false);
          }}
        />
      ) : null}
    </div>
  );
}
