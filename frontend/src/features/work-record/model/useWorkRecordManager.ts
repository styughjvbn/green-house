"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type {
  BedZone,
  OrchidGroup,
  PhysicalBed,
  WorkRecord,
} from "@/entities/farm/types";
import {
  createWorkRecord,
  getWorkRecordTargetOptions,
} from "../api/workRecordApi";
import {
  createInitialWorkRecordFilters,
  createInitialWorkRecordForm,
  filterWorkRecords,
  getSelectedTargetId,
  resetWorkRecordFormAfterSubmit,
  resolveSafeBedZoneId,
  resolveSafeOrchidGroupId,
  resolveSafePhysicalBedId,
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
  const [physicalBeds, setPhysicalBeds] = useState<PhysicalBed[]>([]);
  const [bedZones, setBedZones] = useState<BedZone[]>([]);
  const [orchidGroups, setOrchidGroups] = useState<OrchidGroup[]>([]);
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
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const safePhysicalBedId = resolveSafePhysicalBedId(
    form.physicalBedId,
    physicalBeds,
  );
  const safeBedZoneId = resolveSafeBedZoneId(form.bedZoneId, bedZones);
  const safeOrchidGroupId = resolveSafeOrchidGroupId(
    form.orchidGroupId,
    orchidGroups,
  );

  const selectedTargetId = useMemo(
    () =>
      getSelectedTargetId(
        form.targetType,
        form,
        safePhysicalBedId,
        safeBedZoneId,
        safeOrchidGroupId,
      ),
    [form, safePhysicalBedId, safeBedZoneId, safeOrchidGroupId],
  );
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
    if (!form.houseId) {
      return;
    }

    let cancelled = false;

    async function loadHouseScopedOptions() {
      const options = await getWorkRecordTargetOptions(form.houseId);
      if (cancelled) {
        return;
      }
      setPhysicalBeds(options.physicalBeds);
      setBedZones(options.bedZones);
      setOrchidGroups(options.orchidGroups);
    }

    void loadHouseScopedOptions().catch((error) => {
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
  }, [form.houseId]);

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
      const createdRecord = await createWorkRecord(
        toCreateWorkRecordPayload(form, selectedTargetId, workTypes),
      );
      setRecords((current) => [createdRecord, ...current]);
      setSelectedRecordId(createdRecord.id);
      setDetailOpen(true);
      setCurrentPage(1);
      setShowCreateForm(false);
      setForm((current) => resetWorkRecordFormAfterSubmit(current));
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setSaving(false);
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
    errorMessage,
    physicalBeds,
    bedZones,
    orchidGroups,
    safePhysicalBedId,
    safeBedZoneId,
    safeOrchidGroupId,
    selectRecord,
    closeDetail,
    changePage,
    changePageSize,
    setShowCreateForm,
    updateFilters,
    resetFilters,
    updateForm,
    submitWorkRecord,
  };
}
