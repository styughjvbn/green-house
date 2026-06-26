import type {
  BedZone,
  HouseStatusSummary,
  OrchidGroup,
  PhysicalBed,
  WorkRecord,
  WorkRecordTargetType,
  WorkType,
} from "@/entities/farm/types";
import {
  getManualWorkTypes,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import type {
  CreateWorkRecordPayload,
  WorkRecordFilterState,
  WorkRecordFormState,
} from "../model/types";

export function createInitialWorkRecordForm(
  workTypes: WorkType[],
  houses: HouseStatusSummary[],
): WorkRecordFormState {
  const today = new Date().toISOString().slice(0, 10);
  const firstWorkType = getManualWorkTypes(workTypes)[0] ?? workTypes[0];

  return {
    workTypeId: firstWorkType ? String(firstWorkType.id) : "",
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

export function resolveSafePhysicalBedId(
  value: string,
  physicalBeds: PhysicalBed[],
) {
  return resolveSafeOptionId(
    value,
    physicalBeds.map((bed) => bed.id),
  );
}

export function resolveSafeBedZoneId(value: string, bedZones: BedZone[]) {
  return resolveSafeOptionId(
    value,
    bedZones.map((zone) => zone.id),
  );
}

export function resolveSafeOrchidGroupId(
  value: string,
  orchidGroups: OrchidGroup[],
) {
  return resolveSafeOptionId(
    value,
    orchidGroups.map((group) => group.id),
  );
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

export function toCreateWorkRecordPayload(
  form: WorkRecordFormState,
  targetId: number | null,
  workTypes: WorkType[],
): CreateWorkRecordPayload {
  const workType = workTypes.find(
    (candidate) => String(candidate.id) === form.workTypeId,
  );
  const template = workType?.template ?? null;

  return {
    workTypeId: Number(form.workTypeId),
    workDate: form.workDate,
    targetType: form.targetType,
    targetId,
    materialName: isVisibleWorkRecordField(template, "materialName")
      ? nullableText(form.materialName)
      : null,
    dilutionRatio: isVisibleWorkRecordField(template, "dilutionRatio")
      ? nullableText(form.dilutionRatio)
      : null,
    quantity: isVisibleWorkRecordField(template, "quantity")
      ? nullableText(form.quantity)
      : null,
    worker: isVisibleWorkRecordField(template, "worker")
      ? nullableText(form.worker)
      : null,
    memo: isVisibleWorkRecordField(template, "memo")
      ? nullableText(form.memo)
      : null,
  };
}

export function resetWorkRecordFormAfterSubmit(
  form: WorkRecordFormState,
): WorkRecordFormState {
  return {
    ...form,
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    memo: "",
  };
}

export function createInitialWorkRecordFilters(
  today = new Date().toISOString().slice(0, 10),
): WorkRecordFilterState {
  const from = new Date(today);
  from.setDate(from.getDate() - 30);

  return {
    targetType: "",
    workType: "",
    from: from.toISOString().slice(0, 10),
    to: today,
    worker: "",
    keyword: "",
  };
}

export function filterWorkRecords(
  records: WorkRecord[],
  filters: WorkRecordFilterState,
) {
  const keyword = filters.keyword.trim().toLowerCase();
  const worker = filters.worker.trim().toLowerCase();

  return records.filter((record) => {
    if (filters.targetType && record.targetType !== filters.targetType) {
      return false;
    }
    if (filters.workType && record.workType !== filters.workType) {
      return false;
    }
    if (filters.from && record.workDate < filters.from) {
      return false;
    }
    if (filters.to && record.workDate > filters.to) {
      return false;
    }
    if (worker && !(record.worker ?? "").toLowerCase().includes(worker)) {
      return false;
    }
    if (!keyword) {
      return true;
    }

    return [
      record.materialName,
      record.dilutionRatio,
      record.quantity,
      record.memo,
      record.workType,
      formatTarget(record),
    ]
      .filter(Boolean)
      .some((value) => value?.toLowerCase().includes(keyword));
  });
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

export function formatTargetType(targetType: WorkRecordTargetType) {
  return {
    FARM: "전체 농장",
    HOUSE: "동",
    PHYSICAL_BED: "물리 배드",
    BED_ZONE: "논리 구역",
    ORCHID_GROUP: "난 묶음",
  }[targetType];
}

export function formatMaterialSummary(record: WorkRecord) {
  return (
    [record.materialName, record.dilutionRatio, record.quantity]
      .filter(Boolean)
      .join(" / ") || "-"
  );
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
