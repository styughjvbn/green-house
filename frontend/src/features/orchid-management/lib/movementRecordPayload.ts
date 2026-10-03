import type { OrchidGroup } from "@/entities/farm/types";
import type { components } from "@/shared/api/generated/openapi";

export function movementRecordPayload(
  group: Pick<OrchidGroup, "id" | "quantity" | "varietyName">,
  destination: {
    bedZoneId: number;
    startPosition: number;
    endPosition: number;
  },
  workTypeId: number,
  businessDate: string,
  idempotencyKey: string,
): components["schemas"]["StructureChangeRecordBatchCreateRequest"] {
  return {
    records: [
      {
        operation: {
          workTypeId,
          title: `${group.varietyName} · 자리 이동`,
          plannedStartDate: businessDate,
          sourceScopeType: "MANUAL_SELECTION",
          sourceOrchidGroupIds: [group.id],
        },
        execution: {
          idempotencyKey,
          completedDate: businessDate,
          sources: [
            { sourceOrchidGroupId: group.id, inputQuantity: group.quantity },
          ],
          results: [
            {
              ...destination,
              quantity: group.quantity,
              attributeSourceOrchidGroupId: group.id,
              purpose: "NORMAL",
            },
          ],
        },
      },
    ],
  };
}
