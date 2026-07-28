"use client";

import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";

export function usePagedListUrlActions<Filters>({
  filters,
  filterKeys,
  writeFilterParams,
  onSearch,
  onReset,
  onPageChange,
  onPageSizeChange,
}: {
  filters: Filters;
  filterKeys: ReadonlyArray<string>;
  writeFilterParams: (params: URLSearchParams, filters: Filters) => void;
  onSearch: (filters: Filters) => void;
  onReset: () => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  const writeUrlParams = useUrlSearchParamsWriter();

  function search() {
    onSearch(filters);
    writeUrlParams((params) => {
      writeFilterParams(params, filters);
      params.set("page", "0");
    });
  }

  function reset() {
    onReset();
    writeUrlParams((params) => {
      filterKeys.forEach((key) => params.delete(key));
      params.set("page", "0");
    });
  }

  function changePage(page: number) {
    onPageChange(page);
    writeUrlParams((params) => {
      params.set("page", String(page));
    });
  }

  function changePageSize(size: number) {
    onPageSizeChange(size);
    writeUrlParams((params) => {
      params.set("size", String(size));
      params.set("page", "0");
    });
  }

  return {
    search,
    reset,
    changePage,
    changePageSize,
  };
}
