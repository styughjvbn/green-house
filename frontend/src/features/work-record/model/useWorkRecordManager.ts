"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type { BedZone, OrchidGroup, PhysicalBed, WorkRecord } from "@/types/farm";
import { createWorkRecord, getWorkRecordTargetOptions } from "../api/workRecordApi";
import {
  createInitialWorkRecordForm,
  getSelectedTargetId,
  resetWorkRecordFormAfterSubmit,
  resolveSafeBedZoneId,
  resolveSafeOrchidGroupId,
  resolveSafePhysicalBedId,
  toCreateWorkRecordPayload,
} from "../lib/workRecordForm";
import type { WorkRecordFormState, WorkRecordManagerProps } from "./types";

export function useWorkRecordManager({ initialRecords, houses, workTypes }: WorkRecordManagerProps) {
  const [records, setRecords] = useState<WorkRecord[]>(initialRecords);
  const [physicalBeds, setPhysicalBeds] = useState<PhysicalBed[]>([]);
  const [bedZones, setBedZones] = useState<BedZone[]>([]);
  const [orchidGroups, setOrchidGroups] = useState<OrchidGroup[]>([]);
  const [form, setForm] = useState<WorkRecordFormState>(() => createInitialWorkRecordForm(workTypes, houses));
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const safePhysicalBedId = resolveSafePhysicalBedId(form.physicalBedId, physicalBeds);
  const safeBedZoneId = resolveSafeBedZoneId(form.bedZoneId, bedZones);
  const safeOrchidGroupId = resolveSafeOrchidGroupId(form.orchidGroupId, orchidGroups);

  const selectedTargetId = useMemo(
    () => getSelectedTargetId(form.targetType, form, safePhysicalBedId, safeBedZoneId, safeOrchidGroupId),
    [form, safePhysicalBedId, safeBedZoneId, safeOrchidGroupId],
  );

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
        setErrorMessage(error instanceof Error ? error.message : "대상 목록을 불러오지 못했습니다.");
      }
    });

    return () => {
      cancelled = true;
    };
  }, [form.houseId]);

  function updateForm<K extends keyof WorkRecordFormState>(field: K, value: WorkRecordFormState[K]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submitWorkRecord(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setErrorMessage(null);

    try {
      const createdRecord = await createWorkRecord(toCreateWorkRecordPayload(form, selectedTargetId));
      setRecords((current) => [createdRecord, ...current]);
      setForm((current) => resetWorkRecordFormAfterSubmit(current));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return {
    records,
    form,
    saving,
    errorMessage,
    physicalBeds,
    bedZones,
    orchidGroups,
    safePhysicalBedId,
    safeBedZoneId,
    safeOrchidGroupId,
    updateForm,
    submitWorkRecord,
  };
}
