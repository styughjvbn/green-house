export type RouteSearchParams =
  | Record<string, string | string[] | undefined>
  | undefined;

export function readSearchParam(searchParams: RouteSearchParams, key: string) {
  const value = searchParams?.[key];
  return Array.isArray(value) ? value[0] : value;
}

export function readPageParam(searchParams: RouteSearchParams) {
  return readBoundedInteger(
    searchParams,
    "page",
    0,
    0,
    Number.MAX_SAFE_INTEGER,
  );
}

export function readPageSizeParam(
  searchParams: RouteSearchParams,
  defaultValue: number,
) {
  return readBoundedInteger(searchParams, "size", defaultValue, 1, 100);
}

export function readBooleanParam(searchParams: RouteSearchParams, key: string) {
  return readSearchParam(searchParams, key) === "true";
}

export function readEnumParam<const Value extends string>(
  searchParams: RouteSearchParams,
  key: string,
  allowedValues: readonly Value[],
): Value | "" {
  const value = readSearchParam(searchParams, key);
  return value != null && allowedValues.includes(value as Value)
    ? (value as Value)
    : "";
}

function readBoundedInteger(
  searchParams: RouteSearchParams,
  key: string,
  defaultValue: number,
  minimum: number,
  maximum: number,
) {
  const value = readSearchParam(searchParams, key);
  const parsed = value ? Number(value) : defaultValue;

  if (!Number.isInteger(parsed)) {
    return defaultValue;
  }
  return Math.min(Math.max(parsed, minimum), maximum);
}
