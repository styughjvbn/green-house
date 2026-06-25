import type { ReactNode } from "react";
import { dotClass } from "../../lib/dashboardView";
import type { DashboardTone } from "../../model/types";

export function DashboardPanel({
  action,
  children,
  title,
}: {
  action?: ReactNode;
  children: ReactNode;
  title: string;
}) {
  return (
    <section className="rounded-lg border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-[#17251b]">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

export function DashboardMetric({
  label,
  value,
  warning = false,
}: {
  label: string;
  value: string;
  warning?: boolean;
}) {
  return (
    <div className="border-r border-[#edf0ec] p-3 last:border-r-0">
      <p className="text-xs text-[#6a766e]">{label}</p>
      <p
        className={`mt-1 font-bold ${warning ? "text-[#e52d2d]" : "text-[#17251b]"}`}
      >
        {value}
      </p>
    </div>
  );
}

export function DashboardLegend({
  label,
  tone,
}: {
  label: string;
  tone: DashboardTone;
}) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className={`h-2 w-2 rounded-full ${dotClass(tone)}`} />
      {label}
    </span>
  );
}

export function DashboardEmptyText({ text }: { text: string }) {
  return <p className="py-6 text-center text-sm text-[#5c6a60]">{text}</p>;
}
