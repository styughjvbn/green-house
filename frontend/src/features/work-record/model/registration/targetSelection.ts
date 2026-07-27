import type { WorkTargetPreview } from "@/entities/farm/types";

export function getIncludedTargets(
  preview: WorkTargetPreview | null,
  excludedIds: Set<number>,
) {
  return (
    preview?.targets.filter(
      (target) =>
        target.orchidGroupId != null && !excludedIds.has(target.orchidGroupId),
    ) ?? []
  );
}

export function getRecordTargetIds(
  preview: WorkTargetPreview | null,
  excludedIds: Set<number>,
  manualIds: Set<number>,
) {
  if (!preview) return [...manualIds];
  return preview.targets.flatMap((target) =>
    target.orchidGroupId != null && !excludedIds.has(target.orchidGroupId)
      ? [target.orchidGroupId]
      : [],
  );
}
