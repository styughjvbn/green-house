import type { WorkTargetPreview } from "@/entities/farm/types";

export function WorkOperationTargetPreview({
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
