"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type { OrchidGroup, WorkRecord } from "@/entities/farm/types";
import {
  cancelWorkRecord,
  createCompletedWorkOperationFromRecord,
  getSelectableWorkTargetGroups,
} from "../api/workRecordApi";
import {
  createInitialWorkRecordFilters,
  createInitialWorkRecordForm,
  filterWorkRecords,
  resetWorkRecordFormAfterSubmit,
  toCreateWorkRecordPayload,
} from "../lib/workRecordForm";
import type {
  WorkRecordFilterState,
  WorkRecordFormState,
  WorkRecordManagerProps,
} from "./types";

export function useWorkRecordManager({
  initialRecords,
  houses,
  workTypes,
}: WorkRecordManagerProps) {
  const [records, setRecords] = useState<WorkRecord[]>(initialRecords);
  const [orchidGroups, setOrchidGroups] = useState<OrchidGroup[]>([]);
  const [selectedOrchidGroupIds, setSelectedOrchidGroupIds] = useState<
    Set<number>
  >(new Set());
  const [form, setForm] = useState<WorkRecordFormState>(() =>
    createInitialWorkRecordForm(workTypes, houses),
  );
  const [filters, setFilters] = useState<WorkRecordFilterState>(() =>
    createInitialWorkRecordFilters(),
  );
  const [selectedRecordId, setSelectedRecordId] = useState<number | null>(
    initialRecords[0]?.id ?? null,
  );
  const [detailOpen, setDetailOpen] = useState(Boolean(initialRecords[0]));
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [saving, setSaving] = useState(false);
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
    void getSelectableWorkTargetGroups()
      .then((groups) => {
        if (!cancelled) setOrchidGroups(groups);
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

  function updateForm<K extends keyof WorkRecordFormState>(
    field: K,
    value: WorkRecordFormState[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
  }

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

  async function submitWorkRecord(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setErrorMessage(null);

    try {
      const payload = toCreateWorkRecordPayload(
        form,
        selectedOrchidGroupIds,
        workTypes,
      );
      const workType = workTypes.find((item) => item.id === payload.workTypeId);
      await createCompletedWorkOperationFromRecord(
        payload,
        workType?.name ?? "작업",
      );
      setOperationCreatedVersion((current) => current + 1);
      setShowCreateForm(false);
      setSelectedOrchidGroupIds(new Set());
      setForm((current) => resetWorkRecordFormAfterSubmit(current));
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setSaving(false);
    }
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
    form,
    filters,
    detailOpen,
    currentPage: visibleCurrentPage,
    pageSize,
    totalPages,
    showCreateForm,
    saving,
    canceling,
    errorMessage,
    orchidGroups,
    selectedOrchidGroupIds,
    setSelectedOrchidGroupIds,
    selectRecord,
    closeDetail,
    changePage,
    changePageSize,
    setShowCreateForm,
    updateFilters,
    resetFilters,
    updateForm,
    submitWorkRecord,
    cancelSelectedRecord,
    operationCreatedVersion,
  };
}
