"use client";

import { useCallback } from "react";

export function useUrlSearchParamsWriter() {
  return useCallback((updater: (params: URLSearchParams) => void) => {
    const params = new URLSearchParams(window.location.search);
    updater(params);
    const query = params.toString();
    window.history.replaceState(
      null,
      "",
      query ? `${window.location.pathname}?${query}` : window.location.pathname,
    );
  }, []);
}
