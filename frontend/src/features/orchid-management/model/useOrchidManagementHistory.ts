"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type {
  OrchidGroup,
  OrchidGroupWorkHistory,
} from "@/entities/farm/types";
import {
  getOrchidGroupLineage,
  getWorkHistory,
} from "../api/orchidManagementApi";
import type {
  OrchidGroupLineage,
  OrchidSelection,
  WorkHistoryPage,
  WorkRecordSummary,
} from "./types";

const SUMMARY_HISTORY_SIZE = 20;
const DETAIL_HISTORY_SIZE = 10;

export function useOrchidManagementHistory(
  selection: OrchidSelection | null,
  selectedOrchidGroup: OrchidGroup | null,
) {
  const [version, setVersion] = useState(0);
  const [summaryState, setSummaryState] = useState<{
    key: string;
    result: WorkHistoryPage;
  } | null>(null);
  const summaryCacheRef = useRef(new Map<string, WorkHistoryPage>());
  const summaryRequestsRef = useRef(
    new Map<
      string,
      { controller: AbortController; promise: Promise<WorkHistoryPage> }
    >(),
  );
  const [detailState, setDetailState] = useState<{
    key: string;
    orchidGroupId: number;
    result: WorkHistoryPage;
  } | null>(null);
  const [detailLoadingKey, setDetailLoadingKey] = useState<string | null>(null);
  const detailCacheRef = useRef(new Map<string, WorkHistoryPage>());
  const detailRequestRef = useRef<{
    key: string;
    controller: AbortController;
  } | null>(null);
  const [lineageState, setLineageState] = useState<{
    orchidGroupId: number;
    item: OrchidGroupLineage;
  } | null>(null);
  const scope = useMemo(() => resolveHistoryScope(selection), [selection]);
  const scopeKey = scope ? `${scope.scopeType}:${scope.scopeId}` : null;
  const currentHistory = useMemo(
    () =>
      scopeKey && summaryState?.key === scopeKey
        ? summaryState.result.content
        : [],
    [scopeKey, summaryState],
  );
  const summary = useMemo(
    () => createWorkRecordSummary(currentHistory),
    [currentHistory],
  );
  const summaryLoading = Boolean(scopeKey && summaryState?.key !== scopeKey);

  useEffect(() => {
    if (!scope || !scopeKey) {
      summaryRequestsRef.current.forEach((request) =>
        request.controller.abort(),
      );
      summaryRequestsRef.current.clear();
      return;
    }

    for (const [pendingKey, pendingRequest] of summaryRequestsRef.current) {
      if (pendingKey !== scopeKey) {
        pendingRequest.controller.abort();
        summaryRequestsRef.current.delete(pendingKey);
      }
    }

    const cached = summaryCacheRef.current.get(scopeKey);
    if (cached) {
      let active = true;
      queueMicrotask(() => {
        if (active) setSummaryState({ key: scopeKey, result: cached });
      });
      return () => {
        active = false;
      };
    }

    let request = summaryRequestsRef.current.get(scopeKey);
    if (!request) {
      const controller = new AbortController();
      request = {
        controller,
        promise: getWorkHistory(
          scope.scopeType,
          scope.scopeId,
          0,
          SUMMARY_HISTORY_SIZE,
          controller.signal,
        ),
      };
      summaryRequestsRef.current.set(scopeKey, request);
    }
    let active = true;

    void request.promise
      .then((result) => {
        summaryCacheRef.current.set(scopeKey, result);
        if (active) setSummaryState({ key: scopeKey, result });
      })
      .catch((error: unknown) => {
        if (
          active &&
          !(error instanceof DOMException && error.name === "AbortError")
        ) {
          setSummaryState({
            key: scopeKey,
            result: emptyWorkHistoryPage(0, SUMMARY_HISTORY_SIZE),
          });
        }
      })
      .finally(() => {
        if (summaryRequestsRef.current.get(scopeKey) === request) {
          summaryRequestsRef.current.delete(scopeKey);
        }
      });

    return () => {
      active = false;
    };
  }, [scope, scopeKey, version]);

  useEffect(
    () => () => {
      summaryRequestsRef.current.forEach((request) =>
        request.controller.abort(),
      );
      summaryRequestsRef.current.clear();
      detailRequestRef.current?.controller.abort();
    },
    [],
  );

  useEffect(() => {
    let ignore = false;
    if (!selectedOrchidGroup) return;

    void getOrchidGroupLineage(selectedOrchidGroup.id)
      .then((lineage) => {
        if (!ignore) {
          setLineageState({
            orchidGroupId: selectedOrchidGroup.id,
            item: lineage,
          });
        }
      })
      .catch(() => {
        if (!ignore) {
          setLineageState({
            orchidGroupId: selectedOrchidGroup.id,
            item: {
              orchidGroupId: selectedOrchidGroup.id,
              sources: [],
              results: [],
            },
          });
        }
      });

    return () => {
      ignore = true;
    };
  }, [selectedOrchidGroup, version]);

  useEffect(() => {
    const selectedPrefix = selectedOrchidGroup
      ? `${selectedOrchidGroup.id}:`
      : null;
    const pending = detailRequestRef.current;
    if (
      pending &&
      (!selectedPrefix || !pending.key.startsWith(selectedPrefix))
    ) {
      pending.controller.abort();
      detailRequestRef.current = null;
    }
  }, [selectedOrchidGroup]);

  async function loadPage(page: number) {
    if (!selectedOrchidGroup || page < 0) return;
    const orchidGroupId = selectedOrchidGroup.id;
    const key = `${orchidGroupId}:${page}:${DETAIL_HISTORY_SIZE}`;
    const cached = detailCacheRef.current.get(key);
    if (cached) {
      setDetailState({ key, orchidGroupId, result: cached });
      setDetailLoadingKey(null);
      return;
    }

    detailRequestRef.current?.controller.abort();
    const controller = new AbortController();
    detailRequestRef.current = { key, controller };
    setDetailLoadingKey(key);
    try {
      const result = await getWorkHistory(
        "ORCHID_GROUP",
        orchidGroupId,
        page,
        DETAIL_HISTORY_SIZE,
        controller.signal,
      );
      detailCacheRef.current.set(key, result);
      if (detailRequestRef.current?.key === key) {
        setDetailState({ key, orchidGroupId, result });
      }
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) {
        setDetailState({
          key,
          orchidGroupId,
          result: emptyWorkHistoryPage(page, DETAIL_HISTORY_SIZE),
        });
      }
    } finally {
      if (detailRequestRef.current?.key === key) {
        detailRequestRef.current = null;
        setDetailLoadingKey(null);
      }
    }
  }

  function invalidate() {
    summaryCacheRef.current.clear();
    summaryRequestsRef.current.forEach((request) => request.controller.abort());
    summaryRequestsRef.current.clear();
    detailCacheRef.current.clear();
    detailRequestRef.current?.controller.abort();
    detailRequestRef.current = null;
    setDetailState(null);
    setDetailLoadingKey(null);
    setSummaryState(null);
    setVersion((current) => current + 1);
  }

  const selectedId = selectedOrchidGroup?.id ?? null;
  return {
    history:
      selectedOrchidGroup && scope?.scopeType === "ORCHID_GROUP"
        ? currentHistory
        : [],
    historyLoading: Boolean(selectedOrchidGroup) && summaryLoading,
    historyPage:
      selectedId && detailState?.orchidGroupId === selectedId
        ? detailState.result
        : null,
    historyPageLoading: Boolean(
      selectedId && detailLoadingKey?.startsWith(`${selectedId}:`),
    ),
    lineage:
      selectedId && lineageState?.orchidGroupId === selectedId
        ? lineageState.item
        : null,
    lineageLoading: Boolean(
      selectedId && lineageState?.orchidGroupId !== selectedId,
    ),
    loadPage,
    summary,
    summaryLoading,
    invalidate,
  };
}

function resolveHistoryScope(selection: OrchidSelection | null): {
  scopeType: "HOUSE" | "PHYSICAL_BED" | "BED_ZONE" | "ORCHID_GROUP";
  scopeId: number;
} | null {
  if (!selection) return null;
  switch (selection.type) {
    case "HOUSE":
      return { scopeType: "HOUSE", scopeId: selection.houseId };
    case "PHYSICAL_BED":
      return { scopeType: "PHYSICAL_BED", scopeId: selection.physicalBedId };
    case "BED_ZONE":
      return { scopeType: "BED_ZONE", scopeId: selection.bedZoneId };
    case "ORCHID_GROUP":
      return { scopeType: "ORCHID_GROUP", scopeId: selection.orchidGroupId };
  }
}

function createWorkRecordSummary(
  records: OrchidGroupWorkHistory[],
): WorkRecordSummary {
  const uniqueRecords = new Map<string, OrchidGroupWorkHistory>();
  records.forEach((record) => {
    const key = `${record.sourceKind}-${record.workOperationId}`;
    if (!uniqueRecords.has(key)) uniqueRecords.set(key, record);
  });
  const sortedRecords = [...uniqueRecords.values()].sort(compareRecordsDesc);

  return {
    latestRecords: sortedRecords.slice(0, 5),
    latestByType: {
      pesticide:
        sortedRecords.find((record) => record.workType === "농약") ?? null,
      fertilizer:
        sortedRecords.find((record) => record.workType === "비료") ?? null,
      repot:
        sortedRecords.find((record) => record.workType === "분갈이") ?? null,
    },
  };
}

function emptyWorkHistoryPage(page: number, size: number): WorkHistoryPage {
  return {
    content: [],
    page,
    size,
    totalElements: 0,
    totalPages: 0,
  };
}

function compareRecordsDesc(
  a: OrchidGroupWorkHistory,
  b: OrchidGroupWorkHistory,
) {
  if (a.workDate !== b.workDate) return b.workDate.localeCompare(a.workDate);
  return b.workOperationId - a.workOperationId;
}
