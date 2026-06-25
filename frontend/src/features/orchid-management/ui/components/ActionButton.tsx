"use client";

export default function ActionButton({
  danger = false,
  disabled = false,
  label,
  onClick,
  primary = false,
}: {
  danger?: boolean;
  disabled?: boolean;
  label: string;
  onClick: () => void;
  primary?: boolean;
}) {
  const className = danger
    ? "border border-[#e0b3aa] bg-white text-[#b43b24]"
    : primary
      ? "bg-[#159447] text-white"
      : "border border-[#d7ddd4] bg-white text-[#435047]";

  return (
    <button
      className={`touch-manipulation rounded-md px-3 py-2 text-sm font-semibold ${className}`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {label}
    </button>
  );
}

export function DisabledAction({ label }: { label: string }) {
  return (
    <button
      className="touch-manipulation rounded-md border border-[#d7ddd4] bg-white px-3 py-2 text-sm font-semibold text-[#435047] opacity-60"
      disabled
      type="button"
    >
      {label}
    </button>
  );
}
