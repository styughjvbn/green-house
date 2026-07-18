"use client";

import { useEffect, useMemo, useState } from "react";
import type { BedZone, OrchidGroup, WorkRecord } from "@/entities/farm/types";
import {
  cancelWorkRecord,
  getWorkTargetSelectionOptions,
} from "../api/workRecordApi";
import {
  createInitialWorkRecordFilters,
  filterWorkRecords,
} from "../lib/workRecordForm";
import type { WorkRecordFilterState, WorkRecordManagerProps } from "./types";

export function useWorkRecordManager({
  initialRecords,
}: WorkRecordManagerProps) {
  const [records, setRecords] = useState<WorkRecord[]>(initialRecords);
  const [orchidGroups, setOrchidGroups] = useState<OrchidGroup[]>([]);
  const [bedZones, setBedZones] = useState<BedZone[]>([]);
  const [filters, setFilters] = useState<WorkRecordFilterState>(() =>
    createInitialWorkRecordFilters(),
  );
  const [selectedRecordId, setSelectedRecordId] = useState<number | null>(
    initialRecords[0]?.id ?? null,
  );
  const [detailOpen, setDetailOpen] = useState(Boolean(initialRecords[0]));
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [canceling, setCanceling] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [operationCreatedVersion, setOperationCreatedVersion] = useState(0);

  const filteredRecords = useMemo(
    () => filterWorkRecords(records, filters),
    [records, filters],
  );
  const totalPages = Math.max(1, Math.ceil(filteredRecords.length / pageSize));
  const visibleCurrentPage = Math.min(currentPage, totalPages);
  const paginatedRecords = useMemo(() => {
    const startIndex = (visibleCurrentPage - 1) * pageSize;
    return filteredRecords.slice(startIndex, startIndex + pageSize);
  }, [filteredRecords, pageSize, visibleCurrentPage]);
  const selectedRecord =
    records.find((record) => record.id === selectedRecordId) ??
    filteredRecords[0] ??
    records[0] ??
    null;

  useEffect(() => {
    let cancelled = false;
    void getWorkTargetSelectionOptions()
      .then((options) => {
        if (!cancelled) {
          setOrchidGroups(options.orchidGroups);
          setBedZones(options.bedZones);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "대상 목록을 불러오지 못했습니다.",
          );
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  function updateFilters<K extends keyof WorkRecordFilterState>(
    field: K,
    value: WorkRecordFilterState[K],
  ) {
    setFilters((current) => ({ ...current, [field]: value }));
    setCurrentPage(1);
  }

  function resetFilters() {
    setFilters(createInitialWorkRecordFilters());
    setCurrentPage(1);
  }

  function changePage(page: number) {
    setCurrentPage(Math.min(Math.max(page, 1), totalPages));
  }

  function changePageSize(size: number) {
    setPageSize(size);
    setCurrentPage(1);
  }

  function selectRecord(recordId: number) {
    setSelectedRecordId(recordId);
    setDetailOpen(true);
  }

  function closeDetail() {
    setDetailOpen(false);
  }

  async function cancelSelectedRecord(cancelReason: string | null) {
    if (selectedRecordId == null) {
      return;
    }

    setCanceling(true);
    setErrorMessage(null);

    try {
      await cancelWorkRecord({ workRecordId: selectedRecordId, cancelReason });
      setRecords((current) => {
        const nextRecords = current.filter(
          (record) => record.id !== selectedRecordId,
        );
        const nextSelectedRecordId = nextRecords[0]?.id ?? null;
        setSelectedRecordId(nextSelectedRecordId);
        setDetailOpen(Boolean(nextSelectedRecordId));
        return nextRecords;
      });
      setCurrentPage(1);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setCanceling(false);
    }
  }

  return {
    records,
    filteredRecords,
    paginatedRecords,
    selectedRecord,
    filters,
    detailOpen,
    currentPage: visibleCurrentPage,
    pageSize,
    totalPages,
    canceling,
    errorMessage,
    orchidGroups,
    bedZones,
    selectRecord,
    closeDetail,
    changePage,
    changePageSize,
    updateFilters,
    resetFilters,
    cancelSelectedRecord,
    operationCreatedVersion,
    setOperationCreatedVersion,
  };
}
