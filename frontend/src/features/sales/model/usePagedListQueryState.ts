import { useState } from "react";

export type PagedListQueryState<Filters> = {
  filters: Filters;
  page: number;
  size: number;
};

export function usePagedListQueryState<Filters>({
  createEmptyFilters,
  initialFilters,
  initialPage,
  initialSize,
}: {
  createEmptyFilters: () => Filters;
  initialFilters: Filters;
  initialPage: number;
  initialSize: number;
}) {
  const [filters, setFilters] = useState(initialFilters);
  const [queryState, setQueryState] = useState<PagedListQueryState<Filters>>({
    filters: initialFilters,
    page: initialPage,
    size: initialSize,
  });

  function updateFilter<K extends keyof Filters>(field: K, value: Filters[K]) {
    setFilters((current) => ({ ...current, [field]: value }));
  }

  function search(nextFilters = filters) {
    setQueryState((current) => ({
      ...current,
      filters: nextFilters,
      page: 0,
    }));
  }

  function reset() {
    const emptyFilters = createEmptyFilters();
    setFilters(emptyFilters);
    setQueryState((current) => ({
      ...current,
      filters: emptyFilters,
      page: 0,
    }));
  }

  function changePage(page: number) {
    setQueryState((current) => ({ ...current, page }));
  }

  function changePageSize(size: number) {
    setQueryState((current) => ({ ...current, size, page: 0 }));
  }

  return {
    filters,
    queryState,
    updateFilter,
    search,
    reset,
    changePage,
    changePageSize,
  };
}
