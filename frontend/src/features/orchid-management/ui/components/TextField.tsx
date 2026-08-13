"use client";

import type { ReactNode } from "react";

export default function TextField({
  disabled = false,
  label,
  min,
  max,
  onBlur,
  onChange,
  required = false,
  step,
  type = "text",
  value,
}: {
  disabled?: boolean;
  label: string;
  max?: number;
  min?: number;
  onBlur?: () => void;
  onChange: (value: string) => void;
  required?: boolean;
  step?: number;
  type?: "date" | "number" | "text";
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <input
        className="w-full rounded-md border border-[#cfd8cc] px-2 py-1 text-sm disabled:bg-[#f2f4f1] disabled:text-[#879087]"
        disabled={disabled}
        max={type === "number" ? max : undefined}
        min={type === "number" ? (min ?? 0) : undefined}
        required={required}
        step={type === "number" ? step : undefined}
        type={type}
        value={value}
        onBlur={onBlur}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

export function SelectField({
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
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <select
        className="w-full rounded-md border border-[#cfd8cc] px-2 py-1 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </label>
  );
}
