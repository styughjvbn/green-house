"use client";

import { useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import type {
  BedZone,
  House,
  OrchidGroup,
  WorkOperation,
  WorkOperationTarget,
  WorkTargetPreview,
  WorkType,
} from "@/entities/farm/types";
import {
  getSchedulableWorkTypes,
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import {
  completeWorkOperation,
  createInboundPottingPlan,
  createWorkOperation,
  getInboundPottingCandidates,
  getWorkTargetSelectionOptions,
  previewWorkOperationTargets,
  transitionWorkOperation,
  transitionWorkOperationTarget,
} from "../../api/workRecordApi";
import type {
  InboundPottingCandidate,
  WorkOperationFormState,
  WorkTargetPreviewPayload,
} from "../../model/types";
import { SelectField, TextField } from "./FormFields";
import { WorkTargetSelectionDialog } from "./WorkTargetSelectionDialog";
import { StructureWorkExecutionDialog } from "./StructureWorkExecutionDialog";

export function WorkOperationPanel({
  houses,
  workTypes,
  initialWorkTypeCode,
  onClose,
  onOpenCompletedWork,
  onSaved,
}: {
  houses: House[];
  workTypes: WorkType[];
  initialWorkTypeCode?: string | null;
  onClose: () => void;
  onOpenCompletedWork: () => void;
  onSaved?: () => void;
}) {
  const schedulableWorkTypes = getSchedulableWorkTypes(workTypes);
  const initialWorkType =
    schedulableWorkTypes.find(
      (workType) => workType.code === initialWorkTypeCode,
    ) ?? schedulableWorkTypes[0];
  const [form, setForm] = useState<WorkOperationFormState>(() => ({
    workTypeId: initialWorkType ? String(initialWorkType.id) : "",
    sourceScopeType: "MANUAL_SELECTION",
    houseId: "",
    scopeKey: "",
    collectionId: "",
    title: initialWorkType ? `${initialWorkType.name} 작업` : "기간 작업",
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
  const [executionTarget, setExecutionTarget] =
    useState<WorkOperationTarget | null>(null);
  const [excludedIds, setExcludedIds] = useState<Set<number>>(new Set());
  const [manualIds, setManualIds] = useState<Set<number>>(new Set());
  const [inboundRecordIds, setInboundRecordIds] = useState<Set<number>>(
    new Set(),
  );
  const [targetSelectorOpen, setTargetSelectorOpen] = useState(false);
  const [targetScopeLabel, setTargetScopeLabel] = useState<string | null>(null);
  const [orchidGroups, setOrchidGroups] = useState<OrchidGroup[]>([]);
  const [bedZones, setBedZones] = useState<BedZone[]>([]);
  const [inboundCandidates, setInboundCandidates] = useState<
    InboundPottingCandidate[]
  >([]);
  const [loading, setLoading] = useState(false);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const selectedWorkType = schedulableWorkTypes.find(
    (workType) => String(workType.id) === form.workTypeId,
  );
  const isInboundPotting = selectedWorkType?.code === "POTTING";
  const isDedicatedWorkflow =
    selectedWorkType?.code === "POTTING" ||
    selectedWorkType?.code === "REPOT" ||
    selectedWorkType?.code === "DIVIDE" ||
    selectedWorkType?.code === "MERGE" ||
    selectedWorkType?.code === "DISCARD" ||
    selectedWorkType?.code === "MOVEMENT";

  const includedTargets = useMemo(
    () =>
      preview?.targets.filter(
        (target) =>
          target.orchidGroupId != null &&
          !excludedIds.has(target.orchidGroupId),
      ) ?? [],
    [excludedIds, preview],
  );
  const includedQuantity = includedTargets.reduce(
    (sum, target) => sum + target.quantitySnapshot,
    0,
  );
  const selectedManualGroups = useMemo(
    () => orchidGroups.filter((group) => manualIds.has(group.id)),
    [manualIds, orchidGroups],
  );
  const selectedManualQuantity = selectedManualGroups.reduce(
    (sum, group) => sum + group.quantity,
    0,
  );
  const selectedManualZoneCount = new Set(
    selectedManualGroups.map((group) => group.bedZoneId),
  ).size;
  const saveUnavailableReason = loading
    ? "처리 중입니다."
    : !selectedWorkType
      ? "작업 유형을 선택해주세요."
      : !form.title.trim()
        ? "작업명을 입력해주세요."
        : isInboundPotting
          ? inboundRecordIds.size === 0
            ? "포트 작업할 입고 기록을 선택해주세요."
            : null
          : !preview
            ? "대상 선택 후 실제 대상을 미리보기해주세요."
            : includedTargets.length === 0
              ? "포함할 난 묶음을 하나 이상 선택해주세요."
              : null;

  useEffect(() => {
    let cancelled = false;
    void Promise.all([
      getWorkTargetSelectionOptions(),
      getInboundPottingCandidates(),
    ])
      .then(([result, candidates]) => {
        if (!cancelled) {
          setOrchidGroups(result.orchidGroups);
          setBedZones(result.bedZones);
          setInboundCandidates(candidates);
        }
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

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !targetSelectorOpen && !executionTarget)
        onClose();
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [executionTarget, onClose, targetSelectorOpen]);

  function updateForm<K extends keyof WorkOperationFormState>(
    field: K,
    value: WorkOperationFormState[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
    if (field === "workTypeId") {
      const workType = schedulableWorkTypes.find(
        (candidate) => String(candidate.id) === value,
      );
      setForm((current) => ({
        ...current,
        workTypeId: String(value),
        title: workType ? `${workType.name} 작업` : current.title,
        sourceScopeType:
          workType?.code === "POTTING"
            ? "INBOUND_RECORD_SELECTION"
            : "MANUAL_SELECTION",
      }));
      setPreview(null);
      setExcludedIds(new Set());
    } else if (
      field === "sourceScopeType" ||
      field === "scopeKey" ||
      field === "collectionId"
    ) {
      setPreview(null);
      setExcludedIds(new Set());
    } else if (field === "houseId") {
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
    if (!selectedWorkType) return;
    if (isInboundPotting) {
      if (inboundRecordIds.size === 0) return;
      setLoading(true);
      setErrorMessage(null);
      try {
        const result = await createInboundPottingPlan({
          title: form.title.trim(),
          plannedStartDate: form.plannedStartDate,
          plannedEndDate: form.plannedEndDate || null,
          inboundRecordIds: [...inboundRecordIds],
          worker: form.worker.trim() || null,
          memo: form.memo.trim() || null,
        });
        setOperation(result);
        onSaved?.();
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "작업을 저장하지 못했습니다.",
        );
      } finally {
        setLoading(false);
      }
      return;
    }
    if (!preview || includedTargets.length === 0) return;
    const scopePayload = buildScopePayload(form, manualIds);
    if (!scopePayload) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const result = await createWorkOperation({
        workTypeId: selectedWorkType.id,
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
      onSaved?.();
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
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-6xl flex-col overflow-hidden rounded-md bg-[#f5fbf5] shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="작업 등록"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[#dbe8dc] bg-white p-5">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">작업 관리</p>
            <h2 className="mt-1 text-xl font-semibold text-[#17251b]">
              작업 등록
            </h2>
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

        <div className="min-h-0 overflow-y-auto p-5">
          {!operation ? (
            <div className="grid grid-cols-2 gap-2 rounded-md bg-[#e5eee5] p-1">
              <button
                className="rounded px-3 py-2 text-sm font-semibold text-[#526057] hover:bg-white/70"
                type="button"
                onClick={onOpenCompletedWork}
              >
                완료 작업 기록
              </button>
              <button
                className="rounded bg-white px-3 py-2 text-sm font-semibold text-[#10783a] shadow-sm"
                type="button"
                aria-current="page"
              >
                기간 작업 계획
              </button>
            </div>
          ) : null}
          {errorMessage ? (
            <p className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
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
              onExecuteTarget={setExecutionTarget}
            />
          ) : (
            <>
              <section className="mt-4 grid gap-3 rounded-md border border-[#cfe0d2] bg-white p-4 md:grid-cols-[minmax(220px,0.45fr)_minmax(0,1fr)]">
                <SelectField
                  label="작업 유형"
                  value={form.workTypeId}
                  onChange={(value) => updateForm("workTypeId", value)}
                >
                  {schedulableWorkTypes.map((workType) => (
                    <option key={workType.id} value={workType.id}>
                      {workType.name}
                    </option>
                  ))}
                </SelectField>
                <div className="rounded-md bg-[#f4f8f3] px-4 py-3 text-sm text-[#526057]">
                  <p className="font-semibold text-[#26352b]">
                    {selectedWorkType?.name ?? "작업 유형을 선택하세요"}
                  </p>
                  <p className="mt-1">
                    {workPlanGuidance(selectedWorkType?.code)}
                  </p>
                </div>
              </section>
              <section className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-[#cfe0d2] bg-white p-4">
                <div>
                  <p className="text-sm font-semibold text-[#26352b]">
                    작업 대상
                  </p>
                  <p className="mt-1 text-sm text-[#5c6a60]">
                    {isInboundPotting
                      ? inboundRecordIds.size > 0
                        ? `입고 기록 ${inboundRecordIds.size}건`
                        : "포트 작업할 유리병 모종 입고 기록을 선택하세요."
                      : manualIds.size > 0
                        ? `${targetScopeLabel ? `${targetScopeLabel} · ` : ""}${manualIds.size}묶음 · ${selectedManualQuantity}분 · ${selectedManualZoneCount}개 구역`
                        : "동, 다이, 구역 또는 개별 난 묶음을 선택하세요."}
                  </p>
                </div>
                <button
                  className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a] hover:bg-[#f1faf3]"
                  disabled={optionsLoading}
                  type="button"
                  onClick={() => setTargetSelectorOpen(true)}
                >
                  {(isInboundPotting ? inboundRecordIds.size : manualIds.size) >
                  0
                    ? "대상 변경"
                    : "작업 대상 선택"}
                </button>
              </section>
              <div className="mt-4 grid gap-3 md:grid-cols-3">
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
                {!isDedicatedWorkflow &&
                isVisibleWorkRecordField(
                  selectedWorkType?.template ?? null,
                  "materialName",
                ) ? (
                  <TextField
                    label={getWorkRecordFieldLabel(
                      selectedWorkType?.template ?? null,
                      "materialName",
                    )}
                    value={form.materialName}
                    onChange={(value) => updateForm("materialName", value)}
                  />
                ) : null}
                {!isDedicatedWorkflow &&
                isVisibleWorkRecordField(
                  selectedWorkType?.template ?? null,
                  "dilutionRatio",
                ) ? (
                  <TextField
                    label={getWorkRecordFieldLabel(
                      selectedWorkType?.template ?? null,
                      "dilutionRatio",
                    )}
                    value={form.dilutionRatio}
                    onChange={(value) => updateForm("dilutionRatio", value)}
                  />
                ) : null}
                {!isDedicatedWorkflow &&
                isVisibleWorkRecordField(
                  selectedWorkType?.template ?? null,
                  "quantity",
                ) ? (
                  <TextField
                    label={getWorkRecordFieldLabel(
                      selectedWorkType?.template ?? null,
                      "quantity",
                    )}
                    value={form.quantity}
                    onChange={(value) => updateForm("quantity", value)}
                  />
                ) : null}
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

              {!isInboundPotting ? (
                <div className="mt-4 flex flex-wrap items-center gap-3">
                  <button
                    className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a]"
                    disabled={
                      loading ||
                      optionsLoading ||
                      !buildScopePayload(form, manualIds)
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
              ) : null}

              {!isInboundPotting && preview ? (
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

              <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-[#6a766e]">
                  {saveUnavailableReason ?? "등록할 준비가 되었습니다."}
                </p>
                <button
                  className="rounded-md bg-[#159447] px-5 py-2.5 text-sm font-semibold text-white disabled:bg-[#9bb7a2]"
                  disabled={saveUnavailableReason != null}
                  type="button"
                  onClick={saveOperation}
                >
                  작업 저장
                </button>
              </div>
            </>
          )}
        </div>
      </section>
      {targetSelectorOpen ? (
        isInboundPotting ? (
          <InboundPottingTargetDialog
            candidates={inboundCandidates}
            initialSelectedIds={inboundRecordIds}
            onClose={() => setTargetSelectorOpen(false)}
            onConfirm={(selectedIds) => {
              setInboundRecordIds(selectedIds);
              setTargetSelectorOpen(false);
            }}
          />
        ) : (
          <WorkTargetSelectionDialog
            bedZones={bedZones}
            groups={orchidGroups}
            initialSelectedIds={manualIds}
            onClose={() => setTargetSelectorOpen(false)}
            onConfirm={(selectedIds, scope) => {
              setManualIds(selectedIds);
              setTargetScopeLabel(scope?.label ?? null);
              const scopePayload: WorkTargetPreviewPayload = scope
                ? scope.type === "DERIVED_GROUP"
                  ? { scopeType: "DERIVED_GROUP", scopeKey: scope.scopeKey }
                  : {
                      scopeType: "USER_COLLECTION",
                      scopeId: scope.collectionId,
                    }
                : {
                    scopeType: "MANUAL_SELECTION",
                    orchidGroupIds: [...selectedIds],
                  };
              setForm((current) => ({
                ...current,
                sourceScopeType: scope?.type ?? "MANUAL_SELECTION",
                scopeKey: scope?.type === "DERIVED_GROUP" ? scope.scopeKey : "",
                collectionId:
                  scope?.type === "USER_COLLECTION"
                    ? String(scope.collectionId)
                    : "",
                title:
                  scope && selectedWorkType
                    ? `${scope.label} ${selectedWorkType.name}`
                    : current.title,
              }));
              setPreview(null);
              setExcludedIds(new Set());
              setTargetSelectorOpen(false);
              setLoading(true);
              setErrorMessage(null);
              void previewWorkOperationTargets(scopePayload)
                .then((result) => setPreview(result))
                .catch((cause: unknown) =>
                  setErrorMessage(
                    cause instanceof Error
                      ? cause.message
                      : "작업 대상을 확인하지 못했습니다.",
                  ),
                )
                .finally(() => setLoading(false));
            }}
          />
        )
      ) : null}
      {operation && executionTarget ? (
        <StructureWorkExecutionDialog
          bedZones={bedZones}
          houses={houses}
          operation={operation}
          orchidGroups={orchidGroups}
          source={
            executionTarget.orchidGroupId == null
              ? null
              : (orchidGroups.find(
                  (group) => group.id === executionTarget.orchidGroupId,
                ) ?? null)
          }
          target={executionTarget}
          onClose={() => setExecutionTarget(null)}
          onSaved={(updated) => {
            setOperation(updated);
            setExecutionTarget(null);
            onSaved?.();
          }}
        />
      ) : null}
    </div>
  );
}

function InboundPottingTargetDialog({
  candidates,
  initialSelectedIds,
  onClose,
  onConfirm,
}: {
  candidates: InboundPottingCandidate[];
  initialSelectedIds: Set<number>;
  onClose: () => void;
  onConfirm: (selectedIds: Set<number>) => void;
}) {
  const [selectedIds, setSelectedIds] = useState(
    () => new Set(initialSelectedIds),
  );
  const [keyword, setKeyword] = useState("");
  const visible = candidates.filter((candidate) =>
    `${candidate.varietyName} ${candidate.tempLocation ?? ""}`
      .toLowerCase()
      .includes(keyword.trim().toLowerCase()),
  );
  return (
    <div
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={(event) => {
        event.stopPropagation();
        onClose();
      }}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-2xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="포트 작업 대상 선택"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">포트 작업 대상 선택</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              임시 보관 또는 포트 작업 대기 중인 유리병 모종입니다.
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <div className="border-b p-4">
          <input
            autoFocus
            className="w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
            placeholder="품종 또는 임시 위치 검색"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          <div className="overflow-hidden rounded-md border">
            {visible.map((candidate) => (
              <label
                className="flex cursor-pointer items-center gap-3 border-b px-3 py-3 text-sm last:border-b-0 hover:bg-[#f4f8f3]"
                key={candidate.id}
              >
                <input
                  checked={selectedIds.has(candidate.id)}
                  className="h-4 w-4 accent-[#159447]"
                  type="checkbox"
                  onChange={() =>
                    setSelectedIds((current) => {
                      const next = new Set(current);
                      if (next.has(candidate.id)) next.delete(candidate.id);
                      else next.add(candidate.id);
                      return next;
                    })
                  }
                />
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold">
                    {candidate.varietyName}
                  </span>
                  <span className="block text-xs text-[#6a766e]">
                    {candidate.tempLocation ?? "임시 위치 미지정"} · 예상{" "}
                    {candidate.estimatedQuantity ?? "-"}분
                  </span>
                </span>
                <span className="text-xs text-[#5c6a60]">
                  예정 {candidate.pottingDueDate ?? "미지정"}
                </span>
              </label>
            ))}
            {visible.length === 0 ? (
              <p className="p-8 text-center text-sm text-[#6a766e]">
                선택할 수 있는 입고 기록이 없습니다.
              </p>
            ) : null}
          </div>
        </div>
        <footer className="flex items-center justify-between border-t p-4">
          <span className="text-sm font-semibold">
            {selectedIds.size}건 선택
          </span>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
            disabled={selectedIds.size === 0}
            type="button"
            onClick={() => onConfirm(new Set(selectedIds))}
          >
            대상 확정
          </button>
        </footer>
      </section>
    </div>
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
          key={target.orchidGroupId ?? target.id}
          className="flex cursor-pointer items-center gap-3 border-b border-[#edf0ec] px-3 py-2 text-sm last:border-b-0"
        >
          <input
            checked={
              target.orchidGroupId != null &&
              !excludedIds.has(target.orchidGroupId)
            }
            type="checkbox"
            onChange={() => {
              if (target.orchidGroupId != null) onToggle(target.orchidGroupId);
            }}
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

export function OperationResult({
  operation,
  loading,
  onComplete,
  onOperationAction,
  onTargetAction,
  onExecuteTarget,
}: {
  operation: WorkOperation;
  loading: boolean;
  onComplete: () => void;
  onOperationAction: (action: "start" | "pause" | "resume" | "cancel") => void;
  onTargetAction: (
    targetId: number,
    action: "start" | "complete" | "skip",
  ) => void;
  onExecuteTarget?: (target: WorkOperation["targets"][number]) => void;
}) {
  const completed = operation.status === "COMPLETED";
  const canceled = operation.status === "CANCELED";
  const corrected = operation.status === "CORRECTED";
  const terminal = completed || canceled || corrected;
  const active = operation.status === "IN_PROGRESS";
  const canComplete =
    active &&
    operation.progress.pending === 0 &&
    operation.progress.inProgress === 0 &&
    operation.progress.partial === 0 &&
    operation.progress.failed === 0;
  const structureChange = ["REPOT", "DIVIDE", "MERGE"].includes(
    operation.workTypeCode,
  );
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
        {terminal ? (
          <span
            className={`rounded-full px-3 py-1.5 text-sm font-semibold ${completed || corrected ? "bg-[#e7f6eb] text-[#10783a]" : "bg-[#f2eeee] text-[#765f5a]"}`}
          >
            {corrected ? "보정됨" : completed ? "완료됨" : "취소됨"}
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
            {active && structureChange ? (
              <StatusAction
                label={`${operation.workType} 실행 등록`}
                primary
                disabled={loading || !onExecuteTarget}
                onClick={() => {
                  const firstTarget = operation.targets.find(
                    (target) => target.remainingQuantity > 0,
                  );
                  if (firstTarget) onExecuteTarget?.(firstTarget);
                }}
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
            {operation.progress.inProgress} · 부분 {operation.progress.partial}{" "}
            · 대기 {operation.progress.pending} · 건너뜀{" "}
            {operation.progress.skipped}
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
            key={
              target.id ??
              `${target.targetReferenceType}-${target.orchidGroupId ?? target.inboundRecordId}`
            }
          >
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-[#26352b]">
                {target.varietyName}
              </p>
              <p className="mt-0.5 text-xs text-[#6a766e]">
                {target.targetReferenceType === "INBOUND_RECORD"
                  ? `${target.locationSnapshot.tempLocation ?? "임시 위치 미지정"} · 입고 #${target.inboundRecordId}`
                  : `${target.locationSnapshot.houseNumber}동 ${target.locationSnapshot.physicalBedNumber}다이 ${target.locationSnapshot.bedZoneName}`}{" "}
                · 계획 {target.quantitySnapshot}분
                {operation.workTypeCode === "DISCARD" &&
                typeof target.resultDetails?.discardedQuantity === "number"
                  ? ` · 폐기 ${target.resultDetails.discardedQuantity}분 · 현재 ${target.resultDetails.remainingQuantity}분`
                  : target.processedQuantity > 0
                    ? ` · 작업 ${target.processedQuantity}분 · 잔여 ${target.remainingQuantity}분`
                    : ""}
              </p>
            </div>
            <span className="rounded-full bg-[#eef2ed] px-2 py-1 text-xs font-semibold text-[#526057]">
              {targetStatusLabel(target.executionStatus)}
            </span>
            {structureChange &&
            active &&
            target.id != null &&
            (target.executionStatus === "PENDING" ||
              target.executionStatus === "PARTIALLY_COMPLETED") ? (
              <StatusAction
                small
                label={
                  target.executionStatus === "PARTIALLY_COMPLETED"
                    ? "잔여 제외"
                    : "건너뛰기"
                }
                disabled={loading}
                onClick={() => onTargetAction(target.id!, "skip")}
              />
            ) : null}
            {!structureChange &&
            active &&
            target.id != null &&
            target.executionStatus === "PENDING" ? (
              <StatusAction
                small
                label="시작"
                disabled={loading}
                onClick={() => onTargetAction(target.id!, "start")}
              />
            ) : null}
            {!structureChange &&
            active &&
            target.id != null &&
            (target.executionStatus === "PENDING" ||
              target.executionStatus === "IN_PROGRESS") ? (
              <>
                <StatusAction
                  small
                  primary
                  label={
                    operation.workTypeCode === "REPOT" ||
                    operation.workTypeCode === "DIVIDE" ||
                    operation.workTypeCode === "POTTING" ||
                    operation.workTypeCode === "DISCARD" ||
                    operation.workTypeCode === "MOVEMENT"
                      ? "실행 입력"
                      : "완료"
                  }
                  disabled={
                    loading ||
                    ((operation.workTypeCode === "REPOT" ||
                      operation.workTypeCode === "DIVIDE" ||
                      operation.workTypeCode === "POTTING" ||
                      operation.workTypeCode === "DISCARD" ||
                      operation.workTypeCode === "MOVEMENT") &&
                      !onExecuteTarget)
                  }
                  onClick={() => {
                    if (
                      operation.workTypeCode === "REPOT" ||
                      operation.workTypeCode === "DIVIDE" ||
                      operation.workTypeCode === "POTTING" ||
                      operation.workTypeCode === "DISCARD" ||
                      operation.workTypeCode === "MOVEMENT"
                    ) {
                      onExecuteTarget?.(target);
                    } else {
                      onTargetAction(target.id!, "complete");
                    }
                  }}
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

function workPlanGuidance(code?: string) {
  switch (code) {
    case "MOVEMENT":
      return "원본 난 묶음을 계획 대상으로 확정하고, 실행할 때 각 묶음의 목적 구역과 위치를 입력합니다.";
    case "REPOT":
      return "보통 자동·사용자 그룹 하나를 대상으로 정하고, 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.";
    case "DIVIDE":
      return "대상 그룹을 정한 뒤 실행 회차마다 원본별 수량과 여러 결과 묶음을 기록합니다.";
    case "MERGE":
      return "같은 품종의 대상 그룹을 정하고, 실행 회차마다 원본과 여러 결과 묶음을 기록합니다.";
    case "POTTING":
      return "포트 작업 대기 입고 기록을 선택하고, 실행할 때 실제 수량과 배치 위치를 입력합니다.";
    case "DISCARD":
      return "폐기할 난 묶음을 대상으로 정하고, 실행할 때 대상별 일부 또는 전량 폐기 수량과 사유를 입력합니다.";
    default:
      return "난 묶음을 계획 대상으로 확정하고, 작업 유형에 맞는 기록 내용을 저장합니다.";
  }
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
    PARTIALLY_COMPLETED: "부분 완료",
    COMPLETED: "완료",
    SKIPPED: "건너뜀",
    CANCELED: "취소",
    FAILED: "실패",
  }[status];
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
    case "INBOUND_RECORD_SELECTION":
      return null;
  }
}
