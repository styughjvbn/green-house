"use client";

import type { ReactNode } from "react";

type ActionButtonProps = {
  active?: boolean;
  danger?: boolean;
  disabled?: boolean;
  icon?: ReactNode;
  label: string;
  onClick: () => void;
  primary?: boolean;
};

export default function ActionButton({
  active = false,
  danger = false,
  disabled = false,
  icon,
  label,
  onClick,
  primary = false,
}: ActionButtonProps) {
  const className = danger
    ? "border border-[#e0b3aa] bg-white text-[#b43b24]"
    : active || primary
      ? "border border-[#159447] bg-[#159447] text-white"
      : "border border-[#d7ddd4] bg-white text-[#435047]";

  return (
    <button
      className={`inline-flex h-8 min-h-0 touch-manipulation items-center justify-center gap-1.5 rounded-md px-3 py-0 text-sm leading-none font-semibold transition hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60 ${className}`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {icon ? (
        <span className="flex size-4 items-center justify-center">{icon}</span>
      ) : null}
      <span>{label}</span>
    </button>
  );
}

export function DisabledAction({
  icon,
  label,
}: {
  icon?: ReactNode;
  label: string;
}) {
  return (
    <button
      className="inline-flex h-8 min-h-0 touch-manipulation items-center justify-center gap-1.5 rounded-md border border-[#d7ddd4] bg-white px-3 py-0 text-sm leading-none font-semibold text-[#435047] opacity-60"
      disabled
      type="button"
    >
      {icon ? (
        <span className="flex size-4 items-center justify-center">{icon}</span>
      ) : null}
      <span>{label}</span>
    </button>
  );
}
