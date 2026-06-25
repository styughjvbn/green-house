import type { BedZone, HouseStatusSummary, OrchidGroup, PhysicalBed, WorkRecord, WorkRecordTargetType } from "@/entities/farm/types";
import type { CreateWorkRecordPayload, WorkRecordFormState } from "../model/types";

export function createInitialWorkRecordForm(workTypes: string[], houses: HouseStatusSummary[]): WorkRecordFormState {
  const today = new Date().toISOString().slice(0, 10);

  return {
    workType: workTypes[0] ?? "농약",
    workDate: today,
    targetType: "FARM",
    houseId: houses[0] ? String(houses[0].houseId) : "",
    physicalBedId: "",
    bedZoneId: "",
    orchidGroupId: "",
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    worker: "",
    memo: "",
  };
}

export function resolveSafePhysicalBedId(value: string, physicalBeds: PhysicalBed[]) {
  return resolveSafeOptionId(value, physicalBeds.map((bed) => bed.id));
}

export function resolveSafeBedZoneId(value: string, bedZones: BedZone[]) {
  return resolveSafeOptionId(value, bedZones.map((zone) => zone.id));
}

export function resolveSafeOrchidGroupId(value: string, orchidGroups: OrchidGroup[]) {
  return resolveSafeOptionId(value, orchidGroups.map((group) => group.id));
}

export function getSelectedTargetId(
  targetType: WorkRecordTargetType,
  form: WorkRecordFormState,
  safePhysicalBedId: string,
  safeBedZoneId: string,
  safeOrchidGroupId: string,
) {
  if (targetType === "HOUSE") {
    return toNullableNumber(form.houseId);
  }
  if (targetType === "PHYSICAL_BED") {
    return toNullableNumber(safePhysicalBedId);
  }
  if (targetType === "BED_ZONE") {
    return toNullableNumber(safeBedZoneId);
  }
  if (targetType === "ORCHID_GROUP") {
    return toNullableNumber(safeOrchidGroupId);
  }
  return null;
}

export function toCreateWorkRecordPayload(form: WorkRecordFormState, targetId: number | null): CreateWorkRecordPayload {
  return {
    workType: form.workType,
    workDate: form.workDate,
    targetType: form.targetType,
    targetId,
    materialName: nullableText(form.materialName),
    dilutionRatio: nullableText(form.dilutionRatio),
    quantity: nullableText(form.quantity),
    worker: nullableText(form.worker),
    memo: nullableText(form.memo),
  };
}

export function resetWorkRecordFormAfterSubmit(form: WorkRecordFormState): WorkRecordFormState {
  return {
    ...form,
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    memo: "",
  };
}

export function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function formatTarget(record: WorkRecord) {
  if (record.targetType === "FARM") {
    return "전체 농장";
  }

  const label = {
    HOUSE: "동",
    PHYSICAL_BED: "배드",
    BED_ZONE: "구역",
    ORCHID_GROUP: "난 묶음",
  }[record.targetType];

  return `${label} #${record.targetId}`;
}

function resolveSafeOptionId(currentValue: string, ids: number[]) {
  if (currentValue && ids.some((id) => String(id) === currentValue)) {
    return currentValue;
  }

  return ids[0] ? String(ids[0]) : "";
}

function toNullableNumber(value: string) {
  return value ? Number(value) : null;
}

