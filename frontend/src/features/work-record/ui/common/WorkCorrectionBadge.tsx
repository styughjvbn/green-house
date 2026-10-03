export function WorkCorrectionBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="inline-flex shrink-0 rounded bg-[#fff0d4] px-1.5 py-px text-[10px] font-semibold text-[#85550e]">
      보정 {count}건
    </span>
  );
}
