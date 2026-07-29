import {
  keepPreviousData,
  useQuery,
  type QueryKey,
} from "@tanstack/react-query";
import { useState } from "react";

export type PagedListQueryState<Filters> = {
  filters: Filters;
  page: number;
  size: number;
};

type PageMetadata = {
  page: number;
  size: number;
};

export function usePagedListQuery<Filters, PageData extends PageMetadata>({
  createEmptyFilters,
  hasInitialData = true,
  initialFilters,
  initialPage,
  queryKey,
  queryFn,
}: {
  createEmptyFilters: () => Filters;
  hasInitialData?: boolean;
  initialFilters: Filters;
  initialPage: PageData;
  queryKey: (state: PagedListQueryState<Filters>) => QueryKey;
  queryFn: (state: PagedListQueryState<Filters>) => Promise<PageData>;
}) {
  const [filters, setFilters] = useState(initialFilters);
  const [queryState, setQueryState] = useState<PagedListQueryState<Filters>>({
    filters: initialFilters,
    page: initialPage.page,
    size: initialPage.size,
  });
  const isInitialQuery =
    queryState.filters === initialFilters &&
    queryState.page === initialPage.page &&
    queryState.size === initialPage.size;
  const query = useQuery({
    queryKey: queryKey(queryState),
    queryFn: () => queryFn(queryState),
    initialData: hasInitialData && isInitialQuery ? initialPage : undefined,
    placeholderData: keepPreviousData,
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
    query,
    pageData: query.data,
    updateFilter,
    search,
    reset,
    changePage,
    changePageSize,
  };
}
