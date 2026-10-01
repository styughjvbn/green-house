import type {
  MutationLabFilters,
  MutationSourceDomain,
  MutationType,
} from "../model/types";

export const MUTATION_TYPES = [
  "BASELINE_IMPORT",
  "CREATE",
  "UPDATE_DETAILS",
  "MOVE",
  "RESERVE",
  "RELEASE_RESERVATION",
  "CONSUME_RESERVATION",
  "RESTORE_OUTBOUND",
  "DISCARD",
  "TRANSFORM",
  "CORRECTION",
  "RECONCILIATION",
  "COMPENSATION",
  "CANCEL_CREATION",
  "DELETE",
] as const satisfies readonly MutationType[];

export const MUTATION_SOURCE_DOMAINS = [
  "FARM",
  "WORK",
  "SALES",
  "INBOUND",
  "MIGRATION",
] as const satisfies readonly MutationSourceDomain[];

export function readMutationLabFilters(
  searchParams: Pick<URLSearchParams, "get">,
): MutationLabFilters {
  return {
    orchidGroupId: positiveNumber(searchParams.get("orchidGroupId")),
    workOperationId: positiveNumber(searchParams.get("workOperationId")),
    mutationType: enumValue(searchParams.get("mutationType"), MUTATION_TYPES),
    sourceDomain: enumValue(
      searchParams.get("sourceDomain"),
      MUTATION_SOURCE_DOMAINS,
    ),
    page: nonNegativeNumber(searchParams.get("page")) ?? 0,
    size: pageSize(searchParams.get("size")),
  };
}

export function writeMutationLabFilters(
  current: Pick<URLSearchParams, "toString">,
  updates: Partial<MutationLabFilters>,
) {
  const params = new URLSearchParams(current.toString());
  Object.entries(updates).forEach(([key, value]) => {
    if (value == null) params.delete(key);
    else params.set(key, String(value));
  });
  return params.toString();
}

function positiveNumber(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function nonNegativeNumber(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
}

function pageSize(value: string | null) {
  const parsed = positiveNumber(value);
  return parsed && [10, 20, 50].includes(parsed) ? parsed : 20;
}

function enumValue<T extends string>(
  value: string | null,
  values: readonly T[],
): T | null {
  return value != null && values.includes(value as T) ? (value as T) : null;
}
