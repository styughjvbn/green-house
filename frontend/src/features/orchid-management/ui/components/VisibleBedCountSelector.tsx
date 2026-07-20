import type { VisibleBedCount } from "@/entities/farm/types";

export default function VisibleBedCountSelector({
  value,
  onChange,
}: {
  value: VisibleBedCount;
  onChange: (value: VisibleBedCount) => void;
}) {
  return (
    <div
      aria-label="화면에 표시할 다이 수"
      className="flex h-8 overflow-hidden rounded-md border border-[#dfe5dc]"
      role="group"
    >
      {([2, 3, 4] as const).map((count) => (
        <button
          key={count}
          aria-pressed={value === count}
          className={`border-r px-2.5 text-xs font-semibold last:border-r-0 ${
            value === count
              ? "bg-[#246df2] text-white"
              : "bg-white text-[#435047] hover:bg-[#f0f5ee]"
          }`}
          type="button"
          onClick={() => onChange(count)}
        >
          {count}개
        </button>
      ))}
    </div>
  );
}
