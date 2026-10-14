import type {
  AllocationPayload,
  AllocationCorrectionPayload,
  AllocationTargetType,
} from "../api/types";
export type AllocationDraft = {
  key: string;
  receiptId: string;
  targetType: AllocationTargetType | "";
  targetId: string;
  amount: string;
};
export function allocationLines(
  rows: AllocationDraft[],
): AllocationPayload["allocations"] {
  if (rows.length > 100)
    throw new Error("배분은 한 번에 100건까지 가능합니다.");
  return rows.map((row) => {
    const receiptId = Number(row.receiptId),
      targetId = Number(row.targetId),
      amount = Number(row.amount);
    if (
      ![receiptId, targetId, amount].every(
        (value) => Number.isSafeInteger(value) && value > 0,
      ) ||
      !row.targetType
    )
      throw new Error("수납·대상을 선택하고 배분액을 양의 정수로 입력하세요.");
    return { receiptId, targetType: row.targetType, targetId, amount };
  });
}
export function allocationInput(
  rows: AllocationDraft[],
  date: string,
): Omit<AllocationPayload, "idempotencyKey"> {
  if (!date || !rows.length)
    throw new Error("배분일과 배분 항목을 입력하세요.");
  return { allocationDate: date, allocations: allocationLines(rows) };
}
export function correctionInput(
  rows: AllocationDraft[],
  date: string,
  cancellationIds: number[],
  reason: string,
): Omit<AllocationCorrectionPayload, "idempotencyKey"> {
  if (
    !date ||
    !reason.trim() ||
    !cancellationIds.length ||
    cancellationIds.length > 100
  )
    throw new Error("정정일·사유와 취소할 배분을 선택하세요.");
  return {
    correctionDate: date,
    cancellationIds,
    allocations: allocationLines(rows),
    reason: reason.trim(),
  };
}
