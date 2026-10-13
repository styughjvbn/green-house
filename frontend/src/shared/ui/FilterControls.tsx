import type { ReactNode } from "react";
import { Funnel, RefreshCw, Search } from "lucide-react";
import { cn } from "@/shared/lib/cn";

export function FilterPanel({
  children,
  footer,
}: {
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-3 shadow-sm">
      {children}
      {footer ? (
        <div className="mt-2 flex flex-wrap gap-4 border-t border-[#edf0ec] pt-2 text-sm font-semibold text-[#46544a]">
          {footer}
        </div>
      ) : null}
    </section>
  );
}

export function FilterGrid({
  children,
  className,
}: {
  children: ReactNode;
  className: string;
}) {
  return <div className={`grid gap-2 ${className}`}>{children}</div>;
}

export function FilterField({
  children,
  hideLabel = false,
  label,
  layout = "stacked",
}: {
  children: ReactNode;
  hideLabel?: boolean;
  label: string;
  layout?: "stacked" | "row";
}) {
  return (
    <label
      className={
        layout === "row"
          ? "grid grid-cols-[5.5rem_minmax(0,1fr)] items-center gap-x-3 border-b border-[#edf0ec] py-4 sm:grid-cols-[7rem_minmax(0,1fr)]"
          : undefined
      }
    >
      <span
        className={
          hideLabel
            ? "sr-only"
            : layout === "row"
              ? "text-sm font-semibold text-[#344138]"
              : "mb-1 block text-sm font-semibold text-[#344138]"
        }
      >
        {label}
      </span>
      {children}
    </label>
  );
}

export function FilterInput({
  className,
  hideLabel = false,
  label,
  layout = "stacked",
  onChange,
  placeholder,
  type = "text",
  value,
}: {
  className?: string;
  hideLabel?: boolean;
  label: string;
  layout?: "stacked" | "row";
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  value: string;
}) {
  return (
    <FilterField hideLabel={hideLabel} label={label} layout={layout}>
      <input
        className={cn(
          "h-8 w-full rounded-md border border-[#cfd8cc] px-2 text-sm",
          layout === "row" && "h-10 min-w-0",
          className,
        )}
        placeholder={placeholder}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </FilterField>
  );
}

export function FilterSelect({
  children,
  className,
  label,
  layout = "stacked",
  onChange,
  value,
}: {
  children: ReactNode;
  className?: string;
  label: string;
  layout?: "stacked" | "row";
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <FilterField label={label} layout={layout}>
      <select
        aria-label={layout === "row" ? label : undefined}
        className={cn(
          "h-10 w-full min-w-0 rounded-md border border-[#cfd8cc] bg-white px-2 text-sm",
          className,
        )}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </FilterField>
  );
}

export function FilterDateRange({
  from,
  layout = "stacked",
  onFromChange,
  onToChange,
  to,
}: {
  from: string;
  layout?: "stacked" | "row";
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
  to: string;
}) {
  return (
    <div
      className={
        layout === "row"
          ? "grid grid-cols-[5.5rem_minmax(0,1fr)] items-center gap-x-3 py-4 sm:grid-cols-[7rem_minmax(0,1fr)]"
          : undefined
      }
    >
      <span
        className={
          layout === "row"
            ? "text-sm font-semibold text-[#344138]"
            : "mb-1 block text-sm font-semibold text-[#344138]"
        }
      >
        기간
      </span>
      <div
        aria-label="기간"
        role="group"
        className={
          layout === "row"
            ? "grid min-w-0 gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-center"
            : "grid grid-cols-[1fr_auto_1fr] items-center gap-2"
        }
      >
        <input
          className="h-10 w-full min-w-0 rounded-md border border-[#cfd8cc] px-2 text-sm"
          aria-label="시작일"
          type="date"
          value={from}
          onChange={(event) => onFromChange(event.target.value)}
        />
        <span
          aria-hidden="true"
          className={cn(
            "text-[#7a8680]",
            layout === "row" && "hidden sm:block",
          )}
        >
          ~
        </span>
        <input
          className="h-10 w-full min-w-0 rounded-md border border-[#cfd8cc] px-2 text-sm"
          aria-label="종료일"
          type="date"
          value={to}
          onChange={(event) => onToChange(event.target.value)}
        />
      </div>
    </div>
  );
}

export function FilterToggleButton({
  activeCount = 0,
  controls,
  expanded,
  iconOnly = false,
  onClick,
}: {
  activeCount?: number;
  controls: string;
  expanded: boolean;
  iconOnly?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      aria-controls={controls}
      aria-expanded={expanded}
      aria-label={`필터 ${expanded ? "접기" : "펼치기"}${activeCount > 0 ? `, ${activeCount}개 적용` : ""}`}
      className={cn(
        "relative inline-flex h-8 shrink-0 items-center justify-center gap-1.5 rounded-md border border-[#dfe5dc] bg-white px-3 text-sm font-semibold whitespace-nowrap text-[#344138] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#16713a]",
        iconOnly && "w-8 gap-0 px-0",
      )}
      type="button"
      onClick={onClick}
    >
      <Funnel aria-hidden="true" className="h-4 w-4" strokeWidth={1.8} />
      <span className={iconOnly ? "sr-only" : undefined}>필터</span>
      {activeCount > 0 ? (
        <span
          className={cn(
            "rounded bg-[#e7f0e6] px-1.5 text-xs text-[#214f31]",
            iconOnly && "text-xxs absolute -top-1 -right-1 px-1 leading-3",
          )}
        >
          {activeCount}
        </span>
      ) : null}
    </button>
  );
}

export function FilterResetButton({
  className = "",
  label = "초기화",
  onClick,
}: {
  className?: string;
  label?: string;
  onClick: () => void;
}) {
  return (
    <button
      className={cn(
        "inline-flex h-8 items-center justify-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-3 text-xs font-semibold text-[#344138] lg:mt-6",
        className,
      )}
      type="button"
      onClick={onClick}
    >
      <RefreshCw className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
      {label}
    </button>
  );
}

export function FilterSearchButton({
  className = "",
  disabled = false,
  iconOnly = false,
  label = "검색",
  onClick,
  type = "submit",
}: {
  className?: string;
  disabled?: boolean;
  iconOnly?: boolean;
  label?: string;
  onClick?: () => void;
  type?: "button" | "submit";
}) {
  return (
    <button
      className={cn(
        "inline-flex h-8 w-8 items-center justify-center gap-2 rounded-md bg-[#159447] px-3 text-sm font-semibold text-white disabled:opacity-60 lg:mt-6",
        className,
      )}
      type={type}
      disabled={disabled}
      onClick={onClick}
    >
      <Search className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
      <span className={iconOnly ? "sr-only" : undefined}>{label}</span>
    </button>
  );
}

export function FilterCheck({
  checked,
  className,
  label,
  onChange,
}: {
  checked: boolean;
  className?: string;
  label: string;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className={cn("inline-flex items-center gap-2", className)}>
      <input
        className="h-4 w-4 shrink-0 accent-[#159447]"
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  );
}
