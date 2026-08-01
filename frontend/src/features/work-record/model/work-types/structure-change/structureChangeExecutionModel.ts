import type { OrchidGroup, WorkOperation } from "@/entities/farm/types";
import type {
  FarmPlacementReference,
  FarmPlacementSelection,
} from "@/entities/farm/model/placement";
import { createUuid } from "@/shared/lib/id";
import type { StructureChangeExecutionPayload } from "../../../api/workRecordApi";

export type StructureChangeOperation = Pick<
  WorkOperation,
  | "id"
  | "plannedStartDate"
  | "targets"
  | "title"
  | "worker"
  | "workType"
  | "workTypeCode"
>;

export type ResultPurpose = "NORMAL" | "DIVIDE_CANDIDATE" | "HELD";

export type ResultRow = {
  key: string;
  sourceOrchidGroupIds: number[];
  placement: FarmPlacementSelection | null;
  quantity: string;
  potSize: string;
  ageYear: string;
  purpose: ResultPurpose;
  autoQuantity: boolean;
};

export type AvailableSource = {
  group: OrchidGroup;
  target: WorkOperation["targets"][number];
  inferredQuantity: number;
};

export function createExecutionPayload({
  completedDate,
  inputQuantities,
  memo,
  releasedPlacements,
  rows,
  selectedSources,
  worker,
}: {
  completedDate: string;
  inputQuantities: Record<number, string>;
  memo: string;
  releasedPlacements: Record<number, FarmPlacementSelection | null | undefined>;
  rows: ResultRow[];
  selectedSources: AvailableSource[];
  worker: string;
}): StructureChangeExecutionPayload {
  return {
    idempotencyKey: createUuid(),
    completedDate,
    worker: worker.trim() || null,
    memo: memo.trim() || null,
    sources: selectedSources.map(({ group }) => ({
      sourceOrchidGroupId: group.id,
      inputQuantity: Number(inputQuantities[group.id]),
      releasedStartPosition:
        releasedPlacements[group.id]?.startPosition ?? null,
      releasedEndPosition: releasedPlacements[group.id]?.endPosition ?? null,
    })),
    results: rows.map((row) => ({
      bedZoneId: row.placement!.bedZoneId,
      quantity: Number(row.quantity),
      sourceOrchidGroupIds: row.sourceOrchidGroupIds,
      potSize: row.potSize || null,
      ageYear: row.ageYear ? Number(row.ageYear) : null,
      purpose: row.purpose,
      placementType: null,
      trayCount: null,
      splitPlacementAllowed: false,
      startPosition: row.placement!.startPosition,
      endPosition: row.placement!.endPosition,
      memo: null,
    })),
  };
}

export function validateExecution({
  availableSources,
  completedDate,
  inputQuantities,
  recordMode,
  releasedPlacements,
  rows,
  selectedSourceIds,
  selectedSources,
  workTypeCode,
}: {
  availableSources: AvailableSource[];
  completedDate: string;
  inputQuantities: Record<number, string>;
  recordMode: boolean;
  releasedPlacements: Record<number, FarmPlacementSelection | null | undefined>;
  rows: ResultRow[];
  selectedSourceIds: Set<number>;
  selectedSources: AvailableSource[];
  workTypeCode: string;
}) {
  if (!completedDate) return "완료일을 입력해주세요.";
  if (selectedSources.length === 0) return "이번에 작업할 원본을 선택해주세요.";
  if (
    recordMode &&
    (selectedSources.length !== availableSources.length ||
      selectedSources.some(
        ({ group, target }) =>
          Number(inputQuantities[group.id]) !== target.quantitySnapshot,
      ))
  ) {
    return "작업 기록은 선택한 모든 원본의 전체 수량을 입력해주세요.";
  }
  if (
    selectedSources.some(({ group, target }) => {
      const quantity = Number(inputQuantities[group.id]);
      return (
        !Number.isInteger(quantity) ||
        quantity < 1 ||
        quantity > group.quantity ||
        quantity > target.remainingQuantity
      );
    })
  ) {
    return "작업 수량은 현재 수량과 계획 잔여 수량 이내로 입력해주세요.";
  }
  const totalInput = selectedSources.reduce(
    (sum, { group }) => sum + Number(inputQuantities[group.id] || 0),
    0,
  );
  const totalResult = rows.reduce(
    (sum, row) => sum + Number(row.quantity || 0),
    0,
  );
  if (workTypeCode !== "DIVIDE" && totalResult > totalInput)
    return "결과 수량은 투입 수량보다 클 수 없습니다.";
  if (
    rows.length === 0 ||
    rows.some(
      (row) =>
        !row.placement ||
        !Number.isInteger(Number(row.quantity)) ||
        Number(row.quantity) < 1 ||
        row.sourceOrchidGroupIds.length === 0 ||
        row.sourceOrchidGroupIds.some((id) => !selectedSourceIds.has(id)),
    )
  ) {
    return "결과 난 묶음의 원본·위치·수량을 확인해주세요.";
  }
  if (hasOverlappingPlacements(rows))
    return "결과 난 묶음끼리 배치 칸이 겹칩니다.";
  for (const { group } of selectedSources) {
    const inputQuantity = Number(inputQuantities[group.id]);
    const released = releasedPlacements[group.id];
    if (
      released &&
      (inputQuantity >= group.quantity ||
        released.bedZoneId !== group.bedZoneId ||
        group.startPosition == null ||
        group.endPosition == null ||
        released.startPosition <= group.startPosition ||
        released.startPosition >= group.endPosition ||
        released.endPosition !== group.endPosition)
    ) {
      return "원본에서 비울 자리는 원본 배치의 맨 뒤쪽 연속 구간으로 선택해주세요.";
    }
    if (
      inputQuantity < group.quantity &&
      overlapsRemainingSource(
        rows.filter((row) => row.sourceOrchidGroupIds.includes(group.id)),
        group,
        released,
      )
    ) {
      return "일부만 작업하는 원본의 잔여 배치와 결과 배치가 겹칠 수 없습니다.";
    }
  }
  return null;
}

export function newResultRow(group: OrchidGroup, quantity: number): ResultRow {
  return {
    key: createUuid(),
    sourceOrchidGroupIds: [group.id],
    placement: inferPlacement(group),
    quantity: String(quantity),
    potSize: group.potSize ?? "",
    ageYear: group.ageYear == null ? "" : String(group.ageYear),
    purpose: "NORMAL",
    autoQuantity: true,
  };
}

export function sourceLocationLabel(group: OrchidGroup) {
  return `${group.houseNumber}동 ${group.physicalBedNumber}다이 ${group.bedZoneName}`;
}

export function sourcePositionLabel(group: OrchidGroup) {
  if (group.startPosition == null || group.endPosition == null) return "";
  return ` · ${Math.floor(group.startPosition) + 1}~${Math.ceil(group.endPosition)}칸`;
}

export function resultReferencePlacements(
  rows: ResultRow[],
  currentRowKey: string,
): FarmPlacementReference[] {
  return rows.flatMap((row, index) => {
    if (row.key === currentRowKey || row.placement == null) return [];
    return [
      {
        ...row.placement,
        kind: "RESULT" as const,
        label: `결과 ${index + 1} · ${row.quantity || 0}분`,
      },
    ];
  });
}

export function sourceReferencePlacements(
  sources: Array<{ group: OrchidGroup }>,
): FarmPlacementReference[] {
  return sources.flatMap(({ group }) => {
    const placement = inferPlacement(group);
    if (!placement) return [];
    return [
      {
        ...placement,
        kind: "SOURCE" as const,
        label: `${group.varietyName} ${group.quantity.toLocaleString()}분`,
      },
    ];
  });
}

export function savedResultReferencePlacements(
  groups: OrchidGroup[],
): FarmPlacementReference[] {
  return groups.flatMap((group) => {
    if (group.quantity < 1) return [];
    const placement = inferPlacement(group);
    if (!placement) return [];
    return [
      {
        ...placement,
        kind: "SAVED_RESULT" as const,
        label: `${group.varietyName} ${group.quantity.toLocaleString()}분`,
      },
    ];
  });
}

export function collectPriorResultOrchidGroupIds(
  operation: Pick<WorkOperation, "targets">,
) {
  const ids = new Set<number>();
  operation.targets.forEach((target) => {
    collectResultIds(target.resultDetails).forEach((id) => ids.add(id));
  });
  return [...ids];
}

export function inferPlacement(
  group: OrchidGroup,
): FarmPlacementSelection | null {
  if (group.startPosition == null || group.endPosition == null) return null;
  return {
    bedZoneId: group.bedZoneId,
    startCell: Math.floor(group.startPosition) + 1,
    endCell: Math.ceil(group.endPosition),
    startPosition: group.startPosition,
    endPosition: group.endPosition,
    label: `${sourceLocationLabel(group)}${sourcePositionLabel(group)}`,
  };
}

export function inferReleasedPlacement(
  group: OrchidGroup,
  inputQuantity: number,
): FarmPlacementSelection | null {
  if (
    !Number.isInteger(inputQuantity) ||
    inputQuantity < 1 ||
    inputQuantity >= group.quantity ||
    group.startPosition == null ||
    group.endPosition == null
  ) {
    return null;
  }
  const totalCells =
    Math.ceil(group.endPosition) - Math.floor(group.startPosition);
  if (totalCells < 2) return null;
  const releasedCells = Math.min(
    totalCells - 1,
    Math.max(1, Math.ceil((totalCells * inputQuantity) / group.quantity)),
  );
  const endCell = Math.ceil(group.endPosition);
  const startCell = endCell - releasedCells + 1;
  return {
    bedZoneId: group.bedZoneId,
    startCell,
    endCell,
    startPosition: startCell - 1,
    endPosition: endCell,
    label: `${sourceLocationLabel(group)} · ${startCell}~${endCell}칸`,
  };
}

function collectResultIds(details: Record<string, unknown> | null) {
  if (!details) return [];
  const ids: number[] = [];
  collectNumber(details.resultOrchidGroupId, ids);
  collectNumbers(details.resultOrchidGroupIds, ids);
  if (Array.isArray(details.results)) {
    details.results.forEach((item) => {
      if (isRecord(item)) collectNumber(item.orchidGroupId, ids);
    });
  }
  return ids;
}

function collectNumbers(value: unknown, ids: number[]) {
  if (!Array.isArray(value)) return;
  value.forEach((item) => collectNumber(item, ids));
}

function collectNumber(value: unknown, ids: number[]) {
  if (typeof value === "number" && Number.isInteger(value)) {
    ids.push(value);
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value != null;
}

function hasOverlappingPlacements(rows: ResultRow[]) {
  return rows.some((left, index) =>
    rows
      .slice(index + 1)
      .some((right) =>
        Boolean(
          left.placement &&
          right.placement &&
          left.placement.bedZoneId === right.placement.bedZoneId &&
          left.placement.startPosition < right.placement.endPosition &&
          right.placement.startPosition < left.placement.endPosition,
        ),
      ),
  );
}

function overlapsRemainingSource(
  rows: ResultRow[],
  source: OrchidGroup,
  released: FarmPlacementSelection | null | undefined,
) {
  if (source.startPosition == null || source.endPosition == null) return false;
  const remainingEnd = released?.startPosition ?? source.endPosition;
  return rows.some(
    (row) =>
      row.placement &&
      row.placement.bedZoneId === source.bedZoneId &&
      row.placement.startPosition < remainingEnd &&
      source.startPosition! < row.placement.endPosition,
  );
}
