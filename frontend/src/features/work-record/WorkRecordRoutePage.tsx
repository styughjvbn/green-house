import {
  dehydrate,
  HydrationBoundary,
  QueryClient,
} from "@tanstack/react-query";
import { redirect } from "next/navigation";
import {
  createNormalizedWorkRecordSearchParams,
  createServerSearchParamReader,
  needsWorkRecordUrlNormalization,
  readWorkRecordUrlState,
} from "./lib/workRecordUrlState";
import {
  workOperationCalendarQueryOptions,
  workOperationPageQueryOptions,
  workTypesQueryOptions,
} from "./model/workRecordQueryOptions";
import { WorkRecordWorkspace } from "./ui/WorkRecordWorkspace";

export async function WorkRecordManager({
  resolvedSearchParams,
}: {
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  const routeState = readWorkRecordUrlState(
    createServerSearchParamReader(resolvedSearchParams),
  );
  if (needsWorkRecordUrlNormalization(resolvedSearchParams, routeState)) {
    redirect(
      `/work-records?${createNormalizedWorkRecordSearchParams(
        resolvedSearchParams,
        routeState,
      )}`,
    );
  }
  const queryClient = new QueryClient();
  const operationPrefetch =
    routeState.view === "CALENDAR"
      ? queryClient.prefetchQuery(workOperationCalendarQueryOptions(routeState))
      : queryClient.prefetchQuery(workOperationPageQueryOptions(routeState));

  await Promise.all([
    queryClient.prefetchQuery(workTypesQueryOptions()),
    operationPrefetch,
  ]);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <WorkRecordWorkspace />
    </HydrationBoundary>
  );
}
