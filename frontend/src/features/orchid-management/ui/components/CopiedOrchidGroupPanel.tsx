import type { BedZone, OrchidGroup } from "@/entities/farm/types";

export default function CopiedOrchidGroupPanel({
  copiedOrchidGroup,
  resolvedZone,
  onClear,
  onPaste,
}: {
  copiedOrchidGroup: OrchidGroup;
  resolvedZone: BedZone | null;
  onClear: () => void;
  onPaste: () => void;
}) {
  return (
    <div className="mt-3 flex shrink-0 items-center justify-between gap-2 rounded-md border border-[#dbe8d8] bg-[#f5faf3] px-3 py-2 text-xs">
      <span className="min-w-0 truncate font-semibold text-[#34503b]">
        복사됨: {copiedOrchidGroup.varietyName} / {copiedOrchidGroup.quantity}분
      </span>
      <div className="flex shrink-0 items-center gap-1.5">
        <button
          className="rounded-md bg-[#159447] px-2.5 py-1.5 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          disabled={!resolvedZone}
          onClick={onPaste}
          type="button"
        >
          붙여넣기
        </button>
        <button
          className="rounded-md border border-[#cfd8cc] bg-white px-2.5 py-1.5 font-semibold text-[#435047]"
          onClick={onClear}
          type="button"
        >
          Clear
        </button>
      </div>
    </div>
  );
}
