import type { AppliedFilterBadge } from "@/shared/ui/ListFilterToolbar";

export function periodFilterBadge<K extends string>(
  from: string,
  to: string,
  fields: readonly K[],
  onRemove: (fields: readonly K[]) => void,
): AppliedFilterBadge[] {
  if (!from && !to) return [];
  const period =
    from && to ? `${from} ~ ${to}` : from ? `${from}부터` : `${to}까지`;
  return [
    {
      key: "period",
      label: `기간: ${period}`,
      onRemove: () => onRemove(fields),
    },
  ];
}

export function valueFilterBadge<K extends string>(
  key: K,
  label: string,
  value: string | boolean,
  onRemove: (fields: readonly K[]) => void,
): AppliedFilterBadge[] {
  if (typeof value === "string" ? !value.trim() : !value) return [];
  return [{ key, label, onRemove: () => onRemove([key]) }];
}

export function hasFilterConditions(filters: Record<string, string | boolean>) {
  return Object.values(filters).some((value) =>
    typeof value === "string" ? Boolean(value.trim()) : value,
  );
}
