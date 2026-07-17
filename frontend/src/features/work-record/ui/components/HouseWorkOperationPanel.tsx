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
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import {
  completeWorkOperation,
  createCompletedWorkOperationFromRecord,
  createInboundPottingPlan,
  createWorkOperation,
  getInboundPottingCandidates,
  getWorkTargetSelectionOptions,
  previewWorkOperationTargets,
  transitionWorkOperation,
  transitionWorkOperationTarget,
} from "../../api/workRecordApi";
import type {
  CreateWorkRecordPayload,
  InboundPottingCandidate,
  WorkOperationFormState,
  WorkTargetPreviewPayload,
} from "../../model/types";
import { InboundPottingTargetDialog } from "./InboundPottingTargetDialog";
import { WorkOperationPlanForm } from "./WorkOperationPlanForm";
import { OperationResult } from "./WorkOperationResult";
import { WorkTargetSelectionDialog } from "./WorkTargetSelectionDialog";
import { StructureWorkExecutionDialog } from "./StructureWorkExecutionDialog";
import {
  buildScopePayload,
  groupInboundTargetsByVariety,
  groupOrchidTargetsByVariety,
  requiresSingleVarietyWork,
  varietyWorkTitle,
} from "./workOperationPanelUtils";

export function WorkOperationPanel({
  houses,
  workTypes,
  initialWorkTypeCode,
  onClose,
  onSaved,
}: {
  houses: House[];
  workTypes: WorkType[];
  initialWorkTypeCode?: string | null;
  onClose: () => void;
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
    plannedStartDate: formatLocalDate(new Date()),
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
  const [registrationMode, setRegistrationMode] = useState<"RECORD" | "PLAN">(
    "PLAN",
  );
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
  const recordDisabled =
    selectedWorkType?.code === "POTTING" ||
    selectedWorkType?.code === "REPOT" ||
    selectedWorkType?.code === "DIVIDE" ||
    selectedWorkType?.code === "MERGE" ||
    selectedWorkType?.code === "DISCARD";

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
          : registrationMode === "PLAN" && !preview
            ? "대상 선택 후 실제 대상을 미리보기해주세요."
            : registrationMode === "PLAN" && includedTargets.length === 0
              ? "포함할 난 묶음을 하나 이상 선택해주세요."
              : registrationMode === "RECORD" && manualIds.size === 0
                ? "기록할 난 묶음을 하나 이상 선택해주세요."
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
      const nextRecordDisabled =
        workType?.code === "POTTING" ||
        workType?.code === "REPOT" ||
        workType?.code === "DIVIDE" ||
        workType?.code === "MERGE" ||
        workType?.code === "DISCARD";
      if (nextRecordDisabled) {
        setRegistrationMode("PLAN");
      }
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
    if (registrationMode === "RECORD") {
      if (manualIds.size === 0 || recordDisabled) return;
      setLoading(true);
      setErrorMessage(null);
      try {
        await createCompletedWorkOperationFromRecord(
          recordPayload(form, manualIds, selectedWorkType),
          selectedWorkType.name,
          form.title,
        );
        onSaved?.();
        onClose();
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "작업 기록을 저장하지 못했습니다.",
        );
      } finally {
        setLoading(false);
      }
      return;
    }
    if (isInboundPotting) {
      if (inboundRecordIds.size === 0) return;
      setLoading(true);
      setErrorMessage(null);
      try {
        const varietyGroups = groupInboundTargetsByVariety(
          inboundRecordIds,
          inboundCandidates,
        );
        await Promise.all(
          varietyGroups.map((group) =>
            createInboundPottingPlan({
              title: varietyWorkTitle(
                form.title.trim(),
                group.varietyName,
                varietyGroups.length,
              ),
              plannedStartDate: form.plannedStartDate,
              plannedEndDate: form.plannedEndDate || null,
              inboundRecordIds: group.targetIds,
              worker: form.worker.trim() || null,
              memo: form.memo.trim() || null,
            }),
          ),
        );
        onSaved?.();
        onClose();
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
      const varietyGroups = requiresSingleVarietyWork(selectedWorkType.code)
        ? groupOrchidTargetsByVariety(includedTargets, orchidGroups)
        : [];
      const groupsToCreate =
        varietyGroups.length > 0
          ? varietyGroups
          : [
              {
                varietyKey: "all",
                varietyName: "",
                targetIds: includedTargets.flatMap((target) =>
                  target.orchidGroupId == null ? [] : [target.orchidGroupId],
                ),
              },
            ];
      await Promise.all(
        groupsToCreate.map((group) => {
          const groupTargetIds = new Set(group.targetIds);
          const excludedForGroup = preview.targets.flatMap((target) =>
            target.orchidGroupId != null &&
            !groupTargetIds.has(target.orchidGroupId)
              ? [target.orchidGroupId]
              : [],
          );
          return createWorkOperation({
            workTypeId: selectedWorkType.id,
            title: varietyWorkTitle(
              form.title.trim(),
              group.varietyName,
              groupsToCreate.length,
            ),
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
            excludedOrchidGroupIds: excludedForGroup,
          });
        }),
      );
      onSaved?.();
      onClose();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "작업을 저장하지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  async function completeOperation(completedDate: string) {
    if (!operation) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      setOperation(await completeWorkOperation(operation.id, completedDate));
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
    completedDate?: string,
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
          undefined,
          completedDate,
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
              targetSummary={targetSummary({
                isInboundPotting,
                inboundRecordIds,
                inboundCandidates,
                manualIds,
                targetScopeLabel,
                selectedManualGroups,
                preview,
                excludedIds,
              })}
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
              registrationMode={registrationMode}
              recordDisabled={recordDisabled}
              onChangeRegistrationMode={setRegistrationMode}
              onCancel={onClose}
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

function targetSummary({
  isInboundPotting,
  inboundRecordIds,
  inboundCandidates,
  manualIds,
  targetScopeLabel,
  selectedManualGroups,
  preview,
  excludedIds,
}: {
  isInboundPotting: boolean;
  inboundRecordIds: Set<number>;
  inboundCandidates: InboundPottingCandidate[];
  manualIds: Set<number>;
  targetScopeLabel: string | null;
  selectedManualGroups: OrchidGroup[];
  preview: WorkTargetPreview | null;
  excludedIds: Set<number>;
}) {
  if (isInboundPotting) {
    const selected = inboundCandidates.filter((candidate) =>
      inboundRecordIds.has(candidate.id),
    );
    const quantity = selected.reduce(
      (sum, item) => sum + (item.actualQuantity ?? item.estimatedQuantity ?? 0),
      0,
    );
    return {
      title:
        selected.length > 0
          ? `입고 포트 대상 · ${selected[0]?.varietyName ?? "품종 미지정"}`
          : "포트 작업할 유리병 모종 입고 기록을 선택하세요.",
      metrics:
        selected.length > 0
          ? `입고 기록 ${selected.length}건 · 총 ${quantity.toLocaleString()}개`
          : "대상 선택이 필요합니다.",
      location: selected
        .map((item) => item.tempLocation)
        .filter(Boolean)
        .slice(0, 3)
        .join(" / "),
    };
  }

  if (manualIds.size === 0) {
    return {
      title: "동, 다이, 구역 또는 개별 난 묶음을 선택하세요.",
      metrics: "대상 선택이 필요합니다.",
      location: "",
    };
  }

  const includedTargets =
    preview?.targets.filter(
      (target) =>
        target.orchidGroupId != null && !excludedIds.has(target.orchidGroupId),
    ) ?? [];
  const groups =
    includedTargets.length > 0
      ? selectedManualGroups.filter((group) =>
          includedTargets.some((target) => target.orchidGroupId === group.id),
        )
      : selectedManualGroups;
  const houseCounts = [...groupByHouse(groups).entries()]
    .sort(([left], [right]) => left - right)
    .map(([houseNumber, count]) => `${houseNumber}동 ${count}개`)
    .join(" / ");
  const totalQuantity = groups.reduce((sum, group) => sum + group.quantity, 0);
  const zoneCount = new Set(groups.map((group) => group.bedZoneId)).size;
  const first = groups[0] ?? selectedManualGroups[0];
  return {
    title: targetScopeLabel
      ? `${targetScopeLabel}`
      : first
        ? `${first.varietyName} · ${first.ageYear ?? "-"}년생 · ${first.potSize ?? "-"}`
        : "직접 선택",
    metrics: `난 묶음 ${groups.length}개 · 총 ${totalQuantity.toLocaleString()}분 · ${zoneCount}개 구역`,
    location: houseCounts,
  };
}

function groupByHouse(groups: OrchidGroup[]) {
  const counts = new Map<number, number>();
  groups.forEach((group) => {
    counts.set(group.houseNumber, (counts.get(group.houseNumber) ?? 0) + 1);
  });
  return counts;
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function recordPayload(
  form: WorkOperationFormState,
  manualIds: Set<number>,
  selectedWorkType: WorkType,
): CreateWorkRecordPayload {
  const template = selectedWorkType.template;
  return {
    workTypeId: selectedWorkType.id,
    workDate: form.plannedStartDate,
    targetType: "ORCHID_GROUP",
    targetId: null,
    orchidGroupIds: [...manualIds],
    materialName: isVisibleWorkRecordField(template, "materialName")
      ? form.materialName.trim() || null
      : null,
    dilutionRatio: isVisibleWorkRecordField(template, "dilutionRatio")
      ? form.dilutionRatio.trim() || null
      : null,
    quantity: isVisibleWorkRecordField(template, "quantity")
      ? form.quantity.trim() || null
      : null,
    worker: isVisibleWorkRecordField(template, "worker")
      ? form.worker.trim() || null
      : null,
    memo: isVisibleWorkRecordField(template, "memo")
      ? form.memo.trim() || null
      : null,
  };
}
