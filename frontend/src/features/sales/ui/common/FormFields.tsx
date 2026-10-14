import type { ReactNode } from "react";

export function TextField({
  disabled = false,
  label,
  maxLength,
  min,
  onChange,
  required = false,
  type = "text",
  value,
}: {
  disabled?: boolean;
  label: string;
  maxLength?: number;
  min?: number;
  onChange: (value: string) => void;
  required?: boolean;
  type?: "date" | "number" | "text";
  value: string;
}) {
  return (
    <label className="block w-full">
      <span className="text-sm font-semibold text-[#435047]">
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </span>
      <input
        className="mt-1 min-h-10 w-full min-w-0 rounded-md border border-[#cfd8cc] bg-white px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#159447] disabled:bg-[#f5f7f3]"
        disabled={disabled}
        maxLength={maxLength}
        min={type === "number" ? (min ?? 0) : undefined}
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

export function SelectField({
  children,
  disabled = false,
  required = false,
  label,
  onChange,
  value,
}: {
  children: ReactNode;
  disabled?: boolean;
  required?: boolean;
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </span>
      <select
        required={required}
        className="mt-1 min-h-10 w-full min-w-0 rounded-md border border-[#cfd8cc] bg-white px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#159447] disabled:bg-[#f5f7f3]"
        disabled={disabled}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </label>
  );
}
