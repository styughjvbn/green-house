"use client";

import { isStandardPotSize, POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";

export function PotSizeInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="space-y-1 text-xs font-semibold text-[#425047]">
      <span>{label}</span>
      <select
        className="w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm font-normal outline-none focus:ring-1 focus:ring-[#159447]"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {!isStandardPotSize(value) ? (
          <option disabled value={value}>
            검수 필요: {value}
          </option>
        ) : null}
        {POT_SIZE_OPTIONS.map((option) => (
          <option key={option.value || "unspecified"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
