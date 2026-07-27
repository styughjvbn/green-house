"use client";

type TextFieldProps = {
  disabled?: boolean;
  label: string;
  max?: string;
  onChange: (value: string) => void;
  required?: boolean;
  type?: "date" | "number" | "text";
  value: string;
};

export function TextField({
  disabled = false,
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
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm disabled:cursor-not-allowed disabled:bg-[#f1f4f0] disabled:text-[#6a766e]"
        disabled={disabled}
        max={max}
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
