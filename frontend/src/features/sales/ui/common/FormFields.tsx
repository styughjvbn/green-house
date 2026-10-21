import { useId, type ReactNode } from "react";
import { ReviewBadge, ReviewField, type InputReview } from "./InputReview";

export function TextField({
  disabled = false,
  label,
  maxLength,
  min,
  onChange,
  required = false,
  review,
  type = "text",
  value,
}: {
  disabled?: boolean;
  label: string;
  maxLength?: number;
  min?: number;
  onChange: (value: string) => void;
  required?: boolean;
  review?: InputReview;
  type?: "date" | "number" | "text";
  value: string;
}) {
  const id = useId();
  return (
    <ReviewField
      id={`${id}-review`}
      review={review}
      disabled={disabled}
      className="block w-full"
    >
      <label htmlFor={id} className="text-sm font-semibold text-[#435047]">
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
        {review ? <ReviewBadge /> : null}
      </label>
      <input
        id={id}
        aria-describedby={`${id}-review`}
        className={`mt-1 min-h-10 w-full min-w-0 rounded-md border px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#159447] disabled:bg-[#f5f7f3] ${review ? "border-amber-500 bg-amber-50" : "border-[#cfd8cc] bg-white"}`}
        disabled={disabled}
        maxLength={maxLength}
        min={type === "number" ? (min ?? 0) : undefined}
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </ReviewField>
  );
}

export function SelectField({
  children,
  disabled = false,
  required = false,
  review,
  label,
  onChange,
  value,
}: {
  children: ReactNode;
  disabled?: boolean;
  required?: boolean;
  review?: InputReview;
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  const id = useId();
  return (
    <ReviewField
      id={`${id}-review`}
      review={review}
      disabled={disabled}
      className="block"
    >
      <label htmlFor={id} className="text-sm font-semibold text-[#435047]">
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
        {review ? <ReviewBadge /> : null}
      </label>
      <select
        id={id}
        aria-describedby={`${id}-review`}
        required={required}
        className={`mt-1 min-h-10 w-full min-w-0 rounded-md border px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#159447] disabled:bg-[#f5f7f3] ${review ? "border-amber-500 bg-amber-50" : "border-[#cfd8cc] bg-white"}`}
        disabled={disabled}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </ReviewField>
  );
}
