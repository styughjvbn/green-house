"use client";

import { useEffect, useMemo, useState } from "react";
import type {
  HouseStatusSummary,
  OrchidGroup,
  WorkOperation,
  WorkTargetPreview,
  WorkType,
} from "@/entities/farm/types";
import {
  completeWorkOperation,
  createWorkOperation,
  getWorkOperationScopeOptions,
  previewWorkOperationTargets,
  transitionWorkOperation,
  transitionWorkOperationTarget,
} from "../../api/workRecordApi";
import type {
  WorkOperationFormState,
  WorkOperationScopeOptions,
  WorkTargetPreviewPayload,
} from "../../model/types";
import { SelectField, TextField } from "./FormFields";

export function WorkOperationPanel({
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
    sourceScopeType: "HOUSE",
    houseId: String(houses[0]?.houseId ?? ""),
    scopeKey: "",
    collectionId: "",
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
  const [manualIds, setManualIds] = useState<Set<number>>(new Set());
  const [manualKeyword, setManualKeyword] = useState("");
  const [scopeOptions, setScopeOptions] =
    useState<WorkOperationScopeOptions | null>(null);
  const [loading, setLoading] = useState(false);
  const [optionsLoading, setOptionsLoading] = useState(true);
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
  const manualGroups = useMemo(() => {
    const keyword = manualKeyword.trim().toLowerCase();
    return (scopeOptions?.orchidGroups ?? []).filter(
      (group) =>
        !keyword ||
        group.varietyName.toLowerCase().includes(keyword) ||
        `${group.houseNumber}동 ${group.physicalBedNumber}다이 ${group.bedZoneName}`.includes(
          keyword,
        ),
    );
  }, [manualKeyword, scopeOptions]);

  useEffect(() => {
    let cancelled = false;
    void getWorkOperationScopeOptions()
      .then((result) => {
        if (!cancelled) setScopeOptions(result);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "작업 대상 목록을 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setOptionsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function updateForm<K extends keyof WorkOperationFormState>(
    field: K,
    value: WorkOperationFormState[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
    if (
      field === "sourceScopeType" ||
      field === "scopeKey" ||
      field === "collectionId"
    ) {
      setPreview(null);
      setExcludedIds(new Set());
    } else if (field === "houseId") {
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
    const scopePayload = buildScopePayload(form, manualIds);
    if (!scopePayload) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const result = await previewWorkOperationTargets(scopePayload);
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
    const scopePayload = buildScopePayload(form, manualIds);
    if (!scopePayload) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const result = await createWorkOperation({
        workTypeId: pesticideType.id,
        title: form.title.trim(),
        plannedStartDate: form.plannedStartDate,
        plannedEndDate: form.plannedEndDate || null,
        sourceScopeType: scopePayload.scopeType,
        sourceScopeId: scopePayload.scopeId,
        sourceScopeKey: scopePayload.scopeKey,
        sourceOrchidGroupIds: scopePayload.orchidGroupIds,
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

  async function changeOperationStatus(
    action: "start" | "pause" | "resume" | "cancel",
  ) {
    if (!operation) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      setOperation(await transitionWorkOperation(operation.id, action));
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "작업 상태를 변경하지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  async function changeTargetStatus(
    targetId: number,
    action: "start" | "complete" | "skip",
  ) {
    if (!operation) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      setOperation(
        await transitionWorkOperationTarget(
          operation.id,
          targetId,
          action,
          operation.worker,
        ),
      );
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "대상 상태를 변경하지 못했습니다.",
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
            그룹·범위 농약 작업
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
          onOperationAction={changeOperationStatus}
          onTargetAction={changeTargetStatus}
        />
      ) : (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-2 lg:grid-cols-4">
            <SelectField
              label="대상 방식"
              value={form.sourceScopeType}
              onChange={(value) =>
                updateForm(
                  "sourceScopeType",
                  value as WorkOperationFormState["sourceScopeType"],
                )
              }
            >
              <option value="HOUSE">동 전체</option>
              <option value="DERIVED_GROUP">자동 그룹</option>
              <option value="USER_COLLECTION">사용자 그룹</option>
              <option value="MANUAL_SELECTION">직접 다중 선택</option>
            </SelectField>
            {form.sourceScopeType === "HOUSE" ? (
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
            ) : null}
            {form.sourceScopeType === "DERIVED_GROUP" ? (
              <SelectField
                label="자동 그룹"
                value={form.scopeKey}
                onChange={(value) => updateForm("scopeKey", value)}
              >
                <option value="">선택</option>
                {(scopeOptions?.derivedGroups ?? []).map((group) => (
                  <option key={group.groupKey} value={group.groupKey}>
                    {group.varietyName} · {group.ageYear ?? "년생 미지정"} ·{" "}
                    {group.potSize ?? "화분 미지정"} · {group.orchidGroupCount}
                    묶음
                  </option>
                ))}
              </SelectField>
            ) : null}
            {form.sourceScopeType === "USER_COLLECTION" ? (
              <SelectField
                label="사용자 그룹"
                value={form.collectionId}
                onChange={(value) => updateForm("collectionId", value)}
              >
                <option value="">선택</option>
                {(scopeOptions?.collections ?? []).map((collection) => (
                  <option key={collection.id} value={collection.id}>
                    {collection.name} · {collection.orchidGroupCount}묶음 ·{" "}
                    {collection.totalQuantity}분
                  </option>
                ))}
              </SelectField>
            ) : null}
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
          {form.sourceScopeType === "MANUAL_SELECTION" ? (
            <ManualTargetSelector
              groups={manualGroups}
              keyword={manualKeyword}
              selectedIds={manualIds}
              onKeywordChange={setManualKeyword}
              onToggle={(orchidGroupId) => {
                setManualIds((current) => {
                  const next = new Set(current);
                  if (next.has(orchidGroupId)) next.delete(orchidGroupId);
                  else next.add(orchidGroupId);
                  return next;
                });
                setPreview(null);
                setExcludedIds(new Set());
              }}
            />
          ) : null}
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
              disabled={
                loading || optionsLoading || !buildScopePayload(form, manualIds)
              }
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
  onOperationAction,
  onTargetAction,
}: {
  operation: WorkOperation;
  loading: boolean;
  onComplete: () => void;
  onOperationAction: (action: "start" | "pause" | "resume" | "cancel") => void;
  onTargetAction: (
    targetId: number,
    action: "start" | "complete" | "skip",
  ) => void;
}) {
  const completed = operation.status === "COMPLETED";
  const canceled = operation.status === "CANCELED";
  const active = operation.status === "IN_PROGRESS";
  const canComplete =
    active &&
    operation.progress.pending === 0 &&
    operation.progress.inProgress === 0 &&
    operation.progress.failed === 0;
  return (
    <div className="mt-4 rounded-md border border-[#cfe0d2] bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="font-bold text-[#17251b]">{operation.title}</p>
          <p className="mt-1 text-sm text-[#5c6a60]">
            {operation.plannedStartDate}
            {operation.plannedEndDate
              ? ` ~ ${operation.plannedEndDate}`
              : ""} · {operationStatusLabel(operation.status)}
          </p>
        </div>
        {completed || canceled ? (
          <span
            className={`rounded-full px-3 py-1.5 text-sm font-semibold ${completed ? "bg-[#e7f6eb] text-[#10783a]" : "bg-[#f2eeee] text-[#765f5a]"}`}
          >
            {completed ? "완료됨" : "취소됨"}
          </span>
        ) : (
          <div className="flex flex-wrap gap-2">
            {operation.status === "PLANNED" ? (
              <StatusAction
                label="작업 시작"
                disabled={loading}
                onClick={() => onOperationAction("start")}
              />
            ) : null}
            {active ? (
              <StatusAction
                label="일시중지"
                disabled={loading}
                onClick={() => onOperationAction("pause")}
              />
            ) : null}
            {operation.status === "PAUSED" ? (
              <StatusAction
                label="작업 재개"
                disabled={loading}
                onClick={() => onOperationAction("resume")}
              />
            ) : null}
            <StatusAction
              label="전체 완료"
              primary
              disabled={loading || !canComplete}
              onClick={onComplete}
            />
            <StatusAction
              label="취소"
              danger
              disabled={loading}
              onClick={() => onOperationAction("cancel")}
            />
          </div>
        )}
      </div>

      <div className="mt-4 rounded-md bg-[#f4f7f3] p-3">
        <div className="flex items-center justify-between text-sm font-semibold text-[#344138]">
          <span>
            완료 {operation.progress.completed} · 진행{" "}
            {operation.progress.inProgress} · 대기 {operation.progress.pending}{" "}
            · 건너뜀 {operation.progress.skipped}
          </span>
          <span>{operation.progress.progressPercent}%</span>
        </div>
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-[#dce5dc]">
          <div
            className="h-full rounded-full bg-[#159447] transition-all"
            style={{ width: `${operation.progress.progressPercent}%` }}
          />
        </div>
      </div>

      <div className="mt-4 max-h-72 overflow-y-auto rounded-md border border-[#e1e6df]">
        {operation.targets.map((target) => (
          <div
            className="flex flex-wrap items-center gap-2 border-b border-[#edf0ec] px-3 py-2 last:border-b-0"
            key={target.orchidGroupId}
          >
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-[#26352b]">
                {target.varietyName}
              </p>
              <p className="mt-0.5 text-xs text-[#6a766e]">
                {target.locationSnapshot.houseNumber}동{" "}
                {target.locationSnapshot.physicalBedNumber}다이{" "}
                {target.locationSnapshot.bedZoneName} ·{" "}
                {target.quantitySnapshot}분
              </p>
            </div>
            <span className="rounded-full bg-[#eef2ed] px-2 py-1 text-xs font-semibold text-[#526057]">
              {targetStatusLabel(target.executionStatus)}
            </span>
            {active &&
            target.id != null &&
            target.executionStatus === "PENDING" ? (
              <StatusAction
                small
                label="시작"
                disabled={loading}
                onClick={() => onTargetAction(target.id!, "start")}
              />
            ) : null}
            {active &&
            target.id != null &&
            (target.executionStatus === "PENDING" ||
              target.executionStatus === "IN_PROGRESS") ? (
              <>
                <StatusAction
                  small
                  primary
                  label="완료"
                  disabled={loading}
                  onClick={() => onTargetAction(target.id!, "complete")}
                />
                <StatusAction
                  small
                  label="건너뛰기"
                  disabled={loading}
                  onClick={() => onTargetAction(target.id!, "skip")}
                />
              </>
            ) : null}
          </div>
        ))}
      </div>
    </div>
  );
}

function StatusAction({
  label,
  disabled,
  primary = false,
  danger = false,
  small = false,
  onClick,
}: {
  label: string;
  disabled: boolean;
  primary?: boolean;
  danger?: boolean;
  small?: boolean;
  onClick: () => void;
}) {
  const color = primary
    ? "border-[#159447] bg-[#159447] text-white"
    : danger
      ? "border-[#e2b5aa] bg-white text-[#a33a24]"
      : "border-[#cfd8cc] bg-white text-[#34503b]";
  return (
    <button
      className={`rounded-md border font-semibold disabled:opacity-45 ${color} ${small ? "px-2 py-1 text-xs" : "px-3 py-2 text-sm"}`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {label}
    </button>
  );
}

function operationStatusLabel(status: WorkOperation["status"]) {
  return {
    DRAFT: "초안",
    PLANNED: "계획",
    IN_PROGRESS: "진행 중",
    PAUSED: "일시중지",
    COMPLETED: "완료",
    CANCELED: "취소",
    CORRECTED: "보정",
  }[status];
}

function targetStatusLabel(
  status: WorkOperation["targets"][number]["executionStatus"],
) {
  return {
    PENDING: "대기",
    IN_PROGRESS: "진행 중",
    COMPLETED: "완료",
    SKIPPED: "건너뜀",
    CANCELED: "취소",
    FAILED: "실패",
  }[status];
}

function ManualTargetSelector({
  groups,
  keyword,
  selectedIds,
  onKeywordChange,
  onToggle,
}: {
  groups: OrchidGroup[];
  keyword: string;
  selectedIds: Set<number>;
  onKeywordChange: (value: string) => void;
  onToggle: (orchidGroupId: number) => void;
}) {
  return (
    <section className="mt-3 rounded-md border border-[#d7ddd4] bg-white p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-[#344138]">
          직접 선택 {selectedIds.size}묶음
        </p>
        <input
          className="w-64 rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
          placeholder="품종 또는 위치 검색"
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
        />
      </div>
      <div className="mt-2 max-h-48 overflow-y-auto border-t border-[#edf0ec]">
        {groups.map((group) => (
          <label
            className="flex cursor-pointer items-center gap-3 border-b border-[#edf0ec] px-2 py-2 text-sm last:border-b-0"
            key={group.id}
          >
            <input
              checked={selectedIds.has(group.id)}
              className="h-4 w-4 accent-[#159447]"
              type="checkbox"
              onChange={() => onToggle(group.id)}
            />
            <span className="min-w-0 flex-1 truncate font-semibold">
              {group.varietyName}
            </span>
            <span className="text-xs text-[#5c6a60]">
              {group.houseNumber}동 {group.physicalBedNumber}다이{" "}
              {group.bedZoneName}
            </span>
            <span className="w-14 text-right text-xs">{group.quantity}분</span>
          </label>
        ))}
        {groups.length === 0 ? (
          <p className="p-3 text-sm text-[#5c6a60]">검색 결과가 없습니다.</p>
        ) : null}
      </div>
    </section>
  );
}

function buildScopePayload(
  form: WorkOperationFormState,
  manualIds: Set<number>,
): WorkTargetPreviewPayload | null {
  switch (form.sourceScopeType) {
    case "HOUSE":
      return form.houseId
        ? { scopeType: "HOUSE", scopeId: Number(form.houseId) }
        : null;
    case "DERIVED_GROUP":
      return form.scopeKey
        ? { scopeType: "DERIVED_GROUP", scopeKey: form.scopeKey }
        : null;
    case "USER_COLLECTION":
      return form.collectionId
        ? {
            scopeType: "USER_COLLECTION",
            scopeId: Number(form.collectionId),
          }
        : null;
    case "MANUAL_SELECTION":
      return manualIds.size > 0
        ? {
            scopeType: "MANUAL_SELECTION",
            orchidGroupIds: [...manualIds],
          }
        : null;
  }
}
