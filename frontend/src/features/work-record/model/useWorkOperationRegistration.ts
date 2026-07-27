"use client";

import { useEffect, useMemo, useState } from "react";
import type { House, WorkTargetPreview, WorkType } from "@/entities/farm/types";
import { getSchedulableWorkTypes } from "@/entities/farm/workTypes";
import {
  createCompletedWorkOperation,
  createInboundPottingPlansBatch,
  createWorkOperationsBatch,
  getInboundPottingCandidates,
  previewWorkOperationTargets,
} from "../api/workRecordApi";
import type {
  InboundPottingCandidate,
  WorkOperationFormState,
  WorkTargetSelectionScope,
  WorkTargetPreviewPayload,
} from "./types";
import {
  buildCompletedRecordPayload,
  buildTargetSummary,
  buildWorkTargetScopePayload,
  countAutoSplitWorks,
  createInitialWorkOperationForm,
  getSaveUnavailableReason,
  type WorkRegistrationMode,
} from "./workOperationRegistration";
import {
  getIncludedTargets,
  getRecordTargetIds,
} from "./registrationTargetSelection";
import { getWorkTypeDefinition } from "./workTypeDefinition";
import { deriveWorkTargetSelectionOptions } from "./workTargetSelectionOptions";

export function useWorkOperationRegistration({
  houses,
  onClose,
  onSaved,
  workTypes,
}: {
  houses: House[];
  onClose: () => void;
  onSaved?: () => void;
  workTypes: WorkType[];
}) {
  const schedulableWorkTypes = getSchedulableWorkTypes(workTypes).filter(
    (workType) => getWorkTypeDefinition(workType).category != null,
  );
  const initialWorkType = schedulableWorkTypes[0];
  const [form, setForm] = useState<WorkOperationFormState>(() =>
    createInitialWorkOperationForm(initialWorkType),
  );
  const [preview, setPreview] = useState<WorkTargetPreview | null>(null);
  const [excludedIds, setExcludedIds] = useState<Set<number>>(new Set());
  const [manualIds, setManualIds] = useState<Set<number>>(new Set());
  const [registrationMode, setRegistrationMode] =
    useState<WorkRegistrationMode>("RECORD");
  const [inboundRecordIds, setInboundRecordIds] = useState<Set<number>>(
    new Set(),
  );
  const [targetSelectorOpen, setTargetSelectorOpen] = useState(false);
  const [recordResultOpen, setRecordResultOpen] = useState(false);
  const [targetScopeLabel, setTargetScopeLabel] = useState<string | null>(null);
  const { bedZones, orchidGroups } = useMemo(
    () => deriveWorkTargetSelectionOptions(houses),
    [houses],
  );
  const [inboundCandidates, setInboundCandidates] = useState<
    InboundPottingCandidate[]
  >([]);
  const [inboundCandidatesAttempted, setInboundCandidatesAttempted] =
    useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const selectedWorkType = schedulableWorkTypes.find(
    (workType) => String(workType.id) === form.workTypeId,
  );
  const workTypeDefinition = getWorkTypeDefinition(selectedWorkType);
  const isInboundPotting = workTypeDefinition.targetSource === "INBOUND_RECORD";
  const isDedicatedWorkflow = workTypeDefinition.workflow !== "GENERIC";
  const includedTargets = useMemo(
    () => getIncludedTargets(preview, excludedIds),
    [excludedIds, preview],
  );
  const includedQuantity = includedTargets.reduce(
    (sum, target) => sum + target.quantitySnapshot,
    0,
  );
  const recordTargetIds = getRecordTargetIds(preview, excludedIds, manualIds);
  const autoSplitWorkCount = countAutoSplitWorks({
    inboundCandidates,
    inboundRecordIds,
    includedTargets,
    isInboundPotting,
    selectedWorkType,
  });
  const saveUnavailableReason = getSaveUnavailableReason({
    form,
    inboundRecordIds,
    includedTargets,
    isInboundPotting,
    loading,
    preview,
    recordTargetIds,
    registrationMode,
    selectedWorkType,
  });
  const targetSummary = buildTargetSummary({
    excludedIds,
    inboundCandidates,
    inboundRecordIds,
    isInboundPotting,
    manualIds,
    orchidGroups,
    preview,
    targetScopeLabel,
  });

  useEffect(() => {
    if (!isInboundPotting || inboundCandidatesAttempted) return;
    let cancelled = false;
    void getInboundPottingCandidates()
      .then((candidates) => {
        if (!cancelled) {
          setInboundCandidates(candidates);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "포트 작업 대상 목록을 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setInboundCandidatesAttempted(true);
      });
    return () => {
      cancelled = true;
    };
  }, [inboundCandidatesAttempted, isInboundPotting]);

  function updateForm<K extends keyof WorkOperationFormState>(
    field: K,
    value: WorkOperationFormState[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
    if (field === "workTypeId") {
      const workType = schedulableWorkTypes.find(
        (candidate) => String(candidate.id) === value,
      );
      const definition = getWorkTypeDefinition(workType);
      setForm((current) => ({
        ...current,
        workTypeId: String(value),
        title: workType ? `${workType.name} 작업` : current.title,
        sourceScopeType:
          definition.targetSource === "INBOUND_RECORD"
            ? "INBOUND_RECORD_SELECTION"
            : "MANUAL_SELECTION",
      }));
      setPreview(null);
      setExcludedIds(new Set());
      setManualIds(new Set());
      setInboundRecordIds(new Set());
      setTargetScopeLabel(null);
      setRegistrationMode(
        definition.recordSupported && definition.category != null
          ? "RECORD"
          : "PLAN",
      );
    } else if (
      field === "sourceScopeType" ||
      field === "scopeKey" ||
      field === "collectionId"
    ) {
      setPreview(null);
      setExcludedIds(new Set());
    }
  }

  async function loadPreview() {
    const scopePayload = buildWorkTargetScopePayload(form, manualIds);
    if (!scopePayload) return;
    await loadTargetPreview(scopePayload, "대상을 확인하지 못했습니다.");
  }

  async function selectFarmTarget() {
    setForm((current) => ({
      ...current,
      sourceScopeType: "FARM",
      scopeKey: "",
      collectionId: "",
    }));
    setManualIds(new Set());
    setTargetScopeLabel("농장 전체");
    setExcludedIds(new Set());
    await loadTargetPreview(
      { scopeType: "FARM" },
      "농장 전체 대상을 확인하지 못했습니다.",
    );
  }

  function confirmInboundTargets(selectedIds: Set<number>) {
    setInboundRecordIds(selectedIds);
    setTargetSelectorOpen(false);
  }

  function confirmManualTargets(
    selectedIds: Set<number>,
    scope: WorkTargetSelectionScope | null,
  ) {
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
        scope?.type === "USER_COLLECTION" ? String(scope.collectionId) : "",
      title:
        scope && selectedWorkType
          ? `${scope.label} ${selectedWorkType.name}`
          : current.title,
    }));
    setPreview(null);
    setExcludedIds(new Set());
    setTargetSelectorOpen(false);
    void loadTargetPreview(scopePayload, "작업 대상을 확인하지 못했습니다.");
  }

  function toggleExcluded(id: number) {
    setExcludedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function saveOperation() {
    if (!selectedWorkType) return;
    if (registrationMode === "RECORD") {
      if (!isInboundPotting && recordTargetIds.length === 0) return;
      if (workTypeDefinition.recordResult) {
        setRecordResultOpen(true);
        return;
      }
      await runSave(
        () =>
          createCompletedWorkOperation(
            buildCompletedRecordPayload(
              form,
              recordTargetIds,
              selectedWorkType,
            ),
            selectedWorkType.name,
            form.title,
          ),
        "작업 기록을 저장하지 못했습니다.",
      );
      return;
    }
    if (isInboundPotting) {
      if (inboundRecordIds.size === 0) return;
      await runSave(
        () =>
          createInboundPottingPlansBatch({
            title: form.title.trim(),
            plannedStartDate: form.plannedStartDate,
            plannedEndDate: form.plannedEndDate || null,
            inboundRecordIds: [...inboundRecordIds],
            worker: form.worker.trim() || null,
            memo: form.memo.trim() || null,
          }),
        "작업을 저장하지 못했습니다.",
      );
      return;
    }
    if (!preview || includedTargets.length === 0) return;
    const scopePayload = buildWorkTargetScopePayload(form, manualIds);
    if (!scopePayload) return;
    await runSave(
      () =>
        createWorkOperationsBatch({
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
          excludedOrchidGroupIds: preview.targets.flatMap((target) =>
            target.orchidGroupId != null &&
            excludedIds.has(target.orchidGroupId)
              ? [target.orchidGroupId]
              : [],
          ),
        }),
      "작업을 저장하지 못했습니다.",
    );
  }

  async function loadTargetPreview(
    payload: WorkTargetPreviewPayload,
    fallbackMessage: string,
  ) {
    setLoading(true);
    setErrorMessage(null);
    try {
      setPreview(await previewWorkOperationTargets(payload));
      setExcludedIds(new Set());
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : fallbackMessage);
    } finally {
      setLoading(false);
    }
  }

  async function runSave(
    save: () => Promise<unknown>,
    fallbackMessage: string,
  ) {
    setLoading(true);
    setErrorMessage(null);
    try {
      await save();
      onSaved?.();
      onClose();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : fallbackMessage);
    } finally {
      setLoading(false);
    }
  }

  return {
    autoSplitWorkCount,
    bedZones,
    canPreview: buildWorkTargetScopePayload(form, manualIds) != null,
    closeRecordResult: () => setRecordResultOpen(false),
    closeTargetSelector: () => setTargetSelectorOpen(false),
    confirmInboundTargets,
    confirmManualTargets,
    errorMessage,
    excludedIds,
    form,
    inboundCandidates,
    inboundRecordIds,
    includedQuantity,
    includedTargets,
    isDedicatedWorkflow,
    isInboundPotting,
    loading,
    manualIds,
    openTargetSelector: () => setTargetSelectorOpen(true),
    optionsLoading: isInboundPotting && !inboundCandidatesAttempted,
    orchidGroups,
    preview,
    recordResultKind: workTypeDefinition.recordResult,
    recordResultOpen,
    recordSaved: () => {
      onSaved?.();
      onClose();
    },
    recordTargetIds,
    registrationMode,
    saveOperation,
    saveUnavailableReason,
    schedulableWorkTypes,
    selectFarmTarget,
    selectedWorkType,
    setRegistrationMode,
    targetSelectorOpen,
    targetSummary,
    toggleExcluded,
    updateForm,
    loadPreview,
  };
}
