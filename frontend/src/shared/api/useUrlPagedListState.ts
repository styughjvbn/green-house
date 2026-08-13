"use client";

import { useState } from "react";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";

export function useUrlPagedListState<Filters>({
  emptyFilters,
  filterKeys,
  resetParamKeys = [],
  routeFilters,
  writeFilterParams,
}: {
  emptyFilters: () => Filters;
  filterKeys: ReadonlyArray<string>;
  resetParamKeys?: ReadonlyArray<string>;
  routeFilters: Filters;
  writeFilterParams: (params: URLSearchParams, filters: Filters) => void;
}) {
  const writeUrlParams = useUrlSearchParamsWriter();
  const routeKey = JSON.stringify(routeFilters);
  const [draft, setDraft] = useState({
    routeKey,
    filters: routeFilters,
  });
  const filters = draft.routeKey === routeKey ? draft.filters : routeFilters;

  function updateFilter<K extends keyof Filters>(field: K, value: Filters[K]) {
    setDraft({
      routeKey,
      filters: { ...filters, [field]: value },
    });
  }

  function search() {
    writeUrlParams((params) => {
      writeFilterParams(params, filters);
      resetParamKeys.forEach((key) => params.delete(key));
      params.set("page", "0");
    });
  }

  function reset() {
    setDraft({
      routeKey,
      filters: emptyFilters(),
    });
    writeUrlParams((params) => {
      filterKeys.forEach((key) => params.delete(key));
      resetParamKeys.forEach((key) => params.delete(key));
      params.set("page", "0");
    });
  }

  function changePage(page: number) {
    writeUrlParams((params) => {
      resetParamKeys.forEach((key) => params.delete(key));
      params.set("page", String(page));
    });
  }

  function changePageSize(size: number) {
    writeUrlParams((params) => {
      resetParamKeys.forEach((key) => params.delete(key));
      params.set("size", String(size));
      params.set("page", "0");
    });
  }

  return {
    changePage,
    changePageSize,
    filters,
    reset,
    search,
    updateFilter,
  };
}
