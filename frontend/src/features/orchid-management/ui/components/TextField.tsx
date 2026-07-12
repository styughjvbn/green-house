"use client";

import type { ReactNode } from "react";

export default function TextField({
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
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
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
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </label>
  );
}
