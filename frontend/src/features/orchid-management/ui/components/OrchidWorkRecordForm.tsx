"use client";

import type { FormEvent } from "react";
import type {
  BedZone,
  House,
  OrchidGroup,
  PhysicalBed,
  WorkType,
} from "@/entities/farm/types";
import {
  findWorkType,
  getManualWorkTypes,
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import type {
  OrchidSelection,
  WorkRecordQuickFormState,
} from "../../model/types";
import TextField, { SelectField } from "./TextField";

type OrchidWorkRecordFormProps = {
  form: WorkRecordQuickFormState;
  house: House;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedOrchidGroup: OrchidGroup | null;
  selectedPhysicalBed: PhysicalBed | null;
  selection: OrchidSelection | null;
  workTypes: WorkType[];
  onCancel: () => void;
  onChange: <K extends keyof WorkRecordQuickFormState>(
    field: K,
    value: WorkRecordQuickFormState[K],
  ) => void;
  onSubmit: () => Promise<void>;
};

export default function OrchidWorkRecordForm({
  form,
  house,
  resolvedZone,
  saving,
  selectedOrchidGroup,
  selectedPhysicalBed,
  selection,
  workTypes,
  onCancel,
  onChange,
  onSubmit,
}: OrchidWorkRecordFormProps) {
  const manualWorkTypes = getManualWorkTypes(workTypes);
  const selectedWorkType = findWorkType(workTypes, Number(form.workTypeId));
  const template = selectedWorkType?.template ?? null;
  const targetLabel = selectedOrchidGroup
    ? `${selectedOrchidGroup.varietyName} / ${selectedOrchidGroup.quantity}분`
    : resolvedZone
      ? `${resolvedZone.physicalBedNumber}다이 ${resolvedZone.name}`
      : selectedPhysicalBed
        ? `${house.number}동 ${selectedPhysicalBed.number}다이`
        : selection?.type === "HOUSE"
          ? `${house.number}동`
          : "선택 대상";

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit();
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">작업 기록 추가</p>
          <h3 className="mt-1 text-lg font-semibold">{targetLabel}</h3>
        </div>
        <button
          className="rounded-md border border-[#d7ddd4] px-2 py-1 text-xs font-semibold"
          type="button"
          onClick={onCancel}
        >
          닫기
        </button>
      </div>

      <form className="mt-3 space-y-3" onSubmit={handleSubmit}>
        <div className="grid grid-cols-2 gap-2">
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

        {isVisibleWorkRecordField(template, "materialName") ||
        isVisibleWorkRecordField(template, "dilutionRatio") ? (
          <div className="grid grid-cols-2 gap-2">
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

        <div className="grid grid-cols-2 gap-2">
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

        <button
          className="w-full rounded-md bg-[#246df2] px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
          disabled={saving || !form.workTypeId}
          type="submit"
        >
          {saving ? "저장 중" : "작업 기록 저장"}
        </button>
      </form>
    </section>
  );
}
