"use client";

import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { workRecordQueryKeys } from "./workRecordQueryKeys";

export function useWorkRecordInvalidation() {
  const queryClient = useQueryClient();

  const invalidateOperations = useCallback(
    () =>
      queryClient.invalidateQueries({
        queryKey: workRecordQueryKeys.operations.all,
      }),
    [queryClient],
  );

  const invalidateWorkData = useCallback(
    () =>
      Promise.all([
        invalidateOperations(),
        queryClient.invalidateQueries({
          queryKey: workRecordQueryKeys.references.houses,
        }),
      ]),
    [invalidateOperations, queryClient],
  );

  return {
    invalidateOperations,
    invalidateWorkData,
  };
}
