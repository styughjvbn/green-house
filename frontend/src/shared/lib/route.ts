export function appendSearchParams(
  pathname: string,
  searchParams: Record<string, string | string[] | undefined> | undefined,
  excludedKeys: string[] = [],
) {
  const params = new URLSearchParams();

  Object.entries(searchParams ?? {}).forEach(([key, value]) => {
    if (excludedKeys.includes(key) || value == null) return;

    if (Array.isArray(value)) {
      value.forEach((item) => params.append(key, item));
      return;
    }

    params.set(key, value);
  });

  const query = params.toString();
  return query ? `${pathname}?${query}` : pathname;
}
