import type { ReactNode } from "react";

export function EmptyListState({
  message,
  filteredMessage,
  hasAppliedFilters = false,
  action,
}: {
  message: string;
  filteredMessage?: string;
  hasAppliedFilters?: boolean;
  action?: ReactNode;
}) {
  const text = hasAppliedFilters ? (filteredMessage ?? message) : message;
  const visibleAction =
    filteredMessage && !hasAppliedFilters ? undefined : action;
  return visibleAction ? (
    <div className="flex flex-col items-center gap-3">
      <span>{text}</span>
      {visibleAction}
    </div>
  ) : (
    <>{text}</>
  );
}
