import type { ReactNode } from "react";

export function StatusBadge({
  active,
  labels = ["활성", "비활성"],
}: {
  active: boolean;
  labels?: [string, string];
}) {
  return (
    <span
      className={`inline-flex rounded border px-2 py-0.5 text-xs font-semibold ${active ? "border-[#b9dfc3] bg-[#edf8ef] text-[#18833d]" : "border-[#d9ddda] bg-[#f1f2f1] text-[#69736c]"}`}
    >
      {active ? labels[0] : labels[1]}
    </span>
  );
}

export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="min-w-0 space-y-1 text-xs font-semibold text-[#425047]">
      <span>{label}</span>
      {children}
    </label>
  );
}

export const inputClass =
  "h-9 w-full rounded-md border border-[#d7ddd8] bg-white px-3 text-sm font-normal text-[#27332b] outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]";

export function DetailRow({
  label,
  value,
}: {
  label: string;
  value: ReactNode;
}) {
  return (
    <div className="grid grid-cols-[5.5rem_minmax(0,1fr)] gap-2 text-xs leading-5">
      <dt className="font-semibold text-[#667169]">{label}</dt>
      <dd className="min-w-0 text-[#263129]">{value || "-"}</dd>
    </div>
  );
}
