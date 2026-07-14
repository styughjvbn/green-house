"use client";

import { useMemo, useState } from "react";
import type {
  HouseStatusSummary,
  WorkOperation,
  WorkTargetPreview,
  WorkType,
} from "@/entities/farm/types";
import {
  completeWorkOperation,
  createWorkOperation,
  previewWorkOperationTargets,
} from "../../api/workRecordApi";
import type { WorkOperationFormState } from "../../model/types";
import { SelectField, TextField } from "./FormFields";

export function HouseWorkOperationPanel({
  houses,
  workTypes,
  onClose,
}: {
  houses: HouseStatusSummary[];
  workTypes: WorkType[];
  onClose: () => void;
}) {
  const pesticideType = workTypes.find(
    (workType) => workType.code === "PESTICIDE",
  );
  const [form, setForm] = useState<WorkOperationFormState>(() => ({
    houseId: String(houses[0]?.houseId ?? ""),
    title: houses[0] ? `${houses[0].houseNumber}동 농약 작업` : "농약 작업",
    plannedStartDate: new Date().toISOString().slice(0, 10),
    plannedEndDate: "",
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    worker: "",
    memo: "",
  }));
  const [preview, setPreview] = useState<WorkTargetPreview | null>(null);
  const [operation, setOperation] = useState<WorkOperation | null>(null);
  const [excludedIds, setExcludedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const includedTargets = useMemo(
    () =>
      preview?.targets.filter(
        (target) => !excludedIds.has(target.orchidGroupId),
      ) ?? [],
    [excludedIds, preview],
  );
  const includedQuantity = includedTargets.reduce(
    (sum, target) => sum + target.quantitySnapshot,
    0,
  );

  function updateForm<K extends keyof WorkOperationFormState>(
    field: K,
    value: WorkOperationFormState[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
    if (field === "houseId") {
      const house = houses.find((item) => String(item.houseId) === value);
      setForm((current) => ({
        ...current,
        houseId: value,
        title: house ? `${house.houseNumber}동 농약 작업` : current.title,
      }));
      setPreview(null);
      setExcludedIds(new Set());
    }
  }

  async function loadPreview() {
    if (!form.houseId) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const result = await previewWorkOperationTargets({
        scopeType: "HOUSE",
        scopeId: Number(form.houseId),
      });
      setPreview(result);
      setExcludedIds(new Set());
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "대상을 확인하지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  async function saveOperation() {
    if (!preview || !pesticideType || includedTargets.length === 0) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const result = await createWorkOperation({
        workTypeId: pesticideType.id,
        title: form.title.trim(),
        plannedStartDate: form.plannedStartDate,
        plannedEndDate: form.plannedEndDate || null,
        sourceScopeType: "HOUSE",
        sourceScopeId: Number(form.houseId),
        details: {
          materialName: form.materialName.trim() || null,
          dilutionRatio: form.dilutionRatio.trim() || null,
          quantity: form.quantity.trim() || null,
        },
        worker: form.worker.trim() || null,
        memo: form.memo.trim() || null,
        excludedOrchidGroupIds: [...excludedIds],
      });
      setOperation(result);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "작업을 저장하지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  async function completeOperation() {
    if (!operation) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      setOperation(await completeWorkOperation(operation.id));
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "작업을 완료하지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="rounded-md border border-[#b9d7bf] bg-[#f5fbf5] p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-[#17251b]">
            동 전체 농약 작업
          </h2>
          <p className="mt-1 text-sm text-[#5c6a60]">
            저장 시 현재 포함된 난 묶음과 위치가 확정됩니다.
          </p>
        </div>
        <button
          className="rounded-md border border-[#b9c7bc] bg-white px-3 py-2 text-sm"
          type="button"
          onClick={onClose}
        >
          닫기
        </button>
      </div>

      {errorMessage ? (
        <p className="mt-3 rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
          {errorMessage}
        </p>
      ) : null}

      {operation ? (
        <OperationResult
          operation={operation}
          loading={loading}
          onComplete={completeOperation}
        />
      ) : (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-2 lg:grid-cols-4">
            <SelectField
              label="대상 동"
              value={form.houseId}
              onChange={(value) => updateForm("houseId", value)}
            >
              {houses.map((house) => (
                <option key={house.houseId} value={house.houseId}>
                  {house.houseNumber}동
                </option>
              ))}
            </SelectField>
            <TextField
              label="작업명"
              required
              value={form.title}
              onChange={(value) => updateForm("title", value)}
            />
            <TextField
              label="시작일"
              required
              type="date"
              value={form.plannedStartDate}
              onChange={(value) => updateForm("plannedStartDate", value)}
            />
            <TextField
              label="종료 예정일"
              type="date"
              value={form.plannedEndDate}
              onChange={(value) => updateForm("plannedEndDate", value)}
            />
            <TextField
              label="약제명"
              value={form.materialName}
              onChange={(value) => updateForm("materialName", value)}
            />
            <TextField
              label="희석 배수"
              value={form.dilutionRatio}
              onChange={(value) => updateForm("dilutionRatio", value)}
            />
            <TextField
              label="사용량"
              value={form.quantity}
              onChange={(value) => updateForm("quantity", value)}
            />
            <TextField
              label="작업자"
              value={form.worker}
              onChange={(value) => updateForm("worker", value)}
            />
          </div>
          <label className="mt-3 block text-sm font-semibold text-[#435047]">
            메모
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-[#cfd8cc] bg-white px-3 py-2 font-normal"
              value={form.memo}
              onChange={(event) => updateForm("memo", event.target.value)}
            />
          </label>

          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a]"
              disabled={loading || !form.houseId}
              type="button"
              onClick={loadPreview}
            >
              {loading ? "확인 중" : "실제 대상 미리보기"}
            </button>
            {preview ? (
              <span className="text-sm font-semibold text-[#344138]">
                포함 {includedTargets.length}묶음 · {includedQuantity}분
              </span>
            ) : null}
          </div>

          {preview ? (
            <TargetPreview
              preview={preview}
              excludedIds={excludedIds}
              onToggle={(id) =>
                setExcludedIds((current) => {
                  const next = new Set(current);
                  if (next.has(id)) next.delete(id);
                  else next.add(id);
                  return next;
                })
              }
            />
          ) : null}

          <div className="mt-4 flex justify-end">
            <button
              className="rounded-md bg-[#159447] px-5 py-2.5 text-sm font-semibold text-white disabled:bg-[#9bb7a2]"
              disabled={
                loading ||
                !preview ||
                includedTargets.length === 0 ||
                !form.title.trim() ||
                !pesticideType
              }
              type="button"
              onClick={saveOperation}
            >
              작업 저장
            </button>
          </div>
        </>
      )}
    </section>
  );
}

function TargetPreview({
  preview,
  excludedIds,
  onToggle,
}: {
  preview: WorkTargetPreview;
  excludedIds: Set<number>;
  onToggle: (id: number) => void;
}) {
  return (
    <div className="mt-4 max-h-52 overflow-auto rounded-md border border-[#d7ddd4] bg-white">
      {preview.targets.map((target) => (
        <label
          key={target.orchidGroupId}
          className="flex cursor-pointer items-center gap-3 border-b border-[#edf0ec] px-3 py-2 text-sm last:border-b-0"
        >
          <input
            checked={!excludedIds.has(target.orchidGroupId)}
            type="checkbox"
            onChange={() => onToggle(target.orchidGroupId)}
          />
          <span className="min-w-0 flex-1 truncate font-semibold">
            {target.varietyName}
          </span>
          <span className="text-[#5c6a60]">
            {target.locationSnapshot.physicalBedNumber}다이{" "}
            {target.locationSnapshot.bedZoneName}
          </span>
          <span className="w-16 text-right">{target.quantitySnapshot}분</span>
        </label>
      ))}
    </div>
  );
}

function OperationResult({
  operation,
  loading,
  onComplete,
}: {
  operation: WorkOperation;
  loading: boolean;
  onComplete: () => void;
}) {
  const completed = operation.status === "COMPLETED";
  return (
    <div className="mt-4 rounded-md border border-[#cfe0d2] bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="font-bold text-[#17251b]">{operation.title}</p>
          <p className="mt-1 text-sm text-[#5c6a60]">
            대상 {operation.targets.length}묶음 · 상태{" "}
            {completed ? "완료" : "계획"}
          </p>
        </div>
        {!completed ? (
          <button
            className="rounded-md bg-[#246df2] px-5 py-2.5 text-sm font-semibold text-white"
            disabled={loading}
            type="button"
            onClick={onComplete}
          >
            {loading ? "처리 중" : "전체 작업 완료"}
          </button>
        ) : (
          <span className="rounded-full bg-[#e7f6eb] px-3 py-1.5 text-sm font-semibold text-[#10783a]">
            완료됨
          </span>
        )}
      </div>
    </div>
  );
}
