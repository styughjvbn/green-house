import type { ReactNode } from "react";
import { RefreshCw, Search } from "lucide-react";

export function SalesFilterPanel({
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

export function SalesFilterGrid({
  children,
  className,
}: {
  children: ReactNode;
  className: string;
}) {
  return <div className={`grid gap-2 ${className}`}>{children}</div>;
}

export function SalesFilterField({
  children,
  label,
}: {
  children: ReactNode;
  label: string;
}) {
  return (
    <label>
      <span className="mb-1 block text-sm font-semibold text-[#344138]">
        {label}
      </span>
      {children}
    </label>
  );
}

export function SalesFilterInput({
  label,
  onChange,
  placeholder,
  type = "text",
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  value: string;
}) {
  return (
    <SalesFilterField label={label}>
      <input
        className="h-10 w-full rounded-md border border-[#cfd8cc] px-2 text-sm"
        placeholder={placeholder}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </SalesFilterField>
  );
}

export function SalesFilterSelect({
  children,
  label,
  onChange,
  value,
}: {
  children: ReactNode;
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <SalesFilterField label={label}>
      <select
        className="h-10 w-full rounded-md border border-[#cfd8cc] bg-white px-2 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </SalesFilterField>
  );
}

export function SalesFilterDateRange({
  from,
  onFromChange,
  onToChange,
  to,
}: {
  from: string;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
  to: string;
}) {
  return (
    <div>
      <span className="mb-1 block text-sm font-semibold text-[#344138]">
        기간
      </span>
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <input
          className="h-10 min-w-0 rounded-md border border-[#cfd8cc] px-2 text-sm"
          aria-label="시작일"
          type="date"
          value={from}
          onChange={(event) => onFromChange(event.target.value)}
        />
        <span className="text-[#7a8680]">~</span>
        <input
          className="h-10 min-w-0 rounded-md border border-[#cfd8cc] px-2 text-sm"
          aria-label="종료일"
          type="date"
          value={to}
          onChange={(event) => onToChange(event.target.value)}
        />
      </div>
    </div>
  );
}

export function SalesFilterResetButton({
  className = "",
  onClick,
}: {
  className?: string;
  onClick: () => void;
}) {
  return (
    <button
      className={`inline-flex h-10 items-center justify-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-4 text-sm font-semibold text-[#344138] lg:mt-6 ${className}`}
      type="button"
      onClick={onClick}
    >
      <RefreshCw className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
      초기화
    </button>
  );
}

export function SalesFilterSearchButton({
  className = "",
  disabled = false,
  label = "검색",
  onClick,
}: {
  className?: string;
  disabled?: boolean;
  label?: string;
  onClick?: () => void;
}) {
  return (
    <button
      className={`inline-flex h-10 items-center justify-center gap-2 rounded-md bg-[#159447] px-5 text-sm font-semibold text-white disabled:opacity-60 lg:mt-6 ${className}`}
      type="button"
      disabled={disabled}
      onClick={onClick}
    >
      <Search className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
      {label}
    </button>
  );
}

export function SalesFilterCheck({
  checked,
  label,
  onChange,
}: {
  checked: boolean;
  label: string;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="inline-flex items-center gap-2">
      <input
        className="h-4 w-4 accent-[#159447]"
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  );
}
