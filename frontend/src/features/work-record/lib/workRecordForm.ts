import type { WorkRecord, WorkRecordTargetType } from "@/entities/farm/types";
import type { WorkRecordFilterState } from "../model/types";

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

export function formatTarget(record: WorkRecord) {
  if (record.targetType === "FARM") {
    return "전체 농장";
  }

  const label = {
    HOUSE: "동",
    PHYSICAL_BED: "다이",
    BED_ZONE: "구역",
    ORCHID_GROUP: "난 묶음",
  }[record.targetType];

  return `${label} #${record.targetId}`;
}

export function formatTargetType(targetType: WorkRecordTargetType) {
  return {
    FARM: "전체 농장",
    HOUSE: "동",
    PHYSICAL_BED: "다이",
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
