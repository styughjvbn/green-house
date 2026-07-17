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
import { getSchedulableWorkTypes } from "@/entities/farm/workTypes";
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
import { InboundPottingTargetDialog } from "./InboundPottingTargetDialog";
import { WorkOperationPlanForm } from "./WorkOperationPlanForm";
import { OperationResult } from "./WorkOperationResult";
import { WorkTargetSelectionDialog } from "./WorkTargetSelectionDialog";
import { StructureWorkExecutionDialog } from "./StructureWorkExecutionDialog";
import { buildScopePayload } from "./workOperationPanelUtils";

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
            <WorkOperationPlanForm
              form={form}
              workTypes={schedulableWorkTypes}
              selectedWorkType={selectedWorkType}
              isInboundPotting={isInboundPotting}
              isDedicatedWorkflow={isDedicatedWorkflow}
              targetSummary={
                isInboundPotting
                  ? inboundRecordIds.size > 0
                    ? `입고 기록 ${inboundRecordIds.size}건`
                    : "포트 작업할 유리병 모종 입고 기록을 선택하세요."
                  : manualIds.size > 0
                    ? `${targetScopeLabel ? `${targetScopeLabel} · ` : ""}${manualIds.size}묶음 · ${selectedManualQuantity}분 · ${selectedManualZoneCount}개 구역`
                    : "동, 다이, 구역 또는 개별 난 묶음을 선택하세요."
              }
              targetCount={
                isInboundPotting ? inboundRecordIds.size : manualIds.size
              }
              optionsLoading={optionsLoading}
              loading={loading}
              canPreview={buildScopePayload(form, manualIds) != null}
              preview={preview}
              excludedIds={excludedIds}
              includedTargetCount={includedTargets.length}
              includedQuantity={includedQuantity}
              saveUnavailableReason={saveUnavailableReason}
              onOpenCompletedWork={onOpenCompletedWork}
              onUpdateForm={updateForm}
              onOpenTargetSelector={() => setTargetSelectorOpen(true)}
              onLoadPreview={loadPreview}
              onToggleExcluded={(id) =>
                setExcludedIds((current) => {
                  const next = new Set(current);
                  if (next.has(id)) next.delete(id);
                  else next.add(id);
                  return next;
                })
              }
              onSave={saveOperation}
            />
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
