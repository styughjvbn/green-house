import { useEffect, useState } from "react";
import { skipToken, useQuery, useQueryClient } from "@tanstack/react-query";
import type { SalesSlipReadingProgress } from "../api/types";
import { streamSalesSlipReadingProgress } from "../api/salesApi";

function reconnectDelay(signal: AbortSignal, milliseconds: number) {
  return new Promise<void>((resolve) => {
    if (signal.aborted) return resolve();
    const finish = () => {
      clearTimeout(timer);
      signal.removeEventListener("abort", finish);
      resolve();
    };
    const timer = setTimeout(finish, milliseconds);
    signal.addEventListener("abort", finish, { once: true });
  });
}

export function useSalesSlipReadingProgress(
  request: { sessionId: string; requestId: string } | null,
  enabled: boolean,
) {
  const client = useQueryClient();
  const [unavailableRequest, setUnavailableRequest] = useState<string | null>(
    null,
  );
  const sessionId = request?.sessionId;
  const requestId = request?.requestId;
  const query = useQuery<SalesSlipReadingProgress>({
    queryKey: ["sales", "photoReadingProgress", sessionId, requestId],
    queryFn: skipToken,
    gcTime: 0,
  });
  useEffect(() => {
    if (!enabled || !sessionId || !requestId) return;
    const controller = new AbortController();
    async function subscribe(activeSessionId: string, activeRequestId: string) {
      let delay = 1000;
      while (!controller.signal.aborted) {
        try {
          await streamSalesSlipReadingProgress(
            activeSessionId,
            activeRequestId,
            controller.signal,
            (progress) => {
              if (controller.signal.aborted) return;
              client.setQueryData(
                [
                  "sales",
                  "photoReadingProgress",
                  activeSessionId,
                  activeRequestId,
                ],
                progress,
              );
              setUnavailableRequest(null);
            },
          );
          return;
        } catch {
          if (controller.signal.aborted) return;
          setUnavailableRequest(activeRequestId);
          await reconnectDelay(controller.signal, delay);
          delay = Math.min(delay * 2, 10_000);
        }
      }
    }
    void subscribe(sessionId, requestId);
    // Disconnecting the viewer must never abort or retry the extraction POST.
    return () => controller.abort();
  }, [client, enabled, sessionId, requestId]);
  return {
    data: query.data,
    unavailable: Boolean(requestId && unavailableRequest === requestId),
  };
}
