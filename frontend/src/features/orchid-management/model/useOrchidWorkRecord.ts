"use client";

import { useState } from "react";
import type { WorkRecordTargetType, WorkType } from "@/entities/farm/types";
import {
  findWorkType,
  getManualWorkTypes,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import { createOrchidWorkOperation } from "../api/orchidManagementApi";
import type { WorkRecordQuickFormState } from "./types";

type WorkRecordTarget = {
  type: WorkRecordTargetType | "MANUAL_SELECTION";
  id: number | null;
  ids: number[];
};

export function useOrchidWorkRecord(workTypes: WorkType[]) {
  const [form, setForm] = useState<WorkRecordQuickFormState>(() =>
    createInitialForm(workTypes),
  );

  function prepare(target: WorkRecordTarget) {
    setForm((current) => ({
      ...current,
      workTypeId:
        current.workTypeId ||
        String(getManualWorkTypes(workTypes)[0]?.id ?? workTypes[0]?.id ?? ""),
      targetType: target.type,
      targetId: target.id,
      targetIds: target.ids,
    }));
  }

  function update<K extends keyof WorkRecordQuickFormState>(
    field: K,
    value: WorkRecordQuickFormState[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(
    runMutation: (action: () => Promise<void>) => Promise<void>,
  ) {
    await runMutation(async () => {
      const workTypeId = Number(form.workTypeId);
      const workType = workTypes.find((item) => item.id === workTypeId);
      await createOrchidWorkOperation(
        {
          workTypeId,
          workDate: form.workDate,
          targetType: form.targetType,
          targetId: form.targetId,
          targetIds: form.targetIds,
          ...toVisibleFields(form, workTypes),
        },
        workType?.name ?? "작업",
      );
      setForm((current) => ({
        ...current,
        materialName: "",
        dilutionRatio: "",
        quantity: "",
        memo: "",
      }));
    });
  }

  return { form, prepare, submit, update };
}

function createInitialForm(workTypes: WorkType[]): WorkRecordQuickFormState {
  const firstWorkType = getManualWorkTypes(workTypes)[0] ?? workTypes[0];
  return {
    workTypeId: firstWorkType ? String(firstWorkType.id) : "",
    workDate: new Date().toISOString().slice(0, 10),
    targetType: "HOUSE",
    targetId: null,
    targetIds: [],
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    worker: "",
    memo: "",
  };
}

function toVisibleFields(
  form: WorkRecordQuickFormState,
  workTypes: WorkType[],
) {
  const template = findWorkType(workTypes, Number(form.workTypeId))?.template;
  return {
    materialName: isVisibleWorkRecordField(template ?? null, "materialName")
      ? nullableText(form.materialName)
      : null,
    dilutionRatio: isVisibleWorkRecordField(template ?? null, "dilutionRatio")
      ? nullableText(form.dilutionRatio)
      : null,
    quantity: isVisibleWorkRecordField(template ?? null, "quantity")
      ? nullableText(form.quantity)
      : null,
    worker: isVisibleWorkRecordField(template ?? null, "worker")
      ? nullableText(form.worker)
      : null,
    memo: isVisibleWorkRecordField(template ?? null, "memo")
      ? nullableText(form.memo)
      : null,
  };
}

function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}
