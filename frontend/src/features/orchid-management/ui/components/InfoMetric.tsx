"use client";

export default function InfoMetric({
  label,
  value,
  nowrap = false,
}: {
  label: string;
  value: string;
  nowrap?: boolean;
}) {
  return (
    <div className={nowrap ? "min-w-max" : undefined}>
      <p className="text-xs text-[#5c6a60]">{label}</p>
      <p
        className={`mt-0.5 text-sm font-semibold ${
          nowrap ? "whitespace-nowrap tabular-nums" : ""
        }`}
      >
        {value}
      </p>
    </div>
  );
}
