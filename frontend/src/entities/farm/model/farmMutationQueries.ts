import type { QueryClient } from "@tanstack/react-query";

export const farmMutationQueryKeys = {
  workOperations: ["workRecords", "operations"] as const,
  inboundRecords: ["inventory", "inbound"] as const,
};

export function invalidateWorkAndInboundQueries(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({
      queryKey: farmMutationQueryKeys.workOperations,
    }),
    queryClient.invalidateQueries({
      queryKey: farmMutationQueryKeys.inboundRecords,
    }),
  ]);
}
