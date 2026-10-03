"use client";

import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { invalidateWorkAndInboundQueries } from "@/entities/farm/model/farmMutationQueries";
import { workRecordQueryKeys } from "./workRecordQueryKeys";

export function useWorkRecordInvalidation() {
  const queryClient = useQueryClient();

  const invalidateOperations = useCallback(
    () => invalidateWorkAndInboundQueries(queryClient),
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
