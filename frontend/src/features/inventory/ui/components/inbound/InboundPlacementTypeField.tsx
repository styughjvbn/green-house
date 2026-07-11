"use client";

import { Field, inputClass } from "../InventoryPrimitives";

const PLACEMENT_TYPE_OPTIONS = [
  { value: "TRAY_12", label: "12구 트레이" },
  { value: "TRAY_15", label: "15구 트레이" },
  { value: "TRAY_20", label: "20구 트레이" },
  { value: "TRAY_24", label: "24구 트레이" },
  { value: "SINGLE_POT", label: "단독 화분" },
  { value: "HANGING", label: "행잉" },
  { value: "CUSTOM", label: "기타" },
];

export function InboundPlacementTypeField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const selectValue = resolvePlacementSelectValue(value);
  const customValue = value.startsWith("CUSTOM:") ? value.slice(7) : "";
  const hasLegacyValue =
    value.trim().length > 0 &&
    selectValue !== "CUSTOM" &&
    !PLACEMENT_TYPE_OPTIONS.some((option) => option.value === selectValue);

  return (
    <div className="space-y-2">
      <Field label="배치 규격">
        <select
          className={inputClass}
          value={hasLegacyValue ? value : selectValue}
          onChange={(event) =>
            onChange(
              event.target.value === "CUSTOM" ? "CUSTOM:" : event.target.value,
            )
          }
        >
          <option value="">선택</option>
          {hasLegacyValue ? (
            <option value={value}>{formatPlacementTypeLabel(value)}</option>
          ) : null}
          {PLACEMENT_TYPE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </Field>

      {selectValue === "CUSTOM" ? (
        <Field label="기타 배치 규격명">
          <input
            className={inputClass}
            required
            value={customValue}
            onChange={(event) => onChange(`CUSTOM:${event.target.value}`)}
          />
        </Field>
      ) : null}
    </div>
  );
}

function resolvePlacementSelectValue(value: string) {
  return value.startsWith("CUSTOM:") ? "CUSTOM" : value;
}

function formatPlacementTypeLabel(value: string) {
  if (value.startsWith("CUSTOM:")) {
    return `${value.slice(7) || "기타"} (기존 기록값)`;
  }
  return `${value} (기존 기록값)`;
}
