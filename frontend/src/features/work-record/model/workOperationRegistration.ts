import type {
  OrchidGroup,
  WorkOperationTarget,
  WorkTargetPreview,
  WorkType,
} from "@/entities/farm/types";
import { isVisibleWorkRecordField } from "@/entities/farm/workTypes";
import type {
  CompletedWorkOperationPayload,
  InboundPottingCandidate,
  WorkOperationFormState,
  WorkTargetPreviewPayload,
} from "./types";
import { getWorkTypeDefinition } from "./workTypeDefinition";
import { getIncludedTargets } from "./registrationTargetSelection";

export type WorkRegistrationMode = "RECORD" | "PLAN";

type WorkTargetSummary = {
  title: string;
  metrics: string;
  location: string;
};

export function createInitialWorkOperationForm(
  workType?: WorkType,
): WorkOperationFormState {
  return {
    workTypeId: workType ? String(workType.id) : "",
    sourceScopeType: "MANUAL_SELECTION",
    scopeKey: "",
    collectionId: "",
    title: workType ? `${workType.name} 작업` : "기간 작업",
    plannedStartDate: formatLocalDate(new Date()),
    plannedEndDate: "",
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    worker: "",
    memo: "",
  };
}

export function getSaveUnavailableReason({
  form,
  inboundRecordIds,
  includedTargets,
  isInboundPotting,
  loading,
  preview,
  recordTargetIds,
  registrationMode,
  selectedWorkType,
}: {
  form: WorkOperationFormState;
  inboundRecordIds: Set<number>;
  includedTargets: WorkOperationTarget[];
  isInboundPotting: boolean;
  loading: boolean;
  preview: WorkTargetPreview | null;
  recordTargetIds: number[];
  registrationMode: WorkRegistrationMode;
  selectedWorkType?: WorkType;
}) {
  if (loading) return "처리 중입니다.";
  if (!selectedWorkType) return "작업 유형을 선택해주세요.";
  const definition = getWorkTypeDefinition(selectedWorkType);
  if (registrationMode === "RECORD" && !definition.recordSupported)
    return "이 작업 유형은 작업 기록을 지원하지 않습니다.";
  if (registrationMode === "PLAN" && !definition.planSupported)
    return "이 작업 유형은 작업 계획을 지원하지 않습니다.";
  if (!form.title.trim()) return "작업명을 입력해주세요.";
  if (!form.plannedStartDate) return "작업일 또는 시작일을 입력해주세요.";
  if (isInboundPotting) {
    return inboundRecordIds.size === 0
      ? "포트 작업할 입고 기록을 선택해주세요."
      : null;
  }
  if (registrationMode === "PLAN" && !preview)
    return "대상 선택 후 실제 대상을 미리보기해주세요.";
  if (registrationMode === "PLAN" && includedTargets.length === 0)
    return "포함할 난 묶음을 하나 이상 선택해주세요.";
  if (registrationMode === "RECORD" && recordTargetIds.length === 0)
    return "기록할 난 묶음을 하나 이상 선택해주세요.";
  return null;
}

export function buildTargetSummary({
  excludedIds,
  inboundCandidates,
  inboundRecordIds,
  isInboundPotting,
  manualIds,
  orchidGroups,
  preview,
  targetScopeLabel,
}: {
  excludedIds: Set<number>;
  inboundCandidates: InboundPottingCandidate[];
  inboundRecordIds: Set<number>;
  isInboundPotting: boolean;
  manualIds: Set<number>;
  orchidGroups: OrchidGroup[];
  preview: WorkTargetPreview | null;
  targetScopeLabel: string | null;
}): WorkTargetSummary {
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

  const includedTargets = getIncludedTargets(preview, excludedIds);
  if (manualIds.size === 0) {
    if (targetScopeLabel && preview) {
      const quantity = includedTargets.reduce(
        (sum, target) => sum + target.quantitySnapshot,
        0,
      );
      return {
        title: targetScopeLabel,
        metrics: `난 묶음 ${includedTargets.length}개 · 총 ${quantity.toLocaleString()}분`,
        location: "",
      };
    }
    return {
      title: "동, 다이, 구역 또는 개별 난 묶음을 선택하세요.",
      metrics: "대상 선택이 필요합니다.",
      location: "",
    };
  }

  const selectedGroups = orchidGroups.filter((group) =>
    manualIds.has(group.id),
  );
  const groups = preview
    ? selectedGroups.filter((group) =>
        includedTargets.some((target) => target.orchidGroupId === group.id),
      )
    : selectedGroups;
  const houseCounts = [...groupByHouse(groups).entries()]
    .sort(([left], [right]) => left - right)
    .map(([houseNumber, count]) => `${houseNumber}동 ${count}개`)
    .join(" / ");
  const totalQuantity = groups.reduce((sum, group) => sum + group.quantity, 0);
  const zoneCount = new Set(groups.map((group) => group.bedZoneId)).size;
  const first = groups[0];
  return {
    title: targetScopeLabel
      ? targetScopeLabel
      : first
        ? `${first.varietyName} · ${first.ageYear ?? "-"}년생 · ${first.potSize ?? "-"}`
        : "직접 선택",
    metrics: `난 묶음 ${groups.length}개 · 총 ${totalQuantity.toLocaleString()}분 · ${zoneCount}개 구역`,
    location: houseCounts,
  };
}

export function countAutoSplitWorks({
  inboundCandidates,
  inboundRecordIds,
  includedTargets,
  isInboundPotting,
  selectedWorkType,
}: {
  inboundCandidates: InboundPottingCandidate[];
  inboundRecordIds: Set<number>;
  includedTargets: WorkOperationTarget[];
  isInboundPotting: boolean;
  selectedWorkType?: WorkType;
}) {
  if (isInboundPotting) {
    return new Set(
      inboundCandidates
        .filter((candidate) => inboundRecordIds.has(candidate.id))
        .map((candidate) =>
          candidate.varietyId == null
            ? `name:${candidate.varietyName}`
            : `id:${candidate.varietyId}`,
        ),
    ).size;
  }
  if (
    getWorkTypeDefinition(selectedWorkType).recordResult !== "STRUCTURE_CHANGE"
  ) {
    return 0;
  }
  return new Set(includedTargets.map((target) => `name:${target.varietyName}`))
    .size;
}

export function buildCompletedRecordPayload(
  form: WorkOperationFormState,
  orchidGroupIds: number[],
  workType: WorkType,
): CompletedWorkOperationPayload {
  const template = workType.template;
  return {
    workTypeId: workType.id,
    workDate: form.plannedStartDate,
    targetType: "ORCHID_GROUP",
    targetId: null,
    orchidGroupIds,
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

export function buildWorkTargetScopePayload(
  form: WorkOperationFormState,
  manualIds: Set<number>,
): WorkTargetPreviewPayload | null {
  switch (form.sourceScopeType) {
    case "FARM":
      return { scopeType: "FARM" };
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
