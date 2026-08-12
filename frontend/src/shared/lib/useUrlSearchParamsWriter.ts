"use client";

import { useCallback } from "react";

export function useUrlSearchParamsWriter() {
  return useCallback(
    (
      updater: (params: URLSearchParams) => void,
      historyMode: "replace" | "push" = "replace",
    ) => {
      const params = new URLSearchParams(window.location.search);
      updater(params);
      const query = params.toString();
      const url = query
        ? `${window.location.pathname}?${query}`
        : window.location.pathname;
      const method = historyMode === "push" ? "pushState" : "replaceState";
      window.history[method](null, "", url);
    },
    [],
  );
}
