"use client";

import type { ReactNode } from "react";

type TextFieldProps = {
  label: string;
  max?: string;
  onChange: (value: string) => void;
  required?: boolean;
  type?: "date" | "number" | "text";
  value: string;
};

export function TextField({
  label,
  max,
  onChange,
  required = false,
  type = "text",
  value,
}: TextFieldProps) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <input
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
        max={max}
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

type SelectFieldProps = {
  children: ReactNode;
  label: string;
  onChange: (value: string) => void;
  value: string;
};

export function SelectField({
  children,
  label,
  onChange,
  value,
}: SelectFieldProps) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <select
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </label>
  );
}
