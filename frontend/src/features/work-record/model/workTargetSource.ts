import type {
  WorkOperationFormState,
  WorkTargetGroupChoice,
  WorkTargetPreviewPayload,
  WorkTargetSourcePayload,
} from "./types";

type FarmTargetSource = Extract<
  WorkTargetSourcePayload,
  { sourceScopeType: "FARM" }
>;
type DerivedGroupTargetSource = Extract<
  WorkTargetSourcePayload,
  { sourceScopeType: "DERIVED_GROUP" }
>;
type UserCollectionTargetSource = Extract<
  WorkTargetSourcePayload,
  { sourceScopeType: "USER_COLLECTION" }
>;
type ManualTargetSource = Extract<
  WorkTargetSourcePayload,
  { sourceScopeType: "MANUAL_SELECTION" }
>;

export function farmWorkTargetSource(): FarmTargetSource {
  return { sourceScopeType: "FARM" };
}

export function derivedGroupWorkTargetSource(
  derivedGroupKey: string,
): DerivedGroupTargetSource {
  const normalizedKey = derivedGroupKey.trim();
  if (!normalizedKey) throw new Error("자동 그룹 키가 필요합니다.");
  return {
    sourceScopeType: "DERIVED_GROUP",
    sourceDerivedGroupKey: normalizedKey,
  };
}

export function userCollectionWorkTargetSource(
  collectionId: number,
): UserCollectionTargetSource {
  if (!Number.isInteger(collectionId) || collectionId <= 0) {
    throw new Error("사용자 그룹 ID가 필요합니다.");
  }
  return { sourceScopeType: "USER_COLLECTION", sourceScopeId: collectionId };
}

export function manualWorkTargetSource(
  orchidGroupIds: Iterable<number>,
): ManualTargetSource {
  const normalizedIds = [...new Set(orchidGroupIds)];
  if (normalizedIds.length === 0) {
    throw new Error("직접 선택한 난 묶음이 한 개 이상 필요합니다.");
  }
  return {
    sourceScopeType: "MANUAL_SELECTION",
    sourceOrchidGroupIds: normalizedIds,
  };
}

export function buildWorkTargetSourceFromForm(
  form: WorkOperationFormState,
  manualIds: Set<number>,
): WorkTargetPreviewPayload | null {
  switch (form.sourceScopeType) {
    case "FARM":
      return farmWorkTargetSource();
    case "DERIVED_GROUP":
      return form.derivedGroupKey.trim()
        ? derivedGroupWorkTargetSource(form.derivedGroupKey)
        : null;
    case "USER_COLLECTION":
      return form.collectionId && Number(form.collectionId) > 0
        ? userCollectionWorkTargetSource(Number(form.collectionId))
        : null;
    case "MANUAL_SELECTION":
      return manualIds.size > 0 ? manualWorkTargetSource(manualIds) : null;
    case "INBOUND_RECORD_SELECTION":
      return null;
  }
}

export function buildWorkTargetSourceFromGroupChoice(
  groupChoice: WorkTargetGroupChoice | null,
  selectedIds: Set<number>,
): WorkTargetPreviewPayload | null {
  if (!groupChoice) {
    return selectedIds.size > 0 ? manualWorkTargetSource(selectedIds) : null;
  }
  return groupChoice.type === "DERIVED_GROUP"
    ? derivedGroupWorkTargetSource(groupChoice.derivedGroupKey)
    : userCollectionWorkTargetSource(groupChoice.collectionId);
}
